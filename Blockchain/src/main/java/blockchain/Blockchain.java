package blockchain;

import java.util.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import ledger.LRUCache;
import networking.Message;
import networking.MessageType;
import networking.NetworkManager;
import ledger.CoinbaseTransaction;
import ledger.Transaction;
import ledger.TransactionInput;
import ledger.TransactionOutput;
import java.util.concurrent.*;

public class Blockchain {
    public static final ConcurrentHashMap<String, TransactionOutput> UTXOs = new ConcurrentHashMap<>(); // Instance-level UTXO pool
    public static ConcurrentLinkedQueue<Transaction> unconfirmedTransactions = new ConcurrentLinkedQueue<>(); // Unconfirmed transaction pool using ConcurrentLinkedQueue
    private final List<Block> chain;
    private NetworkManager networkManager;
    private final Deque<String> receivedBlockHashes = new ConcurrentLinkedDeque<>(); // Track recent block hashes
    private final LRUCache<String, Boolean> receivedTransactions = new LRUCache<>(500); // Capacity of 500
    private final ExecutorService miningExecutor = Executors.newSingleThreadExecutor(); // A single thread for mining
    private final int numTransactionsToMine = 2; // Number of transactions to mine in a block
    private final float miningReward = 6.00f;
    private final int MAX_HASH_COUNT = 300;  // Keep only the last 300 block hashes, track only the most recent block hashes
    private final int difficulty = 6; // Mining difficulty
    private static final int MINIMUM_CONFIRMATIONS = 5;

    public Blockchain() {
        this.chain = new ArrayList<>();
        Block genesisBlock = new Block(0, "0");
        chain.add(genesisBlock);
        addBlockHashToTracking(genesisBlock.getHash());  // Track the genesis block hash
    }

    public void startMining(int numTransactionsToMine, ForkResolution forkResolution) { // Start mining asynchronously
        miningExecutor.submit(() -> minePendingTransactions(numTransactionsToMine, forkResolution));
    }

    // Add a transaction to the global unconfirmed pool in Main
    public synchronized boolean addTransaction(Transaction transaction) {
        if (transaction.processTransaction()) {
            unconfirmedTransactions.add(transaction);
            receivedTransactions.put(transaction.transactionId, Boolean.TRUE); // Add to received transactions cache
            if (transaction.value == 0) {
                System.out.println("Zero-value transaction added to the pool.");
            } else {
                System.out.println("Transaction successfully added to the pool.");
            }
            return true;
        } else {
            System.out.println("Transaction failed to process.");
            return false;
        }
    }

    // Mine n number of pending transactions from the pool in Main
    private synchronized void minePendingTransactions(int numTransactionsToMine, ForkResolution forkResolution) {
        if (unconfirmedTransactions.size() >= numTransactionsToMine) {
            System.out.println("Mining a new block with " + numTransactionsToMine + " pending transactions...");

            List<Transaction> transactionsToMine = new ArrayList<>();
            Transaction coinbaseTransaction = new CoinbaseTransaction(Main.minerAddress, miningReward);
            coinbaseTransaction.processTransaction();  // Process the coinbase transaction
            transactionsToMine.add(coinbaseTransaction);
            for (int i = 0; i < numTransactionsToMine; i++) {
                Transaction tx = unconfirmedTransactions.poll();
                if (tx != null) {
                    transactionsToMine.add(tx);
                }
            }

            Block newBlock = new Block(chain.size(), chain.get(chain.size() - 1).getHash(), transactionsToMine);
            newBlock.mineBlock(difficulty);
            forkResolution.addBlock(newBlock);  // Add block to ForkResolution for consensus
            //updateUTXOs(newBlock, true);  // Update UTXO pool
            //ageUTXOs();  // Increase confirmations of UTXOs
            networkManager.broadcastMessage(new Message(MessageType.NEW_BLOCK, new Gson().toJson(newBlock)));
            System.out.println("BROADCASTED");
        } else {
            System.out.println(unconfirmedTransactions.size() + " transactions in the pool. Not enough transactions to mine yet.");
        }
    }

