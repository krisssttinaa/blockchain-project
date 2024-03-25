package blockchain;

import java.util.ArrayList;
import java.util.List;

public class Node {
    private Wallet wallet;
    private Blockchain blockchain;

    public Node() {
        this.blockchain = new Blockchain();
        this.wallet = new Wallet();
    }

    public void initialize() {
        // Initialize components, if necessary
    }

    public void startMining() {
        // Start mining blocks
        while (true) {
            Block newBlock = new Block(blockchain.getLatestBlock().getIndex() + 1, blockchain.getLatestBlock().getHash(), "Block data");
            //newBlock.mineBlock(blockchain.getDifficulty());
            blockchain.addBlock(newBlock);
            // Simulate some delay between mining attempts
            try {
                Thread.sleep(1000); // Adjust delay as needed
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void startNetworkingService() {
        // Start networking service to communicate with other nodes
    }

    // Getter for the blockchain
    public Blockchain getBlockchain() {
        return blockchain;
    }

    // Getter for the wallet
    public Wallet getWallet() {
        return wallet;
    }
}
