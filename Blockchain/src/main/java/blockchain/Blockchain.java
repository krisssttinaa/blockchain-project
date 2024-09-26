package blockchain;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import networking.Message;
import networking.MessageType;
import networking.NetworkManager;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class Blockchain {
    private List<Block> chain;
    private final ReentrantLock chainLock = new ReentrantLock();  // Lock for blockchain access
    public static ConcurrentHashMap<String, TransactionOutput> UTXOs = new ConcurrentHashMap<>(); // UTXO pool
    private static final int MAX_HASH_COUNT = 300;  // Keep only the last 300 block hashes, track only the most recent block hashes
    public static Deque<String> receivedBlockHashes = new ConcurrentLinkedDeque<>(); // Track recent block hashes
    private final ExecutorService miningExecutor = Executors.newSingleThreadExecutor(); // A single thread for mining
    NetworkManager networkManager;

    public Blockchain() {
        this.chain = new ArrayList<>();
        Block genesisBlock = new Block(0, "0");
        chain.add(genesisBlock);
        addBlockHashToTracking(genesisBlock.getHash());  // Track the genesis block hash
    }

    // Start mining asynchronously
    public void startMining(int numTransactionsToMine, ForkResolution forkResolution) {
        miningExecutor.submit(() -> {
            Block minedBlock = minePendingTransactions(numTransactionsToMine, forkResolution);
            if (minedBlock != null) {
                System.out.println("Block mined: " + minedBlock.getHash());
                // Immediately broadcast the block after mining
                networkManager.broadcastMessage(new Message(MessageType.NEW_BLOCK, new Gson().toJson(minedBlock)));
                System.out.println("Serialized block being broadcasted: " + new Gson().toJson(minedBlock));
            }
        });
    }

    // Add a transaction to the global unconfirmed pool in Main
    public synchronized boolean addTransaction(Transaction transaction) {
        if (transaction.value == 0) {
            Main.unconfirmedTransactions.add(transaction);
            Main.receivedTransactions.put(transaction.transactionId, Boolean.TRUE); // Add to received transactions cache
            System.out.println("Zero-value transaction added to the pool.");
            return true;
        }

        if (transaction.processTransaction()) {
            Main.unconfirmedTransactions.add(transaction);
            Main.receivedTransactions.put(transaction.transactionId, Boolean.TRUE); // Add to received transactions cache
            System.out.println("Transaction successfully added to the pool.");
            return true;
        } else {
            System.out.println("Transaction failed to process.");
            return false;
        }
    }

    // Mine n number of pending transactions from the pool in Main
    public synchronized Block minePendingTransactions(int numTransactionsToMine, ForkResolution forkResolution) {
        chainLock.lock();  // Ensure thread-safe access to blockchain state
        try {
            if (Main.unconfirmedTransactions.size() >= numTransactionsToMine) {
                System.out.println("Mining a new block with " + numTransactionsToMine + " pending transactions...");

                // Step 1: Create a list to hold the transactions to be mined
                List<Transaction> transactionsToMine = new ArrayList<>();

                // Step 2: Create the coinbase transaction and add it first
                Transaction coinbaseTransaction = new CoinbaseTransaction(Main.minerAddress, Main.miningReward);
                coinbaseTransaction.processTransaction();  // Process the coinbase transaction
                transactionsToMine.add(coinbaseTransaction);

                // Step 3: Add other pending transactions from the pool
                for (int i = 0; i < numTransactionsToMine; i++) {
                    Transaction tx = Main.unconfirmedTransactions.poll();
                    if (tx != null) {
                        transactionsToMine.add(tx);
                    }
                }

                // Step 4: Create the new block with all transactions
                Block newBlock = new Block(chain.size(), chain.get(chain.size() - 1).getHash(), transactionsToMine);
                newBlock.mineBlock(Main.difficulty);
                forkResolution.addBlock(newBlock);  // Add block to ForkResolution for consensus

                return newBlock;
            } else {
                System.out.println(Main.unconfirmedTransactions.size() + " transactions in the pool. Not enough transactions to mine yet.");
            }
            return null;
        } finally {
            chainLock.unlock();  // Always release the lock
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
            if (!block.getHash().startsWith(StringUtil.getDifficultyString(Main.difficulty))) {
                System.out.println("Block failed PoW validation: incorrect difficulty.");
                return false;
            }
            // Check if block index and previous hash match the current chain
            if (block.getPreviousHash().equals(lastBlock.getHash()) && block.getIndex() == lastBlock.getIndex() + 1) {
                /*
                System.out.println("=== Block Validation Start ===");
                System.out.println("Validating block with index: " + block.getIndex());
                System.out.println("Previous block hash: " + lastBlock.getHash());
                System.out.println("Block's previous hash: " + block.getPreviousHash());
                System.out.println("Block's current hash: " + block.getHash());
                System.out.println("Block's nonce: " + block.getNonce());
                System.out.println("Block's timestamp: " + block.getTimestamp());*/
                String recalculatedHash = block.calculateHashOut();
                //System.out.println("Hash components for recalculation: " + block.getPreviousHash() + block.getTimestamp() + block.getIndex() + block.getTransactions() + block.getNonce());
                //System.out.println("Recalculated block hash: " + recalculatedHash);
                if (!block.getHash().equals(recalculatedHash)) {
                    System.out.println("Block validation failed: calculated hash does not match.");
                    System.out.println("Block hash: " + block.getHash());
                    System.out.println("Recalculated hash: " + recalculatedHash);
                    return false;
                } else {
                    System.out.println("Block hash matches the recalculated hash.");
                }

                // Validate all transactions within the block
                for (Transaction transaction : block.getTransactions()) {
                    if (!transaction.verifySignature()) {
                        System.out.println("Block contains invalid transaction signature.");
                        return false;
                    }

                    // Check UTXOs (Unspent Transaction Outputs) for validity
                    for (TransactionInput input : transaction.getInputs()) {
                        TransactionOutput utxo = Blockchain.UTXOs.get(input.transactionOutputId);
                        if (utxo == null) {
                            System.out.println("Transaction input refers to non-existent UTXO.");
                            return false;
                        }

                        // Ensure input value matches UTXO value
                        if (input.UTXO.value != utxo.value) {
                            System.out.println("UTXO value mismatch for input.");
                            return false;
                        }
                    }
                }
                chain.add(block);  // Add block to the chain
                addBlockHashToTracking(block.getHash());  // Track the block's hash
                updateUTXOs(block);  // Update UTXO pool with block's transactions
                System.out.println("Block added to the chain successfully: " + block.getHash());
                return true;
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

    // Add block hash to the tracking deque, ensuring its size stays within the limit
    public void addBlockHashToTracking(String blockHash) {
        if (receivedBlockHashes.size() >= MAX_HASH_COUNT) {
            receivedBlockHashes.poll(); // Remove the oldest block hash to maintain the fixed size
        }
        receivedBlockHashes.add(blockHash); // Add the new block hash
    }

    // Validate the blockchain
    public boolean isValidChain() {
        if (chain.isEmpty()) {
            System.out.println("Blockchain is empty.");
            return false;
        }

        HashMap<String, TransactionOutput> tempUTXOs = new HashMap<>();
        Block previousBlock = chain.get(0); // Genesis block

        // Initialize UTXOs from the genesis block's transactions
        for (TransactionOutput output : previousBlock.getTransactions().get(0).getOutputs()) {
            tempUTXOs.put(output.id, output);
        }
        for (int i = 1; i < chain.size(); i++) {
            Block currentBlock = chain.get(i); // Verify the current block's hash and previous hash
            if (!currentBlock.getHash().equals(currentBlock.calculateHash()) ||
                    !previousBlock.getHash().equals(currentBlock.getPreviousHash())) {
                System.out.println("Block hashes are invalid.");
                return false;
            }

            // Validate each transaction
            for (Transaction transaction : currentBlock.getTransactions()) {
                if (!transaction.verifySignature()) {
                    System.out.println("Transaction signature is invalid.");
                    return false;
                }
                // Validate inputs and UTXOs
                for (TransactionInput input : transaction.getInputs()) {
                    TransactionOutput tempOutput = tempUTXOs.get(input.transactionOutputId);
                    if (tempOutput == null || input.UTXO.value != tempOutput.value) {
                        System.out.println("Transaction input or value is invalid.");
                        return false;
                    }
                }
                // Update temp UTXOs with new transaction outputs
                for (TransactionOutput output : transaction.getOutputs()) {
                    tempUTXOs.put(output.id, output);
                }
                // Remove inputs from temp UTXOs
                for (TransactionInput input : transaction.getInputs()) {
                    tempUTXOs.remove(input.transactionOutputId);
                }
            }
            previousBlock = currentBlock;
        }
        System.out.println("Blockchain is valid.");
        return true;
    }

    // Save the blockchain to disk
    public void saveBlockchain(String filename) {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(filename))) {
            out.writeObject(this.chain);
            out.writeObject(Main.UTXOs);
            System.out.println("Blockchain saved to disk.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Load the blockchain from disk
    public void loadBlockchain(String filename) {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(filename))) {
            this.chain = (List<Block>) in.readObject();
            Main.UTXOs = (ConcurrentHashMap<String, TransactionOutput>) in.readObject();
            System.out.println("Blockchain loaded from disk.");
        } catch (Exception e) {
            e.printStackTrace();
        }
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
                Main.UTXOs.remove(input.transactionOutputId);  // Remove spent UTXOs
            }
            for (TransactionOutput output : transaction.getOutputs()) {
                Main.UTXOs.put(output.id, output);  // Add new UTXOs from the transaction outputs
            }
        }
    }

    // Get the last block in the chain
    Block getLastBlock() {
        return chain.size() > 0 ? chain.get(chain.size() - 1) : null;
    }
    public List<Block> getChain() {return chain;}
}