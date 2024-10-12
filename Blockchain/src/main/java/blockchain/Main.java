package blockchain;

import networking.NetworkManager;
import ledger.Wallet;
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
    public static boolean syncTriggered = false; // Prevent multiple syncs

    public static void main(String[] args) {
        System.out.println("Starting blockchain node...");
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        Wallet senderWallet = new Wallet();
        minerAddress = StringUtil.getStringFromKey(senderWallet.publicKey);

        Blockchain blockchain = new Blockchain();
        ForkResolution forkResolution = new ForkResolution(blockchain);
        new Thread(forkResolution).start();
        NetworkManager networkManager = new NetworkManager(senderWallet.publicKey, forkResolution);
        blockchain.setNetworkManager(networkManager);
        networkManager.setBlockchain(blockchain);

        try {
            String currentIp = getCurrentIp();
            System.out.println("Current IP Address: " + currentIp);
            if (!Objects.equals(currentIp, SEED_NODE_ADDRESS)) {
                System.out.println("Connecting to seed node at " + SEED_NODE_ADDRESS);
                networkManager.connectToPeer(SEED_NODE_ADDRESS, NODE_PORT);

                System.out.println("Waiting for connection to seed node...");
                while (!networkManager.isPeerConnected(SEED_NODE_ADDRESS)) {
                    try {
                        Thread.sleep(500);  // Sleep for half a second to avoid busy waiting
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                System.out.println("Connected to seed node.");
                networkManager.requestPeerListFromSeedNode(SEED_NODE_ADDRESS);
                if (!syncTriggered) {
                    syncTriggered = true; // Ensure we don’t trigger sync multiple times
                    networkManager.requestChainTipFromPeers();  // Ask for the blockchain tip after getting the peer list
                }
            }
        } catch (SocketException e) {
            System.err.println("Error determining IP address: " + e.getMessage());
        }

        BlockchainCLI cli = new BlockchainCLI(blockchain, senderWallet, networkManager, forkResolution);
        cli.start();
    }

    private static String getCurrentIp() throws SocketException { // Helper method to get the current machine's IP address
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