package blockchain;

import networking.NetworkManager;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.Security;
import java.util.*;

public class Main {
    public static float minimumTransaction = 0; // Minimum transaction value
    public static int difficulty = 6; // Mining difficulty
    private static final String SEED_NODE_ADDRESS = "172.18.0.2"; // Seed node for peer-to-peer networking
    public static final int NODE_PORT = 7777;
    public static String minerAddress;
    public static float miningReward = 6.00f;
    public static int numTransactionsToMine = 2; // Number of transactions to mine in a block

    public static void main(String[] args) {
            System.out.println("Starting blockchain node...");
            Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
            Blockchain blockchain = new Blockchain();  // NetworkManager is not set yet

            ForkResolution forkResolution = new ForkResolution(blockchain);
            Thread forkThread = new Thread(forkResolution); // Fork resolution thread
            forkThread.start();

            Wallet senderWallet = new Wallet(blockchain); // Wallet for the current node
            minerAddress = StringUtil.getStringFromKey(senderWallet.publicKey); // Convert PublicKey to String
            NetworkManager networkManager = new NetworkManager(senderWallet.publicKey, forkResolution); // No blockchain yet
            blockchain.setNetworkManager(networkManager);
            networkManager.setBlockchain(blockchain);  // Ensure NetworkManager has Blockchain

            networkManager.startServer();

            try {
                String currentIp = getCurrentIp();
                System.out.println("Current IP Address: " + currentIp);
                if (!Objects.equals(currentIp, SEED_NODE_ADDRESS)) {
                    System.out.println("Connecting to seed node at " + SEED_NODE_ADDRESS);
                    networkManager.connectToPeer(SEED_NODE_ADDRESS, NODE_PORT); // Connect to the seed node
                    System.out.println("Connected to seed node.");
                }
            } catch (SocketException e) {
                System.err.println("Error determining IP address: " + e.getMessage());
            }

            BlockchainCLI cli = new BlockchainCLI(blockchain, senderWallet, networkManager, forkResolution);
            cli.start();
    }

    // Method to get the current machine's IP address
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
}
/*
    // Print all UTXOs in the system (debugging or monitoring purposes)
    public static void printUTXOs() {
        System.out.println("Current UTXOs:");
        for (String id : UTXOs.keySet()) {
            TransactionOutput utxo = UTXOs.get(id);
            System.out.println("UTXO ID: " + id + ", Amount: " + utxo.value + ", Owner: " + StringUtil.getStringFromKey(utxo.recipient));
        }
    }*/