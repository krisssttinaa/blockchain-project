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
                System.out.println("Transaction successfully added to the pool.");}
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

                // Step 1: Create a list to hold the transactions to be mined
                List<Transaction> transactionsToMine = new ArrayList<>();
                // Step 2: Create the coinbase transaction and add it first
                Transaction coinbaseTransaction = new CoinbaseTransaction(Main.minerAddress, miningReward);
                coinbaseTransaction.processTransaction();  // Process the coinbase transaction
                transactionsToMine.add(coinbaseTransaction);
                // Step 3: Add other pending transactions from the pool
                for (int i = 0; i < numTransactionsToMine; i++) {
                    Transaction tx = unconfirmedTransactions.poll();
                    if (tx != null) {
                        transactionsToMine.add(tx);
                    }
                }

                // Step 4: Create the new block with all transactions
                Block newBlock = new Block(chain.size(), chain.get(chain.size() - 1).getHash(), transactionsToMine);
                newBlock.mineBlock(difficulty);
                forkResolution.addBlock(newBlock);  // Add block to ForkResolution for consensus
                networkManager.broadcastMessage(new Message(MessageType.NEW_BLOCK, new Gson().toJson(newBlock)));
                System.out.println("BROADCASTED");
            } else {
                System.out.println(unconfirmedTransactions.size() + " transactions in the pool. Not enough transactions to mine yet.");
            }
    }

    public synchronized boolean addAndValidateBlock(Block block) {
            // Check if the block has already been processed
            if (receivedBlockHashes.contains(block.getHash())) {
                System.out.println("Block already received: " + block.getHash());
                return false;
            }
            Block lastBlock = chain.get(chain.size() - 1);
            // Check if block is valid by PoW (has correct difficulty)
            if (!block.getHash().startsWith(StringUtil.getDifficultyString(difficulty))) {
                System.out.println("Block failed PoW validation: incorrect difficulty.");
                return false;
            }
            // Check if block index and previous hash match the current chain
            // Step 3: Normal Case - Check if the block can be added to the current chain
            if (block.getPreviousHash().equals(lastBlock.getHash()) && block.getIndex() == lastBlock.getIndex() + 1) {
                return validateAndAddBlock(block);  // Valid sequential block, so add it
            }
            // Fork - Block for an index that already exists
            else if (block.getIndex() <= lastBlock.getIndex()) {
                System.out.println("Received block for existing index, potential fork at index: " + block.getIndex());
                return false;
            }
            // Validation failed: incorrect index or hash mismatch
            else {
                System.out.println("Block validation failed: incorrect index or hash mismatch.");
                return false;
            }
    }

    private synchronized boolean validateAndAddBlock(Block block) {
        // Step 1: Recalculate the block hash and check if it matches the provided hash
        String recalculatedHash = block.calculateHash();
        System.out.println("Recalculated block hash: " + recalculatedHash);
        if (!block.getHash().equals(recalculatedHash)) {
            System.out.println("Block validation failed: recalculated hash does not match.");
            return false;
        }

        // Step 2: Validate the block's transactions (including signatures and UTXOs)
        for (Transaction transaction : block.getTransactions()) {
            // Verify the transaction's signature
            if (!transaction.verifySignature()) {
                System.out.println("Block contains an invalid transaction signature.");
                return false;
            }
            // Check if inputs refer to valid and unspent UTXOs
            for (TransactionInput input : transaction.getInputs()) {
                TransactionOutput utxo = UTXOs.get(input.transactionOutputId);
                if (utxo == null) {
                    System.out.println("Transaction input refers to a non-existent UTXO.");
                    return false;
                }
                // Check if the value of the UTXO matches the input's expected value
                if (input.UTXO.value != utxo.value) {
                    System.out.println("UTXO value mismatch for input.");
                    return false;
                }
            }
        }
        // Step 3: Add the block to the chain
        chain.add(block);
        System.out.println("Block added to the chain successfully: " + block.getHash());
        // Step 4: Update the UTXO pool with the new block's transactions
        updateUTXOs(block);
        // Step 5: Track the block's hash to prevent reprocessing
        addBlockHashToTracking(block.getHash());
        return true;
    }

    // New method for adding a transaction and handling the logic for broadcasting and mining
    public synchronized boolean handleNewTransaction(Transaction transaction, String peerIp, NetworkManager networkManager, ForkResolution forkResolution) {
        // Step 1: Add the transaction
        if (addTransaction(transaction)) {
            System.out.println("Transaction validated and added to pool.");

            // Step 2: Broadcast the transaction (skip the peer that sent it, if applicable)
            String jsonTransaction = new Gson().toJson(transaction);
            Message message = new Message(MessageType.NEW_TRANSACTION, jsonTransaction);
            if (peerIp == null) {
                // Broadcast to all peers (if peerIp is null, broadcast to everyone)
                networkManager.broadcastMessage(message);
            } else {
                // Broadcast to all except the peer that sent it
                networkManager.broadcastMessageExceptSender(message, peerIp);
            }

            // Step 3: Check if we need to start mining
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

    // Add block hash to the tracking deque, ensuring its size stays within the limit
    public void addBlockHashToTracking(String blockHash) {
        if (receivedBlockHashes.size() >= MAX_HASH_COUNT) {
            receivedBlockHashes.poll(); // Remove the oldest block hash to maintain the fixed size
        }
        receivedBlockHashes.add(blockHash); // Add the new block hash
    }

    public void printChain() {
        String blockchainJson = new GsonBuilder().setPrettyPrinting().create().toJson(chain);
        System.out.println("The block chain: ");
        System.out.println(blockchainJson);
    }

    // Update UTXO pool after adding a new block
    private void updateUTXOs(Block block) {
        for (Transaction transaction : block.getTransactions()) {
            for (TransactionInput input : transaction.getInputs()) {
                UTXOs.remove(input.transactionOutputId);  // Remove spent UTXOs
            }
            for (TransactionOutput output : transaction.getOutputs()) {
                UTXOs.put(output.id, output);  // Add new UTXOs from the transaction outputs
            }
        }
    }

    public void setNetworkManager(NetworkManager networkManager) {this.networkManager = networkManager;}
    // Get the last block in the chain
    Block getLastBlock() {
        return chain.size() > 0 ? chain.get(chain.size() - 1) : null;
    }
    public List<Block> getChain() {return chain;}

    // Removes the last block from the chain (used during fork resolution)
    public synchronized void removeLastBlock() {
        if (chain.size() > 1) {  // Prevent removing the genesis block
            Block lastBlock = chain.remove(chain.size() - 1);  // Remove the last block
            revertUTXOs(lastBlock);  // Revert UTXO changes made by the block
            System.out.println("Block removed: " + lastBlock.getHash());
        } else {
            System.out.println("Cannot remove genesis block.");
        }
    }

    // Revert UTXO changes made by a block
    private synchronized void revertUTXOs(Block block) {
        for (Transaction transaction : block.getTransactions()) {
            // Revert outputs created by this block's transactions
            for (TransactionOutput output : transaction.getOutputs()) {
                UTXOs.remove(output.id);  // Remove the UTXO created by the block
            }
            // Re-add UTXOs that were spent by this block's transactions
            for (TransactionInput input : transaction.getInputs()) {
                if (input.UTXO != null) {
                    UTXOs.put(input.transactionOutputId, input.UTXO);  // Re-add spent UTXOs
                }
            }
        }
    }
    public int getNumTransactionsToMine() {return numTransactionsToMine;}
    public Deque<String> getReceivedBlockHashes() {return receivedBlockHashes;}
    public LRUCache<String, Boolean> getReceivedTransactions() {return receivedTransactions;}
}