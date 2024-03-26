package blockchain;
import java.security.Security;
import java.util.HashMap;

public class Main {
    public static HashMap<String, TransactionOutput> UTXOs = new HashMap<>(); // List of all unspent transactions.
    public static float minimumTransaction = 0.1f; // Minimum transaction value.
    public static int difficulty = 7; // Difficulty level for mining.

    public static void main(String[] args) {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

        Blockchain blockchain = new Blockchain(); // Initialize the blockchain with the genesis block

        Wallet walletA = new Wallet(); // Create a wallet for testing

        while (true) {
            // Simulate creating a transaction from walletA to a new recipient
            Wallet recipientWallet = new Wallet(); // Simulate a recipient wallet
            Transaction newTransaction = walletA.sendFunds(recipientWallet.publicKey, 0); // Adjust value as needed

            if (newTransaction != null) {
                Block newBlock = new Block(blockchain.getLatestBlock().getIndex() + 1, blockchain.getLatestBlock().getHash());
                newBlock.addTransaction(newTransaction);
                blockchain.addBlock(newBlock);
            }

            // Print the blockchain status
            blockchain.printChain();
        }
    }
}


/*
package blockchain;

import java.security.Security;

public class Main {

    public static void main(String[] args) {
        // Setup Bouncy Castle as a Security Provider
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

        // Create a node
        Node node = new Node();

        // Initialize the node with its wallet and blockchain
        node.initialize();

        // Optionally start mining, or start listening for transactions and blocks
        node.startMining();

        // You might also want to start a networking service to listen for messages from other nodes
        node.startNetworkingService();
    }
}

* */
// Other classes such as Block and Blockchain will be updated as needed to fit this structure.


/*
//main for blockchain
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
*/


/*

public class Main {
    public static HashMap<String, TransactionOutput> UTXOs = new HashMap<>(); // List of all unspent transactions.
    public static float minimumTransaction = 0.1f; // Minimum transaction value.
    public static Blockchain blockchain = new Blockchain(); // The blockchain for this node.
    public static int difficulty = 4; // Difficulty level for mining.

    public static void main(String[] args) {
        // Set up Bouncy Castle as a Security Provider.
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

        // Create wallets for testing.
        Wallet walletA = new Wallet();
        //Wallet walletB = new Wallet();

        // Display public and private keys.
        System.out.println("Private and public keys:");
        System.out.println(StringUtil.getStringFromKey(walletA.privateKey));
        System.out.println(StringUtil.getStringFromKey(walletA.publicKey));

        // Create and process a test transaction from WalletA to walletB.
        Transaction transaction = walletA.sendFunds(walletA.publicKey, 0);

        // Add the genesis block to the blockchain (this should include the first transaction in its data).
        Block genesisBlock = new Block(0, "0", "Genesis Block");
        genesisBlock.mineBlock(difficulty);
        blockchain.addBlock(genesisBlock);

        // Here, the node would continue its normal operation, mining new blocks and processing transactions.

        // Print the blockchain.
        blockchain.printChain();
    }

    public static Boolean isChainValid() {
        // This method should be moved into the Blockchain class.
        // It checks the integrity of the blockchain and ensures that the chain is valid.
        return blockchain.isValidChain();
    }
}
* */