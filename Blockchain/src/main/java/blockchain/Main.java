package blockchain;
import networking.NetworkManager;
import networking.Server;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.Security;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;

public class Main {
    public static HashMap<String, TransactionOutput> UTXOs = new HashMap<>(); // List of all unspent transactions.
    public static List<Transaction> unconfirmedTransactions = new ArrayList<>();
    public static float minimumTransaction = 0; // Minimum transaction amount
    public static int difficulty = 6; // Difficulty level for mining.
    private static final String BLOCKCHAIN_FILE = "blockchain.dat";

    public static void main(String[] args) {
        // Add Bouncy Castle as the security provider
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        Blockchain myblockchain = new Blockchain();

        // Load blockchain from disk if it exists
        File file = new File(BLOCKCHAIN_FILE);
        if (file.exists()) {
            myblockchain.loadBlockchain(BLOCKCHAIN_FILE);
        } else {
            // Otherwise, start a new blockchain
            myblockchain = new Blockchain();
        }

        // Start server to accept incoming connections
        int serverPort = 7777;
        NetworkManager networkManager = new NetworkManager(myblockchain, serverPort);
        try {
            Server server = new Server(serverPort, networkManager);
            server.start(); // Start the server in a separate thread
        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
            e.printStackTrace();
        }

        try {
            String currentIp = getCurrentIp();
            System.out.println("Current IP Address: " + currentIp);

            if (!"172.17.0.2".equals(currentIp)) {
                // This is not the first container, connect to the first container and start discovering peers
                String peerAddress = "172.17.0.2"; // The known address of the first container
                //networkManager.connectToPeer("172.17.0.2");
                //node.connectToPeer(peerAddress);
                // Start peer discovery
                //node.startPeerDiscovery("172.17.0.2");
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }

        // Create wallets
        Wallet walletA = new Wallet();
        Wallet walletB = new Wallet();

        // Print wallet details for debugging
        System.out.println("Wallet A's public key: " + StringUtil.getStringFromKey(walletA.publicKey));
        System.out.println("Wallet B's public key: " + StringUtil.getStringFromKey(walletB.publicKey));

        // Simulate transactions
        // Here you would simulate creating transactions, for example:
        Transaction transaction = walletA.sendFunds(walletB.publicKey, 0); // Send 0 coins from walletA to walletB
        if (transaction != null) {
            myblockchain.addTransaction(transaction); // Add transaction to the pool of unconfirmed transactions
        }

        // Attempt to mine a block and add it to the chain
        myblockchain.createAndAddBlock();

        // Print the current state of the blockchain
        myblockchain.printChain();

        // Verify the integrity of the blockchain
        System.out.println("Blockchain is valid: " + myblockchain.isValidChain());

        // Start the CLI for user interaction
        BlockchainCLI cli = new BlockchainCLI(myblockchain);
        cli.start();

        // Save blockchain to disk before exit
        myblockchain.saveBlockchain(BLOCKCHAIN_FILE);
    }

    public static String getCurrentIp() throws SocketException {
        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface networkInterface = networkInterfaces.nextElement();
            Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
            while (inetAddresses.hasMoreElements()) {
                InetAddress inetAddress = inetAddresses.nextElement();
                if (inetAddress.isSiteLocalAddress()) {
                    return inetAddress.getHostAddress();
                }
            }
        }
        return null; //throw an exception
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