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
                System.out.println("Server started, waiting for connections on port: " + serverPort);
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        System.out.println("Accepted connection from " + clientSocket.getInetAddress().getHostAddress());
                        handleNewConnection(clientSocket);
                    } catch (IOException e) {
                        System.err.println("Error accepting new connection: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                System.err.println("Server failed to start on port " + serverPort + ": " + e.getMessage());
            }
        });

        // Start the gossip protocol
        startGossiping();
    }

    // Connects to a peer using its IP address and port
    public void connectToPeer(String address, int port) {
        System.out.println("Attempting to connect to peer: " + address);
        networkPool.submit(() -> {
            try {
                Socket socket = new Socket(address, port);
                handleNewConnection(socket);
                System.out.println("Successfully connected to peer: " + address);

                // Start gossiping for all nodes
                startGossiping();
            } catch (IOException e) {
                System.err.println("Failed to connect to peer: " + address + ". Error: " + e.getMessage());
            }
        });
    }

    // Handles a new connection from a peer
    private void handleNewConnection(Socket socket) {
        Node node = new Node(socket, blockchain, this);
        networkPool.submit(node); // Start the node in a new thread
    }

    // Initiates the gossip protocol
    void startGossiping() {
        // Run the gossip protocol periodically
        networkPool.submit(() -> {
            while (true) {
                try {
                    Thread.sleep(20000); // Gossip every 20 seconds
                    gossip();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Gossiping thread interrupted: " + e.getMessage());
                }
            }
        });
    }

    // Gossip the peer list to all connected peers
    private void gossip() {
        if (peers.isEmpty()) {
            System.out.println("No peers to gossip with.");
            return; // No peers to gossip with
        }

        System.out.println("Gossiping peer list to " + peers.size() + " peers.");

        // Send the peer list to all connected peers
        peers.forEach((publicKey, peerInfo) -> {
            if (peerInfo.isConnected()) {
                try {
                    System.out.println("Gossiping peer list to " + peerInfo.getIpAddress());
                    sendMessageToPeer(peerInfo.getSocket(), new Message(MessageType.SHARE_PEER_LIST, gson.toJson(peers)));
                } catch (IOException e) {
                    System.err.println("Failed to send gossip message to peer " + peerInfo.getIpAddress() + ": " + e.getMessage());
                    peerInfo.setConnected(false);
                }
            } else {
                System.out.println("Peer " + peerInfo.getIpAddress() + " is marked as disconnected. Skipping.");
            }
        });
    }

    // Broadcasts a message to all connected peers
    public void broadcastMessage(Message message) {
        System.out.println("Broadcasting message to all peers...");
        peers.forEach((publicKey, peerInfo) -> {
            if (peerInfo.isConnected()) {
                try {
                    sendMessageToPeer(peerInfo.getSocket(), message);
                } catch (IOException e) {
                    System.err.println("Failed to send message to peer " + peerInfo.getIpAddress() + ": " + e.getMessage());
                    peerInfo.setConnected(false); // Mark the peer as disconnected
                }
            }
        });
    }

    // Sends a message to a specific peer using an existing socket connection
    private void sendMessageToPeer(Socket socket, Message message) throws IOException {
        if (socket == null || socket.isClosed()) {
            throw new IOException("Socket is not available or closed.");
        }

        try {
            PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
            String messageJson = gson.toJson(message);
            output.println(messageJson); // Send message
            System.out.println("Sent message to peer " + socket.getInetAddress().getHostAddress());
        } catch (IOException e) {
            System.err.println("Failed to send message to peer at " + socket.getInetAddress().getHostAddress() + ": " + e.getMessage());
            throw e;
        }
    }

    public PublicKey getLocalPublicKey() {
        return localPublicKey;
    }

    public Map<String, PeerInfo> getPeers() {
        return peers;
    }
}