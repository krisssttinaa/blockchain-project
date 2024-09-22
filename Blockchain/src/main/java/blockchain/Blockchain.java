package blockchain;

import com.google.gson.GsonBuilder;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class Blockchain {
    private List<Block> chain;
    public static ConcurrentHashMap<String, TransactionOutput> UTXOs = new ConcurrentHashMap<>(); // UTXO pool
    private static final int MAX_HASH_COUNT = 300;  // Keep only the last 300 block hashes, track only the most recent block hashes
    private Deque<String> receivedBlockHashes = new ConcurrentLinkedDeque<>(); // Track recent block hashes
    private static final int CHECKPOINT_INTERVAL = 100;  // Interval for saving blockchain state
    private int lastCheckpoint = 0;

    public Blockchain() {
        this.chain = new ArrayList<>();
        Block genesisBlock = new Block(0, "0");
        chain.add(genesisBlock);
        addBlockHashToTracking(genesisBlock.getHash());  // Track the genesis block hash
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
    public synchronized Block minePendingTransactions(int numTransactionsToMine) {
        if (Main.unconfirmedTransactions.size() >= numTransactionsToMine) {
            System.out.println("Mining a new block with " + numTransactionsToMine + " pending transactions...");

            // Collect `numTransactionsToMine` transactions for mining
            List<Transaction> transactionsToMine = new ArrayList<>();
            for (int i = 0; i < numTransactionsToMine; i++) {
                Transaction tx = Main.unconfirmedTransactions.poll();  // Removes the head of the queue
                if (tx != null) {
                    transactionsToMine.add(tx);
                }
            }

            Block newBlock = new Block(chain.size(), chain.get(chain.size() - 1).getHash(), transactionsToMine);
            if (addAndValidateBlock(newBlock)) {
                System.out.println("Block added to the blockchain.");
                return newBlock;
            } else {
                System.out.println("Failed to add new block to the blockchain.");
            }
        } else {
            System.out.println(Main.unconfirmedTransactions.size() + " transactions in the pool.");
            System.out.println("Not enough transactions to mine yet.");
        }
        return null;
    }

    // Method for mining a block with a coinbase transaction
    private void mineBlock(Block block, int difficulty) {
        block.addCoinbaseTransaction(Main.minerAddress, Main.miningReward);
        String target = StringUtil.getDifficultyString(difficulty);
        while (!block.getHash().substring(0, difficulty).equals(target)) {
            block.incrementNonce();
            block.updateHash();
        }
    }

    // Adding and validating a new block
    public synchronized boolean addAndValidateBlock(Block block) {
        // Check if the block has already been received (hash exists in the deque)
        if (receivedBlockHashes.contains(block.getHash())) {
            System.out.println("Block already received: " + block.getHash());
            return false;  // Reject the block if it's a duplicate
        }

        // Save checkpoint if the block index surpasses the checkpoint interval
        if (block.getIndex() > lastCheckpoint + CHECKPOINT_INTERVAL) {
            saveCheckpoint("checkpoint_" + block.getIndex() + ".dat");
            lastCheckpoint = block.getIndex();
        }

        Block lastBlock = chain.get(chain.size() - 1);
        if (block.getPreviousHash().equals(lastBlock.getHash()) && block.getIndex() == lastBlock.getIndex() + 1) {
            mineBlock(block, Main.difficulty);  // Mine the block (if using PoW)
            chain.add(block);  // Add block to the chain
            addBlockHashToTracking(block.getHash()); // Add the new block hash to the deque for tracking
            return true;
        } else {
            System.out.println("Block validation failed: hash mismatch or incorrect index.");
        }
        return false;
    }

    // Add block hash to the tracking deque, ensuring its size stays within the limit
    private void addBlockHashToTracking(String blockHash) {
        if (receivedBlockHashes.size() >= MAX_HASH_COUNT) {
            receivedBlockHashes.poll(); // Remove the oldest block hash to maintain the fixed size
        }
        receivedBlockHashes.add(blockHash); // Add the new block hash
    }

    // Save the blockchain state periodically
    private void saveCheckpoint(String filename) {
        saveBlockchain(filename);
        System.out.println("Checkpoint saved at block " + lastCheckpoint);
    }

    // Calculate the cumulative difficulty of the chain
    public long calculateCumulativeDifficulty() {
        long cumulativeDifficulty = 0;
        for (Block block : chain) {
            cumulativeDifficulty += Math.pow(2, Main.difficulty);
        }
        return cumulativeDifficulty;
    }

    // Compare and replace the current blockchain with a more valid and difficult chain
    public void compareAndReplace(Blockchain newBlockchain) {
        long currentDifficulty = calculateCumulativeDifficulty();
        long newDifficulty = newBlockchain.calculateCumulativeDifficulty();

        if (newBlockchain.isValidChain() && newDifficulty > currentDifficulty) {
            this.chain = new ArrayList<>(newBlockchain.chain);
            System.out.println("Blockchain has been replaced with a more difficult valid chain.");
        } else {
            System.out.println("Received blockchain is invalid or does not have higher cumulative difficulty.");
        }
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
            Block currentBlock = chain.get(i);

            // Verify the current block's hash and previous hash
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

    // Update UTXO pool after mining a block
    private void updateUTXOs(Block block) {
        for (Transaction transaction : block.getTransactions()) {
            // Remove spent UTXOs
            for (TransactionInput input : transaction.getInputs()) {
                UTXOs.remove(input.transactionOutputId);
            }
            // Add new UTXOs
            for (TransactionOutput output : transaction.getOutputs()) {
                UTXOs.put(output.id, output);
            }
        }
    }

    // Get the last block in the chain
    private Block getLastBlock() {
        return chain.size() > 0 ? chain.get(chain.size() - 1) : null;
    }

}