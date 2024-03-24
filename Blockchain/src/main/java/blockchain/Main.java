package blockchain;

public class Main {
    public static void main(String[] args) {
        try {
            // Create a node
            Node node = new Node();

            // Create a miner and add it to the node
            Miner miner = new Miner("Miner");
            node.addMiner(miner);

            // Start mining
            node.startMining();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
