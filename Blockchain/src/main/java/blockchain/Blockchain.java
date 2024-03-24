package blockchain;import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Blockchain {
    private List<Block> chain;
    private Map<String, TransactionOutput> UTXOs;

    public Blockchain() {
        chain = new ArrayList<>();
        UTXOs = new HashMap<>();  // Corrected initialization
        addGenesisBlock();
    }

    // Create and add the genesis block
    private void addGenesisBlock() {
        chain.add(new Block(0, "0", "Genesis Block"));
    }

    // Add a new block to the blockchain
    public void addBlock(Block newBlock) {
        newBlock.mineBlock(4); // Mine the block with difficulty 4 (adjust as needed)
        chain.add(newBlock);
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

    // Get the latest block in the blockchain
    public Block getLatestBlock() {
        return chain.get(chain.size() - 1);
    }
}
