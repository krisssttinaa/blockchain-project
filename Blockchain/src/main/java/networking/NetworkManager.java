package networking;

import blockchain.Blockchain;
import blockchain.ForkResolution;
import blockchain.StringUtil;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.PublicKey;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static blockchain.Main.NODE_PORT;

public class NetworkManager {
    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>(); // Store PeerInfo by public key
    private final ExecutorService networkPool = Executors.newCachedThreadPool(); // Thread pool for networking tasks
    private Blockchain blockchain;
    private final PublicKey localPublicKey;
    private final Gson gson = new Gson(); // Gson instance for JSON handling
    private static final int MAX_RETRIES = 3; // Maximum number of retry attempts
    private final ForkResolution forkResolution; // Added ForkResolution reference
    private final int gossipInterval = 60000; // Gossip interval in milliseconds

    public NetworkManager(PublicKey localPublicKey, ForkResolution forkResolution) {
        this.localPublicKey = localPublicKey;
        startServer(); // Start the server to accept incoming connections on the same port (7777)
        startGossiping(); // Start gossiping for all nodes
        this.forkResolution = forkResolution; // Initialize the ForkResolution
    }

    // Starts the server to accept incoming connections
    public void startServer() {
        networkPool.submit(() -> {
            try (ServerSocket serverSocket = new ServerSocket(NODE_PORT)) {
                // Retrieve the actual port the server is listening on
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
                System.err.println("Server failed to start on port " + NODE_PORT + ": " + e.getMessage());
            }
        });
    }

    public void broadcastMessageExceptSender(Message message, String senderIp) {
        peers.forEach((publicKey, peerInfo) -> {
            // Avoid sending the message back to the original sender
            if (!peerInfo.getIpAddress().equals(senderIp) && peerInfo.isConnected()) {
                try {
                    sendMessageToPeer(peerInfo.getSocket(), message); // Send the message to other peers
                } catch (IOException e) {
                    System.err.println("Failed to send message to peer " + peerInfo.getIpAddress() + ": " + e.getMessage());
                    peerInfo.setConnected(false); // Mark the peer as disconnected if there was an issue
                }
            }
        });
    }

    private void handleNewConnection(Socket socket) {
        String peerIp = socket.getInetAddress().getHostAddress();
        synchronized (peers) {
            // Check if there's already a connected peer with the same IP
            Optional<PeerInfo> existingPeerInfo = peers.values().stream()
                    .filter(peerInfo -> peerInfo.getIpAddress().equals(peerIp) && peerInfo.isConnected())
                    .findFirst();

            if (existingPeerInfo.isPresent()) {
                System.out.println("A connection already exists with " + peerIp + ". Closing the new socket.");
                try {
                    socket.close(); // Close the new socket to avoid duplicate connections
                } catch (IOException e) {
                    System.err.println("Failed to close duplicate socket: " + e.getMessage());
                }
                return;  // Avoid creating another connection
            }

            // Proceed with handling the new connection
            Node node = new Node(socket, blockchain, this, forkResolution);
            networkPool.submit(node); // Start the node in a new thread
        }
    }

