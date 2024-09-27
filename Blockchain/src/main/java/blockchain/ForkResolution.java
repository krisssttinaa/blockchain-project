package blockchain;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ForkResolution implements Runnable {
    private final Blockchain blockchain;
    private final BlockingQueue<Block> blockQueue;
    private final Map<Integer, List<Block>> forks;

    public ForkResolution(Blockchain blockchain) {
        this.blockchain = blockchain;
        this.blockQueue = new LinkedBlockingQueue<>();
        this.forks = new HashMap<>();
    }

    public void addBlock(Block block) {blockQueue.add(block);}

    @Override
    public void run() {
        while (true) {
            try {
                Block block = blockQueue.take();
                processBlock(block);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("ForkResolution thread interrupted");
                break;
            }
        }
    }

    private void processBlock(Block block) {
            int blockIndex = block.getIndex();
            Block lastBlock = blockchain.getLastBlock();
            System.out.println("ForkResolution Processing block: " + block.getHash());
            System.out.println("ForkResolution processing block:");
            System.out.println("Block hash: " + block.getHash());
            System.out.println("Block index: " + block.getIndex());
            System.out.println("Previous Hash: " + block.getPreviousHash());
            System.out.println("Block's nonce: " + block.getNonce());
            System.out.println("Block's timestamp: " + block.getTimestamp()); //validations disappears after this
            if (blockIndex == lastBlock.getIndex() + 1) {
                if (blockchain.addAndValidateBlock(block)) {
                    System.out.println("ForkResolution Block added to the blockchain: " + block.getHash());
                    System.out.println("We get here");
                } else {
                    System.out.println("ForkResolution Block failed validation: " + block.getHash());
                }
            } else if (blockIndex <= lastBlock.getIndex()) {
                System.out.println("ForkResolution Block index already present, adding to forked blocks: " + blockIndex);
                forks.computeIfAbsent(blockIndex, k -> new ArrayList<>()).add(block);
            }
        //System.out.println("We get here");
    }

    public List<Block> getForkedBlocks(int index) { // Added method to get forked blocks by index
        return forks.getOrDefault(index, Collections.emptyList());
    }
}