package blockchain;

import ledger.Transaction;
import ledger.TransactionOutput;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ForkResolution implements Runnable {
    private final Blockchain blockchain;
    private final BlockingQueue<Block> blockQueue;
    private final Map<Integer, List<Block>> forks; // Tracks forked blocks by index

    public ForkResolution(Blockchain blockchain) {
        this.blockchain = blockchain;
        this.blockQueue = new LinkedBlockingQueue<>();
        this.forks = new HashMap<>();
    }

    @Override
    public void run() {
        while (true) {
            try {
                Block block = blockQueue.take();  // Blocks until a block is available in the queue
                processBlock(block);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("ForkResolution thread interrupted");
                break;
            }
        }
    }

    // Core block processing logic
    private void processBlock(Block block) {
        int blockIndex = block.getIndex();
        Block lastBlock = blockchain.getLastBlock();
        System.out.println("ForkResolution Processing block: " + block.getHash());

        // Block is directly next in the chain (lastBlock + 1)
        if (blockIndex == lastBlock.getIndex() + 1) {
            // If the block's previous hash doesn't match the current last block's hash
            if (!block.getPreviousHash().equals(lastBlock.getHash())) {
                System.out.println("Potential fork detected. Storing block for further validation: " + block.getHash());
                addBlockToForks(block);  // Handle as a fork
            } else {
                // Valid next block in our current chain
                if (blockchain.addAndValidateBlock(block)) {
                    System.out.println("ForkResolution Block added to the blockchain: " + block.getHash());
                    checkForksForExtension(block); // Check if there's a fork that extends this chain
                } else {
                    System.out.println("ForkResolution Block failed validation: " + block.getHash());
                }
            }
        }
        // Block is already in the chain (same index or earlier) - fork
        else if (blockIndex <= lastBlock.getIndex()) {
            System.out.println("ForkResolution Block index already present, adding to forked blocks: " + blockIndex);
            addBlockToForks(block);
            checkForLongerFork(blockIndex);  // Check if there's a longer competing chain
        }
        // Block is ahead of the current chain, store it in forks for later processing
        else {
            System.out.println("ForkResolution Block index too far ahead, adding to forked blocks: " + blockIndex);
            addBlockToForks(block);
        }
    }

    // Check if a fork extends the current chain and try to adopt it if longer
    private void checkForksForExtension(Block newLastBlock) {
        int nextIndex = newLastBlock.getIndex() + 1;
        List<Block> forkedBlocks = forks.get(nextIndex);

        if (forkedBlocks != null && !forkedBlocks.isEmpty()) {
            for (Block forkedBlock : forkedBlocks) {
                if (forkedBlock.getPreviousHash().equals(newLastBlock.getHash())) {
                    System.out.println("Found fork extending the chain: " + forkedBlock.getHash());
                    processBlock(forkedBlock);  // Recursively process the forked block
                    break;
                }
            }
        }
    }

    public synchronized void revertCoinbaseTransaction(Block block) {
        Transaction coinbaseTransaction = block.getTransactions().get(0);  // Assuming coinbase is the first transaction
        if (!"COINBASE".equals(coinbaseTransaction.sender)) {
            return;
        }

        for (TransactionOutput output : coinbaseTransaction.outputs) {
            Blockchain.UTXOs.remove(output.id);  // Remove the UTXOs created by the coinbase transaction
            System.out.println("Reverted coinbase transaction for block: " + block.getHash());
        }
    }

    // Method to handle chain reorganization if a longer fork is detected
    private void checkForLongerFork(int forkIndex) {
        List<Block> forkedBlocks = forks.get(forkIndex);
        if (forkedBlocks == null || forkedBlocks.isEmpty()) {
            System.out.println("No competing fork found at index: " + forkIndex);
            return;
        }
        for (Block forkedBlock : forkedBlocks) {
            // Calculate the length of the forked chain starting from the forked block
            List<Block> potentialForkChain = getExtendedForkChain(forkedBlock);
            // Compare the forked chain length to the current chain length beyond the fork point
            int currentChainLengthFromFork = blockchain.getChain().size() - forkIndex;
            if (potentialForkChain.size() > currentChainLengthFromFork) {
                System.out.println("Competing fork is longer. Reorganizing chain...");
                reorganizeChain(potentialForkChain);
                return; // We reorganized, no need to check other forks at this index
            }
        }
        System.out.println("Current chain is longer or equal. No reorganization needed.");
    }

    // Helper method to extend a forked chain and return its length
    private List<Block> getExtendedForkChain(Block initialBlock) {
        List<Block> extendedChain = new ArrayList<>();
        extendedChain.add(initialBlock);

        int currentIndex = initialBlock.getIndex() + 1;
        String previousHash = initialBlock.getHash();

        // Try to extend the chain using the blocks in the forks map
        while (true) {
            List<Block> nextBlocks = forks.get(currentIndex);
            if (nextBlocks == null || nextBlocks.isEmpty()) {
                break; // No more extensions possible
            }
            // Find a block in nextBlocks that matches the previous hash
            Block nextBlock = null;
            for (Block block : nextBlocks) {
                if (block.getPreviousHash().equals(previousHash)) {
                    nextBlock = block;
                    break;
                }
            }

            if (nextBlock == null) {
                break; // No valid extension found for this fork
            }
            extendedChain.add(nextBlock);// Add the next block to the extended chain
            currentIndex++; // Update the index and previous hash to extend further
            previousHash = nextBlock.getHash();
        }
        return extendedChain;
    }

    private void reorganizeChain(List<Block> competingChain) {
        System.out.println("Reorganizing chain due to a longer fork...");

        // Step 1: Rollback the current chain to the fork point
        List<Block> discardedBlocks = rollbackToFork(competingChain.get(0).getIndex() - 1);
        // Step 2: Re-add transactions from discarded blocks to the unconfirmed pool
        blockchain.reAddTransactionsFromDiscardedBlocks(discardedBlocks);
        // Step 3: Validate and add the competing chain
        for (Block block : competingChain) {
            if (!blockchain.addAndValidateBlock(block)) {
                System.out.println("Fork block failed validation: " + block.getHash());
                return; // Abort if fork chain is invalid
            }
            System.out.println("Fork block added to the chain: " + block.getHash());
            // Ensure UTXOs and confirmations are updated correctly for each block
            blockchain.updateUTXOs(block, true);
            blockchain.ageUTXOs();  // Increment confirmations as each block is re-added
        }
    }

    // Rollback the blockchain to a certain index (fork point), returning discarded blocks
    private List<Block> rollbackToFork(int forkIndex) {
        System.out.println("Rolling back chain to index: " + forkIndex);
        List<Block> discardedBlocks = new ArrayList<>();
        while (blockchain.getLastBlock().getIndex() > forkIndex) {
            Block discardedBlock = blockchain.getLastBlock();
            discardedBlocks.add(discardedBlock);
            blockchain.revertUTXOs(discardedBlock);
            blockchain.removeLastBlock();
            System.out.println("Discarded block: " + discardedBlock.getHash());
        }
        return discardedBlocks;
    }

    public void addBlock(Block block) {blockQueue.add(block);}
    private void addBlockToForks(Block block) {
        forks.computeIfAbsent(block.getIndex(), k -> new ArrayList<>()).add(block);
        revertCoinbaseTransaction(block);
    }
}