package blockchain;

import com.google.gson.GsonBuilder;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static blockchain.Main.difficulty;

public class Blockchain {
    private List<Block> chain;
    public static HashMap<String, TransactionOutput> UTXOs = new HashMap<>();
    private List<Transaction> unconfirmedTransactions; // A pool for unconfirmed transactions
    private static final int CHECKPOINT_INTERVAL = 100;  // Save a checkpoint every 100 blocks
    private int lastCheckpoint = 0;

    public Blockchain() {
        this.chain = new ArrayList<>();
        this.unconfirmedTransactions = new ArrayList<>();
        Block genesisBlock = new Block(0, "0");
        chain.add(genesisBlock);
    }

    private void mineBlock(Block block, int difficulty) {
        String target = StringUtil.getDifficultyString(difficulty); // Create a string with difficulty * "0"
        while (!block.getHash().substring(0, difficulty).equals(target)) {
            block.incrementNonce(); // Increase nonce to change the hash
            block.updateHash(); // Recalculate hash with the new nonce
        }
    }

    public boolean addAndValidateBlock(Block block) {
        if (block.getIndex() > lastCheckpoint + CHECKPOINT_INTERVAL) {
            saveCheckpoint("checkpoint_" + block.getIndex() + ".dat");
            lastCheckpoint = block.getIndex();
        }
        Block lastBlock = chain.get(chain.size() - 1);
        if (block.getPreviousHash().equals(lastBlock.getHash()) && block.getIndex() == lastBlock.getIndex() + 1) {
            mineBlock(block, difficulty); // Mine the new block so it satisfies the difficulty level
            chain.add(block); // Add to chain if it's valid
            unconfirmedTransactions.clear(); // Clear unconfirmed transactions
            return true;
        }
        return false;
    }

    private void saveCheckpoint(String filename) {
        saveBlockchain(filename);
        System.out.println("Checkpoint saved at block " + lastCheckpoint);
    }

    // Add this method to calculate the cumulative difficulty
    public long calculateCumulativeDifficulty() {
        long cumulativeDifficulty = 0;
        for (Block block : chain) {
            // Difficulty here refers to the number of leading zeros, so 2^difficulty gives us a rough measure of the work done
            cumulativeDifficulty += Math.pow(2, difficulty);
        }
        return cumulativeDifficulty;
    }

    public void compareAndReplace(Blockchain newBlockchain) {
        // Calculate the cumulative difficulty of both blockchains
        long currentDifficulty = calculateCumulativeDifficulty();
        long newDifficulty = newBlockchain.calculateCumulativeDifficulty();

        // Replace if the new blockchain has a higher cumulative difficulty
        if (newBlockchain.isValidChain() && newDifficulty > currentDifficulty) {
            this.chain = new ArrayList<>(newBlockchain.chain);
            System.out.println("Blockchain has been replaced with a more difficult valid chain.");
        } else {
            System.out.println("Received blockchain is invalid or does not have higher cumulative difficulty.");
        }
    }

    private void updateUTXOs(Block block) {
        for (Transaction transaction : block.getTransactions()) {
            // For each transaction, remove spent UTXOs
            transaction.getInputs().forEach(i -> UTXOs.remove(i.transactionOutputId));
            // Add new UTXOs
            transaction.getOutputs().forEach(o -> UTXOs.put(o.id, o));
        }
    }

    // Method to add a transaction to the pool of unconfirmed transactions
    public boolean addTransaction(Transaction transaction) {
        if (transaction.processTransaction() && transaction.getInputsValue() >= Main.minimumTransaction) {
            unconfirmedTransactions.add(transaction);
            System.out.println("Transaction successfully added to the pool.");
            return true;
        } else {
            System.out.println("Transaction failed to process.");
            return false;
        }
    }

    // Assuming you have a method to get the last block in the chain
    private Block getLastBlock() {
        return chain.size() > 0 ? chain.get(chain.size() - 1) : null;
    }

