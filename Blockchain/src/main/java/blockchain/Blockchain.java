package blockchain;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static blockchain.Main.difficulty;

public class Blockchain {
    private List<Block> chain;
    public static HashMap<String, TransactionOutput> UTXOs = new HashMap<>();

    public Blockchain() {
        chain = new ArrayList<>();
        // Initialize with genesis block
        addGenesisBlock();
    }

    private void addGenesisBlock() {
        Block genesisBlock = new Block(0, "0");
        // Add some initial transactions to the genesis block if necessary
        chain.add(genesisBlock);
        System.out.println("Genesis Block Added");
    }

    public void addBlock(Block newBlock) {
        newBlock.mineBlock(difficulty);
        chain.add(newBlock);
        // Update UTXOs: Remove spent ones and add new ones
        updateUTXOs(newBlock);

        // Print the details of the newly added block
        System.out.println("New Block Added:");
        System.out.println("Index: " + newBlock.getIndex());
        System.out.println("Timestamp: " + newBlock.getTimestamp());
        System.out.println("Previous Hash: " + newBlock.getPreviousHash());
        System.out.println("Hash: " + newBlock.getHash());
        System.out.println("Transactions: " + newBlock.getTransactions().size());
        System.out.println();
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
}

/*
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
* */