    public synchronized boolean addAndValidateBlock(Block block) {
        if (receivedBlockHashes.contains(block.getHash())) {
            System.out.println("Block already received: " + block.getHash());
            return false;
        }
        Block lastBlock = chain.get(chain.size() - 1);
        if (!block.getHash().startsWith(StringUtil.getDifficultyString(difficulty))) {
            System.out.println("Block failed PoW validation: incorrect difficulty.");
            return false;
        }
        if (block.getPreviousHash().equals(lastBlock.getHash()) && block.getIndex() == lastBlock.getIndex() + 1) {
            return validateAndAddBlock(block);
        }
        else if (block.getIndex() <= lastBlock.getIndex()) {
            System.out.println("Received block for existing index, potential fork at index: " + block.getIndex());
            return false;
        }
        else {
            System.out.println("Block validation failed: incorrect index or hash mismatch.");
            return false;
        }
    }

    private synchronized boolean validateAndAddBlock(Block block) {
        String recalculatedHash = block.calculateHash();
        System.out.println("Recalculated block hash: " + recalculatedHash);
        if (!block.getHash().equals(recalculatedHash)) {
            System.out.println("Block validation failed: recalculated hash does not match.");
            return false;
        }

        for (Transaction transaction : block.getTransactions()) {
            if (!transaction.verifySignature()) {
                System.out.println("Block contains an invalid transaction signature.");
                return false;
            }
            if (transaction.value != 0) {
                // Check if inputs refer to valid and unspent UTXOs
                for (TransactionInput input : transaction.getInputs()) {
                    TransactionOutput utxo = Blockchain.UTXOs.get(input.transactionOutputId);
                    if (utxo == null) {
                        System.out.println("Transaction input refers to a non-existent UTXO.");
                        return false;
                    }
                    input.UTXO = utxo;  // Link the UTXO to the input
                    System.out.println("Transaction input references UTXO ID: " + input.transactionOutputId + " with value: " + utxo.value);
                }
            }else {
                // Zero-value transaction; no inputs, skip UTXO checks
                System.out.println("Skipping UTXO validation for zero-value transaction.");
            }
        }
        chain.add(block);
        System.out.println("Block added to the chain successfully: " + block.getHash());
        updateUTXOs(block, true);  // Since you're adding the block to the chain, update UTXO pool for main chain
        addBlockHashToTracking(block.getHash());
        ageUTXOs();  // Increment confirmations for all UTXOs
        return true;
    }

    // New method for adding a transaction and handling the logic for broadcasting and mining
    public synchronized boolean handleNewTransaction(Transaction transaction, String peerIp, NetworkManager networkManager, ForkResolution forkResolution) {
        if (addTransaction(transaction)) {
            System.out.println("Transaction validated and added to pool.");
            String jsonTransaction = new Gson().toJson(transaction);
            Message message = new Message(MessageType.NEW_TRANSACTION, jsonTransaction);
            if (peerIp == null) {
                networkManager.broadcastMessage(message);
            } else {
                networkManager.broadcastMessageExceptSender(message, peerIp);
            }

            if (unconfirmedTransactions.size() >= getNumTransactionsToMine()) {
                System.out.println("Enough transactions in the pool. Starting mining...");
                startMining(getNumTransactionsToMine(), forkResolution);
            } else {
                System.out.println(unconfirmedTransactions.size() + " transactions in the pool, NOT ENOUGH.");
            }
            return true;
        } else {
            System.out.println("Transaction failed to validate.");
            return false;
        }
    }

    public void addBlockHashToTracking(String blockHash) {
        if (receivedBlockHashes.size() >= MAX_HASH_COUNT) {
            receivedBlockHashes.poll(); // Remove the oldest block hash to maintain the fixed size
        }
        receivedBlockHashes.add(blockHash); // Add the new block hash
    }

    public void ageUTXOs() {
        for (TransactionOutput utxo : Blockchain.UTXOs.values()) {
            System.out.println("Aging UTXO with ID: " + utxo.id + " in block: " + utxo.parentTransactionId);
            Block containingBlock = getBlockByTransactionId(utxo.parentTransactionId);
            if (isBlockInMainChain(containingBlock)) {
                if (utxo.confirmations < MINIMUM_CONFIRMATIONS) {
                    utxo.confirmations++;
                    System.out.println("UTXO with ID: " + utxo.id + " now has " + utxo.confirmations + " confirmations.");
                } else {
                    System.out.println("UTXO with ID: " + utxo.id + " has reached "+MINIMUM_CONFIRMATIONS+" confirmations.");
                }
            }
        }
    }