    // Validate the integrity of the blockchain
    public boolean isValidChain() {
        if (chain.isEmpty()) {
            System.out.println("Blockchain is empty.");
            return false;
        }

        HashMap<String, TransactionOutput> tempUTXOs = new HashMap<>();
        Block previousBlock = chain.get(0); // The first block in the chain is the genesis block

        // Assuming the genesis block has at least one transaction to initialize UTXOs
        for (TransactionOutput output : previousBlock.getTransactions().get(0).getOutputs()) {
            tempUTXOs.put(output.id, output);
        }

        // Start from the second block (index 1) because the first block is the genesis block
        for (int i = 1; i < chain.size(); i++) {
            Block currentBlock = chain.get(i);

            // Check current block hash
            if (!currentBlock.getHash().equals(currentBlock.calculateHash())) {
                System.out.println("Current Hashes are not equal.");
                return false;
            }

            // Check previous block hash
            if (!previousBlock.getHash().equals(currentBlock.getPreviousHash())) {
                System.out.println("Previous Hashes are not equal.");
                return false;
            }

            // Check if the block is mined
            if (!currentBlock.getHash().substring(0, difficulty).equals(StringUtil.getDifficultyString(difficulty))) {
                System.out.println("This block hasn't been mined.");
                return false;
            }

            // Validate all transactions in the current block
            for (Transaction transaction : currentBlock.getTransactions()) {
                if (!transaction.verifySignature()) {
                    System.out.println("Transaction signature is invalid.");
                    return false;
                }

                // Validate transaction inputs are unspent and belong to the sender
                for (TransactionInput input : transaction.getInputs()) {
                    TransactionOutput tempOutput = tempUTXOs.get(input.transactionOutputId);

                    if (tempOutput == null) {
                        System.out.println("Referenced input on Transaction is Missing.");
                        return false;
                    }

                    if (input.UTXO.value != tempOutput.value) {
                        System.out.println("Referenced input Transaction value is Invalid.");
                        return false;
                    }
                }

                // Update tempUTXOs with transactions outputs
                transaction.getOutputs().forEach(output -> tempUTXOs.put(output.id, output));

                // Remove transaction inputs from tempUTXOs
                for (TransactionInput input : transaction.getInputs()) {
                    tempUTXOs.remove(input.transactionOutputId);
                }
            }
            previousBlock = currentBlock;
        }
        System.out.println("Blockchain is valid.");
        return true;
    }

    public void printChain() {
        String blockchainJson = new GsonBuilder().setPrettyPrinting().create().toJson(chain);
        System.out.println("The block chain: ");
        System.out.println(blockchainJson);
    }

    public Block getLatestBlock() {
        return chain.get(chain.size() - 1);
    }

    // Save the blockchain and UTXO set to a file
    public void saveBlockchain(String filename) {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(filename))) {
            out.writeObject(this.chain);
            out.writeObject(Main.UTXOs);
            System.out.println("Blockchain saved to disk.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Load the blockchain and UTXO set from a file
    public void loadBlockchain(String filename) {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(filename))) {
            this.chain = (List<Block>) in.readObject();
            Main.UTXOs = (HashMap<String, TransactionOutput>) in.readObject();
            System.out.println("Blockchain loaded from disk.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Create a block when the unconfirmed transaction pool reaches a certain size
    public Block minePendingTransactions(int numTransactionsToMine) {
        if (unconfirmedTransactions.size() >= numTransactionsToMine) {
            System.out.println("Mining a new block with pending transactions...");
            List<Transaction> transactionsToMine = new ArrayList<>( // Get only the transactions needed for this block
                    unconfirmedTransactions.subList(0, numTransactionsToMine)
            );

            // Create a new block with those transactions
            Block newBlock = new Block(chain.size(), chain.get(chain.size() - 1).getHash(), transactionsToMine);
            if (addAndValidateBlock(newBlock)) { // Add and validate the block
                System.out.println("Block added to the blockchain");
                unconfirmedTransactions.removeAll(transactionsToMine); // Remove only the mined transactions from the pool
                return newBlock;
            } else {
                System.out.println("Failed to add new block to the blockchain");
            }
        }
        return null;
    }
}