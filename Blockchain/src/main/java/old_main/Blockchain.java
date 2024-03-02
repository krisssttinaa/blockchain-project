package old_main;

import java.util.ArrayList;
import java.util.List;
public class Blockchain {
    private List<Block> chain;
    public Blockchain() {
        this.chain = new ArrayList<>();
        addGenesisBlock();
    }

    private void addGenesisBlock() {
        //System.out.println("Am i here");
        Block genesisBlock = new Block(0, "0000000000000000000000000000000000000000000000000000000000000000",
                (new Transaction("", (new Miner("")), 0)));
        //System.out.println("Here");
        chain.add(genesisBlock);
        System.out.println("Genesis Block added successfully!");
    }
    public void addBlock(Block block) {
        if (isValidBlock(block)) {
            chain.add(block);
        } else {
            System.out.println("Invalid block - Proof of work not satisfied or previous hash mismatch.");
        }
    }

    private boolean isValidBlock(Block block) {
        Block previousBlock = getLatestBlock();
        // Check if the proof of work is valid
        if (!block.getHash().startsWith("00000")) {
            return false;
        }
        // Check if the previous hash points to the previous block
        if (!block.getPreviousHash().equals(previousBlock.getHash())) {
            return false;
        }

        return true;
    }

    public Block getLatestBlock() { return chain.get(chain.size() - 1);}

    public List<Block> getChain() { return chain;}
}

/*
    public void addBlock(Block block) {
        if (isValidBlock(block)) {
            chain.add(block);
        }
    }

    private boolean isValidBlock(Block block) {
        return block.getPreviousHash().equals(getLatestBlock().getHash());
    }
*/