    public void connectToPeer(String address, int port) {
        networkPool.submit(() -> {
            synchronized (peers) {
                // Check if there is already an established connection
                Optional<PeerInfo> existingPeerInfo = peers.values().stream()
                        .filter(peerInfo -> peerInfo.getIpAddress().equals(address) && peerInfo.isConnected())
                        .findFirst();

                if (existingPeerInfo.isPresent()) {
                    System.out.println("Connection to peer " + address + " already exists. Skipping new connection.");
                    return;  // Exit if the peer is already connected
                }
            }

            int attempts = 0;
            while (attempts < MAX_RETRIES) {
                try {
                    System.out.println("Attempting to connect to peer: " + address + " on port: " + port + " (Attempt " + (attempts + 1) + ")");
                    Socket socket = new Socket(address, port);
                    socket.setKeepAlive(true);  // Enable TCP keep-alive

                    synchronized (peers) {
                        // Double-check after creating the socket if the peer was connected in the meantime
                        Optional<PeerInfo> existingPeerInfo = peers.values().stream()
                                .filter(peerInfo -> peerInfo.getIpAddress().equals(address) && peerInfo.isConnected())
                                .findFirst();

                        if (existingPeerInfo.isPresent()) {
                            System.out.println("A connection was established while creating a new one. Closing new socket.");
                            socket.close();
                            return;  // If already connected, close the newly created socket
                        }

                        // Proceed with handling the new connection
                        handleNewConnection(socket);
                        String peerPublicKey = StringUtil.getStringFromKey(getPeerPublicKey(socket));
                        PeerInfo peerInfo = peers.get(peerPublicKey);

                        if (peerInfo != null) {
                            peerInfo.setSocket(socket);
                            peerInfo.setConnected(true);  // Mark the peer as connected
                            System.out.println("Successfully connected to peer: " + address);
                            return;
                        }
                    }
                } catch (IOException e) {
                    attempts++;
                    System.err.println("Failed to connect to peer: " + address + " on attempt " + attempts + ". Error: " + e.getMessage());
                    if (attempts >= MAX_RETRIES) {
                        System.err.println("Max retry attempts reached. Marking peer " + address + " as disconnected.");
                        updatePeerConnectionStatus(address, false);
                    } else {
                        try {
                            Thread.sleep(2000); // Wait before retrying
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        });
    }

    // Initiates the gossip protocol
    void startGossiping() {
        networkPool.submit(() -> {
            while (true) {
                try {
                    Thread.sleep(gossipInterval);
                    gossip();
                } catch (InterruptedException e) {
                    System.err.println("Gossiping thread interrupted: " + e.getMessage());
                    break;
                } catch (Exception e) {
                    System.err.println("Unexpected error during gossiping: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }

    // Gossip the peer list to all connected peers
    private void gossip() {
        if (peers.isEmpty()) {
            System.out.println("No peers to gossip with.");
            return;
        }
        System.out.println("Gossiping peer list to connected peers.");
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
                System.out.println("PeerInfo before skipping gossip:");
                System.out.println("IP Address: " + peerInfo.getIpAddress());
                System.out.println("Is Connected: " + peerInfo.isConnected());
                System.out.println("Socket: " + peerInfo.getSocket());
                System.out.println("Skipping gossip to " + peerInfo.getIpAddress() + " because it's marked as disconnected.");
            }
        });
    }

    // Broadcasts a message to all connected peers
    public synchronized void broadcastMessage(Message message) {
        try {
            System.out.println("Broadcasting message to all peers...");
            peers.forEach((publicKey, peerInfo) -> {
                Socket socket = peerInfo.getSocket();
                if (peerInfo.isConnected() && socket != null) {
                    try {
                        sendMessageToPeer(socket, message);
                    } catch (IOException e) {
                        System.err.println("Failed to send message to peer " + peerInfo.getIpAddress() + ": " + e.getMessage());
                        peerInfo.setConnected(false); // Mark the peer as disconnected
                    }
                }
            });
        } catch (Exception e) {
            System.err.println("Failed to broadcast message to all peers: " + e.getMessage());
        }
    }

    // Sends a message to a specific peer using an existing socket connection
    private void sendMessageToPeer(Socket socket, Message message) throws IOException {
        if (socket == null || socket.isClosed()) {
            throw new IOException("Socket is not available or closed.");
        }
        try {
            PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
            String messageJson = gson.toJson(message);
            output.println(messageJson);
            System.out.println("Sent message to peer " + socket.getInetAddress().getHostAddress());
        } catch (IOException e) {
            System.err.println("Failed to send message to peer at " + socket.getInetAddress().getHostAddress() + ": " + e.getMessage());
            throw e;
        }
    }

    public void updatePeerConnectionStatus(String peerIp, boolean status) {
        synchronized (peers) {
            peers.values().stream()
                    .filter(peerInfo -> peerInfo.getIpAddress().equals(peerIp))
                    .forEach(peerInfo -> {
                        System.out.println("Updating connection status for peer " + peerIp + " to " + status);
                        peerInfo.setConnected(status);
                    });
        }
    }

    public void updatePeerSocket(String peerIp, Socket socket) {
        synchronized (peers) {
            peers.values().stream()
                    .filter(peerInfo -> peerInfo.getIpAddress().equals(peerIp))
                    .forEach(peerInfo -> {
                        System.out.println("Updating socket for peer " + peerIp + " to " + socket);
                        peerInfo.setSocket(socket);
                    });
        }
    }

    public void setBlockchain(Blockchain blockchain) {this.blockchain = blockchain;}
    public String getLocalPublicKey() {return StringUtil.getStringFromKey(localPublicKey);}
    public Map<String, PeerInfo> getPeers() {
        return peers;
    }
    public PublicKey getPeerPublicKey(Socket socket) {return StringUtil.getKeyFromString(socket.getInetAddress().getHostAddress());}

    // Utility method to get the local IP address
    private String getLocalIPAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (IOException e) {
            System.err.println("Failed to get local IP address: " + e.getMessage());
            return "Unknown IP";
        }
    }
}