package blockchain;

import networking.NetworkManager;
import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.Security;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;

public class Main {
    public static HashMap<String, TransactionOutput> UTXOs = new HashMap<>();
    public static List<Transaction> unconfirmedTransactions = new ArrayList<>();
    public static float minimumTransaction = 0;
    public static int difficulty = 5;
    private static final String BLOCKCHAIN_FILE = "blockchain.dat";
    private static final String SEED_NODE_ADDRESS = "172.18.0.2";

    public static void main(String[] args) {

        System.out.println("Starting blockchain node...");
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

        Blockchain blockchain = new Blockchain();
        Wallet senderWallet = new Wallet();
        NetworkManager networkManager = new NetworkManager(blockchain, 7777, senderWallet.publicKey, SEED_NODE_ADDRESS);

        // Load blockchain from disk if it exists
        File file = new File(BLOCKCHAIN_FILE);
        if (file.exists()) {
            blockchain.loadBlockchain(BLOCKCHAIN_FILE);
            System.out.println("Blockchain loaded from disk.");
        }

        try {
            String currentIp = getCurrentIp();
            System.out.println("Current IP Address: " + currentIp);

            if (SEED_NODE_ADDRESS.equals(currentIp)) {
                System.out.println("This node is the seed node.");
                networkManager.startServer();
            } else {
                System.out.println("Connecting to seed node at " + SEED_NODE_ADDRESS);
                networkManager.connectToPeer(SEED_NODE_ADDRESS, 7777);
                System.out.println("Connected to seed node.");
            }
        } catch (SocketException e) {
            System.err.println("Error determining IP address: " + e.getMessage());
        }

        // Start CLI for user interaction
        BlockchainCLI cli = new BlockchainCLI(blockchain, senderWallet, networkManager);
        cli.start();

        // Save blockchain to disk before exit
        blockchain.saveBlockchain(BLOCKCHAIN_FILE);
        System.out.println("Blockchain saved to disk.");
    }

    private static String getCurrentIp() throws SocketException {
        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface networkInterface = networkInterfaces.nextElement();

            Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
            while (inetAddresses.hasMoreElements()) {
                InetAddress inetAddress = inetAddresses.nextElement();
                if (inetAddress instanceof Inet4Address && !inetAddress.isLoopbackAddress() && !inetAddress.isLinkLocalAddress()) {
                    System.out.println("Selected IPv4 Address: " + inetAddress.getHostAddress() + " on interface " + networkInterface.getName());
                    return inetAddress.getHostAddress();
                }
            }
        }
        return null;
    }

    public static void printUTXOs() {
        System.out.println("Current UTXOs:");
        for (String id : UTXOs.keySet()) {
            TransactionOutput utxo = UTXOs.get(id);
            System.out.println("UTXO ID: " + id + ", Amount: " + utxo.value + ", Owner: " + StringUtil.getStringFromKey(utxo.recipient));
        }
    }
}
