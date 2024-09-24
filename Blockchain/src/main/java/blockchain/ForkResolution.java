package blockchain;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static blockchain.Blockchain.receivedBlockHashes;

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
        if (blockIndex == lastBlock.getIndex() + 1) {
            if (blockchain.addAndValidateBlock(block)) {
                System.out.println("Block added to the blockchain: " + block.getHash());
                blockchain.addBlockHashToTracking(block.getHash());  // Add mined block hash to receivedBlockHashes
            } else {
                System.out.println("Block failed validation: " + block.getHash());
            }
        } else if (blockIndex <= lastBlock.getIndex()) {
            System.out.println("Block index already present, adding to forked blocks: " + blockIndex);
            forks.computeIfAbsent(blockIndex, k -> new ArrayList<>()).add(block);
        }
    }

    public List<Block> getForkedBlocks(int index) { // Added method to get forked blocks by index
        return forks.getOrDefault(index, Collections.emptyList());
    }
}