    private void updateUTXOs(Block block, boolean isMainChain) {
        if (!isMainChain) {
            System.out.println("Block is part of a fork, not adding UTXOs.");
            return;
        }

        for (Transaction transaction : block.getTransactions()) {
            // Skip UTXO input handling for zero-value transactions
            if (transaction.value == 0) {
                System.out.println("Skipping UTXO input handling for zero-value transaction.");
                continue;  // Skip directly to handling outputs
            }

            // Remove spent UTXOs referenced in the transaction's inputs
            for (TransactionInput input : transaction.getInputs()) {
                TransactionOutput utxo = Blockchain.UTXOs.get(input.transactionOutputId);
                // Check if the UTXO is fully matured before spending it
                if (utxo != null && utxo.confirmations >= MINIMUM_CONFIRMATIONS) {
                    Blockchain.UTXOs.remove(input.transactionOutputId);
                    System.out.println("UTXO removed: " + input.transactionOutputId);
                } else {
                    System.out.println("Attempted to spend immature UTXO: " + input.transactionOutputId + ". Ignored.");
                }
            }

            // Add new UTXOs created by the transaction
            for (TransactionOutput output : transaction.getOutputs()) {
                Blockchain.UTXOs.put(output.id, output);
                System.out.println("UTXO added: " + output.id);
            }
        }
    }


    private synchronized void revertUTXOs(Block block) {
        for (Transaction transaction : block.getTransactions()) {
            for (TransactionOutput output : transaction.getOutputs()) {
                Blockchain.UTXOs.remove(output.id);
                System.out.println("Reverted UTXO removed: " + output.id);
            }
            for (TransactionInput input : transaction.getInputs()) {
                if (input.UTXO != null) {
                    Blockchain.UTXOs.put(input.transactionOutputId, input.UTXO);
                    System.out.println("Reverted UTXO re-added: " + input.transactionOutputId);
                }
            }
        }
    }

    public void reAddTransactionsFromDiscardedBlocks(List<Block> discardedBlocks) {
        for (Block block : discardedBlocks) {
            for (Transaction transaction : block.getTransactions()) {
                if (transaction.isStillValid()) {
                    Blockchain.unconfirmedTransactions.add(transaction);
                    System.out.println("Re-added transaction: " + transaction.transactionId);
                }
            }
        }
    }

    public synchronized void removeLastBlock() {
        if (chain.size() > 1) {  // Prevent removing the genesis block
            Block lastBlock = chain.remove(chain.size() - 1);
            revertUTXOs(lastBlock);  // Revert UTXO changes made by the block
            System.out.println("Block removed: " + lastBlock.getHash());
        } else {
            System.out.println("Cannot remove genesis block.");
        }
    }

    public List<Block> getBlocksInRange(int startIndex, int endIndex) {
        // Check for invalid ranges
        if (startIndex < 0 || endIndex >= chain.size() || startIndex > endIndex) {
            System.out.println("Invalid block range requested.");
            return Collections.emptyList();
        }
        if (startIndex == 0) {startIndex = 1;}
        return new ArrayList<>(chain.subList(startIndex, Math.min(endIndex + 1, chain.size())));
    }

    public Block getBlockByTransactionId(String transactionId) {
        for (Block block : chain) {
            for (Transaction transaction : block.getTransactions()) {
                if (transaction.getTransactionId().equals(transactionId)) {
                    return block;
                }
            }
        }
        return null;
    }

    public void printChain() {
        String blockchainJson = new GsonBuilder().setPrettyPrinting().create().toJson(chain);
        System.out.println("The blockchain: ");
        System.out.println(blockchainJson);
    }

    public boolean isBlockInMainChain(Block block) { return chain.contains(block); }
    public int getNumTransactionsToMine() { return numTransactionsToMine; }
    public Deque<String> getReceivedBlockHashes() { return receivedBlockHashes; }
    public LRUCache<String, Boolean> getReceivedTransactions() { return receivedTransactions; }
    public void setNetworkManager(NetworkManager networkManager) { this.networkManager = networkManager; }
    public Block getLastBlock() {
        return chain.size() > 0 ? chain.get(chain.size() - 1) : null;
    }
    public List<Block> getChain() { return chain; }
}