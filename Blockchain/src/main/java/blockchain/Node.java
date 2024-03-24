package blockchain;

import java.util.ArrayList;
import java.util.List;

public class Node {
    private List<Miner> miners;
    private Blockchain blockchain;

    public Node() {
        miners = new ArrayList<>();
        blockchain = new Blockchain();
    }

    public void addMiner(Miner miner) {
        miners.add(miner);
    }

    public void startMining() {
        while (true) {
            for (Miner miner : miners) {
                miner.mine(blockchain);
            }
            // Simulate some delay between mining attempts
            try {
                Thread.sleep(1000); // Adjust delay as needed
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public Blockchain getBlockchain() {
        return blockchain;
    }
}
