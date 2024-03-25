package blockchain;

import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Blockchain {
    private List<Block> chain;
    public static HashMap<String, TransactionOutput> UTXOs = new HashMap<>(); // Use the correct TransactionOutput class

    public Blockchain() {
        chain = new ArrayList<>();
        // Initialize with genesis block
        addGenesisBlock();
    }

    private void addGenesisBlock() {
        Block genesisBlock = new Block(0, "0");
        // Add some initial transactions to the genesis block if necessary
        chain.add(genesisBlock);
    }

    public void addBlock(Block newBlock) {
        newBlock.mineBlock(Main.difficulty);
        chain.add(newBlock);
        // Update UTXOs: Remove spent ones and add new ones
        updateUTXOs(newBlock);
    }

    private void updateUTXOs(Block block) {
        for (Transaction transaction : block.getTransactions()) {
            // For each transaction, remove spent UTXOs
            transaction.getInputs().forEach(i -> UTXOs.remove(i.transactionOutputId));
            // Add new UTXOs
            transaction.getOutputs().forEach(o -> UTXOs.put(o.id, o));
        }
    }

    // Validate the integrity of the blockchain
    public boolean isValidChain() {
        Block currentBlock;
        Block previousBlock;

        for (int i = 1; i < chain.size(); i++) {
            currentBlock = chain.get(i);
            previousBlock = chain.get(i - 1);

            if (!currentBlock.getHash().equals(currentBlock.calculateHash())) {
                System.out.println("Current Block Hashes not equal");
                return false;
            }

            if (!previousBlock.getHash().equals(currentBlock.getPreviousHash())) {
                System.out.println("Previous Block Hashes not equal");
                return false;
            }
        }

        return true;
    }

    public void printChain() {
        String blockchainJson = new GsonBuilder().setPrettyPrinting().create().toJson(chain);
        System.out.println("The block chain: ");
        System.out.println(blockchainJson);
    }

    // Get the latest block in the blockchain
    public Block getLatestBlock() {
        return chain.get(chain.size() - 1);
    }
}
