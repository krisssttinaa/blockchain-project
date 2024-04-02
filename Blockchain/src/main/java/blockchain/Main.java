package blockchain;
import networking.NetworkManager;
import networking.Node;
import java.security.Security;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Main {
    public static HashMap<String, TransactionOutput> UTXOs = new HashMap<>(); // List of all unspent transactions.
    public static List<Transaction> unconfirmedTransactions = new ArrayList<>();
    public static float minimumTransaction = 0; // Minimum transaction value.
    public static int difficulty = 6; // Difficulty level for mining.



    public static void main(String[] args) {
        // Add Bouncy Castle as the security provider
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        NetworkManager networkManager = new NetworkManager();
        // Initialize the blockchain
        Blockchain blockchain = new Blockchain();

        // Create wallets
        Wallet walletA = new Wallet();
        Wallet walletB = new Wallet();

        // Print wallet details for debugging
        System.out.println("Wallet A's public key: " + StringUtil.getStringFromKey(walletA.publicKey));
        System.out.println("Wallet B's public key: " + StringUtil.getStringFromKey(walletB.publicKey));

        // Simulate transactions
        // Here you would simulate creating transactions, for example:
        Transaction transaction = walletA.sendFunds(walletB.publicKey, 0); // Send 5 coins from walletA to walletB
        if (transaction != null) {
            blockchain.addTransaction(transaction); // Add transaction to the pool of unconfirmed transactions
        }

        // Attempt to mine a block and add it to the chain
        blockchain.createAndAddBlock();

        // Optional: Start the networking service if your application is networked
        //Node node = new Node(blockchain);
        // listening for connections, connect to peers bla bla
        // node.connectToPeer("192.168.1.2:5000");

        // Print the current state of the blockchain
        blockchain.printChain();

        // Verify the integrity of the blockchain
        System.out.println("Blockchain is valid: " + blockchain.isValidChain());
    }
    public static void printUTXOs() {
        System.out.println("Current UTXOs:");
        for (String id : UTXOs.keySet()) {
            TransactionOutput utxo = UTXOs.get(id);
            System.out.println("UTXO ID: " + id + ", Amount: " + utxo.value + ", Owner: " + StringUtil.getStringFromKey(utxo.recipient));
            System.out.println();
        }
    }

}

/*
    public static void main(String[] args) {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

        Blockchain blockchain = new Blockchain(); // Initialize the blockchain with the genesis block
        Wallet walletA = new Wallet(); // Create a wallet for testing
        //Wallet recipientWallet = new Wallet(); // Simulate a recipient wallet
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
            //blockchain.printChain();
            printUTXOs();
        }
    }
* */