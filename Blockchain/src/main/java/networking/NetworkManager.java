package networking;

import blockchain.Blockchain;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.PublicKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetworkManager {
    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>(); // Store PeerInfo by public key
    private final ExecutorService networkPool = Executors.newCachedThreadPool(); // Thread pool for networking tasks
    private final Blockchain blockchain;
    private final int serverPort;
    private final PublicKey localPublicKey;
    private final Gson gson = new Gson(); // Gson instance for JSON handling
    private final String seedNodeAddress; // Store the seed node address

    public NetworkManager(Blockchain blockchain, int serverPort, PublicKey localPublicKey, String seedNodeAddress) {
        this.blockchain = blockchain;
        this.serverPort = serverPort;
        this.localPublicKey = localPublicKey;
        this.seedNodeAddress = seedNodeAddress;
    }

    // Starts the server to accept incoming connections
    public void startServer() {
        networkPool.submit(() -> {
            try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
                System.out.println("Server started, waiting for connections...");
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        handleNewConnection(clientSocket, false);
                    } catch (IOException e) {
                        System.err.println("Error accepting new connection: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                System.err.println("Server failed to start: " + e.getMessage());
            }
        });
    }

    // Connects to a peer using its IP address and port
    public void connectToPeer(String address, int port) {
        System.out.println("Attempting to connect to peer: " + address);
        networkPool.submit(() -> {
            try {
                Socket socket = new Socket(address, port);
                handleNewConnection(socket, address.equals(seedNodeAddress));
                System.out.println("Successfully connected to peer: " + address);
            } catch (IOException e) {
                System.err.println("Failed to connect to peer: " + address + ". Error: " + e.getMessage());
            }
        });
    }

    // Handles a new connection from a peer
    private void handleNewConnection(Socket socket, boolean isSeedNode) {
        Node node = new Node(socket, blockchain, this, isSeedNode);
        networkPool.submit(node); // Start the node in a new thread
    }

    // Broadcasts a message to all connected peers
    public void broadcastMessage(Message message) {
        System.out.println("Broadcasting message to all peers...");
        peers.forEach((publicKey, peerInfo) -> {
            if (peerInfo.isConnected()) {
                try {
                    sendMessageToPeer(peerInfo.getIpAddress(), message);
                } catch (IOException e) {
                    System.err.println("Failed to send message to peer " + peerInfo.getIpAddress() + ": " + e.getMessage());
                    peerInfo.setConnected(false); // Mark the peer as disconnected
                }
            }
        });
    }

    // Sends a message to a specific peer
    private void sendMessageToPeer(String ip, Message message) throws IOException {
        try (Socket socket = new Socket(ip, serverPort)) { // Open connection
            PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
            String messageJson = gson.toJson(message);
            output.println(messageJson); // Send message
            System.out.println("Sent message to peer " + ip);
        } catch (IOException e) {
            System.err.println("Failed to send message to peer at " + ip + ": " + e.getMessage());
        }
    }

    public PublicKey getLocalPublicKey() {
        return localPublicKey;
    }

    public Map<String, PeerInfo> getPeers() {
        return peers;
    }
}