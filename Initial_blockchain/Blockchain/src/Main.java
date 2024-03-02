import java.sql.SQLOutput;
import java.util.List;
public class Main {
    //private static int counter=0; //ne, bad idea
    public static void main(String[] args) {
        try {
            // Create a blockchain and a miner
            //System.out.println("we get here");
            Blockchain blockchain = new Blockchain();
            printBlockchain(blockchain); //to print the genesis block
            //System.out.println("we get here, yey");

            Miner miner = new Miner("Blokčič");
            System.out.println("Initial amount of currency of Miner: "+miner.getReward());
            System.out.println();System.out.println();

            // Mining loop, just until you stop the execution
            while (true) {
                miner.mine(blockchain);
                printBlockchain(blockchain);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public static void printBlockchain(Blockchain blockchain) {
        List<Block> chain = blockchain.getChain();
        // Get the most recently added block
        Block latestBlock = chain.get(chain.size() - 1);

        System.out.println("---------------------------------");
        System.out.println("NEW BLOCK:");
        System.out.println("Block Index: " + latestBlock.getIndex());
        System.out.println("Timestamp: " + latestBlock.getTimestamp());
        System.out.println("Previous Hash: " + latestBlock.getPreviousHash());

        System.out.println("Transactions:");
        for (Transaction transaction : latestBlock.getTransactions()) {
            System.out.println("  Sender: " + transaction.getSender());
            System.out.println("  Recipient: " + transaction.getRecipient());
            System.out.println("  Amount: " + transaction.getAmount());
        }

        System.out.println("Block Hash: " + latestBlock.getHash());
        System.out.println("---------------------------------");

        System.out.println();
        System.out.println();
    }
}

/*
System.out.println("Blockchain Contents:");
        for (Block block : chain) {
            System.out.println("Block Index: " + block.getIndex());
            System.out.println("Timestamp: " + block.getTimestamp());
            System.out.println("Previous Hash: " + block.getPreviousHash());

            System.out.println("Transactions:");
            for (Transaction transaction : block.getTransactions()) {
                System.out.println("  Sender: " + transaction.getSender());
                System.out.println("  Recipient: " + transaction.getRecipient());
                System.out.println("  Amount: " + transaction.getAmount());
            }

            System.out.println("Block Hash: " + block.getHash());
            System.out.println("---------------------------------");
        }
 */