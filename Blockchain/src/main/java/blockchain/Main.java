package blockchain;

import networking.NetworkManager;
import ledger.Wallet;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.Security;
import java.util.*;

public class Main {
    private static final String SEED_NODE_ADDRESS = "172.18.0.2"; // Seed node IP
    public static final int NODE_PORT = 7777;
    public static String minerAddress;
    private static final String WALLET_FILE_PATH = "wallet.dat";
    public static boolean syncTriggered = false; // Prevent multiple syncs

    public static void main(String[] args) {
        System.out.println("Starting blockchain node...");
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

        // Step 1: Check if this node is resuming
        boolean isResuming = checkWalletFileExists();

        // Step 2: Load or create wallet
        Wallet senderWallet = new Wallet();
        minerAddress = StringUtil.getStringFromKey(senderWallet.publicKey);

        // Step 3: Initialize blockchain and fork resolution
        Blockchain blockchain = new Blockchain();
        ForkResolution forkResolution = new ForkResolution(blockchain);
        new Thread(forkResolution).start();

        // Step 4: Start the network manager (without connecting yet)
        NetworkManager networkManager = new NetworkManager(senderWallet.publicKey, forkResolution);
        blockchain.setNetworkManager(networkManager);
        networkManager.setBlockchain(blockchain);

        try {
            String currentIp = getCurrentIp();
            System.out.println("Current IP Address: " + currentIp);

            // Step 5: Connect to seed node and get peers
            if (!Objects.equals(currentIp, SEED_NODE_ADDRESS)) {
                System.out.println("Connecting to seed node at " + SEED_NODE_ADDRESS);
                networkManager.connectToPeer(SEED_NODE_ADDRESS, NODE_PORT);
                // Step 6: Wait until the active thread count drops to 2 (main thread + background system thread)
                System.out.println("Waiting for connection to seed node...");
                while (!networkManager.isPeerConnected(SEED_NODE_ADDRESS)) {
                    try {
                        Thread.sleep(500);  // Sleep for half a second to avoid busy waiting
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                System.out.println("Connected to seed node.");

                // Step 6: Request peer list from seed node
                System.out.println("WE ASK DIRECTLY FOR THE PEER LIST");
                networkManager.requestPeerListFromSeedNode(SEED_NODE_ADDRESS);

                // Step 7: Request the chain tip once peers are discovered
                if (!syncTriggered) {
                    syncTriggered = true; // Ensure we don’t trigger sync multiple times
                    System.out.println("WE ASK DIRECTLY FOR THE CHAIN TIP");
                    networkManager.requestChainTipFromPeers();  // Ask for the blockchain tip after getting the peer list
                }
            }
        } catch (SocketException e) {
            System.err.println("Error determining IP address: " + e.getMessage());
        }

        // Step 8: Launch CLI for user interaction
        BlockchainCLI cli = new BlockchainCLI(blockchain, senderWallet, networkManager, forkResolution);
        cli.start();
    }

    // Helper method to check if the wallet file exists
    private static boolean checkWalletFileExists() {
        File walletFile = new File(WALLET_FILE_PATH);
        if (walletFile.exists() && walletFile.length() > 0) {
            System.out.println("Wallet file found. Node is resuming.");
            return true;
        }
        System.out.println("No wallet file found. Starting fresh.");
        return false;
    }

    // Helper method to get the current machine's IP address
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