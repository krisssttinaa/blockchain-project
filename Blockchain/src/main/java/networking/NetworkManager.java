package networking;

import blockchain.Blockchain;
import blockchain.Constants;
import blockchain.ForkResolution;
import blockchain.StringUtil;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class NetworkManager {
    private final ForkResolution forkResolution; // Added ForkResolution reference
    private Blockchain blockchain;
    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>(); // Store PeerInfo by public key
    private final ExecutorService networkPool = Executors.newCachedThreadPool(); // Thread pool for networking tasks
    private final PublicKey localPublicKey;
    private final Gson gson = new Gson(); // Gson instance for JSON handling
    private static final int MAX_RETRIES = Constants.MAX_RETRIES; // Maximum number of retry attempts
    private static final int NODE_PORT = Constants.NODE_PORT; // Node's listening port
    private final BlockingQueue<OutgoingMessage> outgoingMessageQueue = new LinkedBlockingQueue<>();
    private final Thread outgoingWorkerThread;

    public NetworkManager(PublicKey localPublicKey, ForkResolution forkResolution) {
        this.localPublicKey = localPublicKey;
        this.forkResolution = forkResolution; // Initialize the ForkResolution
        startServer(); // Start the server to accept incoming connections on the same port (7777)
        new GossipManager(peers, networkPool, gson, this);
        new PingManager(peers, networkPool, this);
        outgoingWorkerThread = new Thread(this::processOutgoingMessages);
        outgoingWorkerThread.start();
    }

    private static class OutgoingMessage {
        private final Socket socket;
        private final Message message;

        public OutgoingMessage(Socket socket, Message message) {
            this.socket = socket;
            this.message = message;
        }
    }

    private void processOutgoingMessages() {
        while (true) {
            try {
                OutgoingMessage task = outgoingMessageQueue.take();
                sendMessageToPeer(task.socket, task.message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Outgoing message worker interrupted.");
                break;
            } catch (IOException e) {
                System.err.println("Failed to send outgoing message: " + e.getMessage());
            }
        }
    }

    // Enqueue the message to the outgoing queue
    public synchronized void sendOutgoingMessage(Socket socket, Message message) {
        outgoingMessageQueue.add(new OutgoingMessage(socket, message));
    }

    public void startServer() { // Starts the server to accept incoming connections
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

                        handleNewConnection(socket); // Proceed with handling the new connection
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

    public synchronized void broadcastMessage(Message message) {  // Broadcasts a message to all connected peers
        try {
            System.out.println("Broadcasting message to all peers...");
            peers.forEach((publicKey, peerInfo) -> {
                Socket socket = peerInfo.getSocket();
                if (peerInfo.isConnected() && socket != null) {
                    sendOutgoingMessage(socket, message);
                }
            });
        } catch (Exception e) {
            System.err.println("Failed to broadcast message to all peers: " + e.getMessage());
        }
    }

    public void broadcastMessageExceptSender(Message message, String senderIp) {
        peers.forEach((publicKey, peerInfo) -> {
            if (!peerInfo.getIpAddress().equals(senderIp) && peerInfo.isConnected()) {
                sendOutgoingMessage(peerInfo.getSocket(), message); // Send the message to other peers
            }
        });
    }

    public synchronized void sendMessageToPeer(Socket socket, Message message) throws IOException { // Sends a message to a specific peer using an existing socket connection
        if (socket == null || socket.isClosed()) {
            throw new IOException("Socket is not available or closed.");
        }
        int retryCount = 0;
        boolean messageSent = false;

        while (retryCount < MAX_RETRIES && !messageSent) {
            try {
                PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
                String messageJson = gson.toJson(message);
                output.println(messageJson);
                System.out.println("Sent message to peer " + socket.getInetAddress().getHostAddress());
                messageSent = true;
            } catch (IOException e) {
                retryCount++;
                System.err.println("Failed to send message to peer at " + socket.getInetAddress().getHostAddress() + " (Attempt " + retryCount + "): " + e.getMessage());
                if (retryCount >= MAX_RETRIES) {
                    System.err.println("Max retry attempts reached for peer. Removing peer.");
                    String publicKey = StringUtil.getStringFromKey(getPeerPublicKey(socket));
                    removePeer(publicKey);
                    if (!socket.isClosed()) {
                        socket.close();
                    }
                }
                try {
                    Thread.sleep(2000); // Delay before retry
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }

    public void updatePeerConnectionStatus(String peerIp, boolean status) {
        synchronized (peers) {
            peers.values().stream()
                    .filter(peerInfo -> peerInfo.getIpAddress().equals(peerIp))
                    .forEach(peerInfo -> {
                        System.out.println("Updating connection status for peer " + peerIp + " to " + status);
                        peerInfo.setConnected(status);

                        // If the peer is disconnected, remove it
                        if (!status) {
                            String peerPublicKey = StringUtil.getStringFromKey(getPeerPublicKey(peerInfo.getSocket()));
                            removePeer(peerPublicKey); // Remove from peers map
                        }
                    });
        }
    }

    public void requestPeerListFromSeedNode(String seedNodeIp) { // Request peer list from the seed node or any peer directly
        System.out.println("Requesting peer list from seed node: " + seedNodeIp);
        PeerInfo seedNodePeer = peers.values().stream()
                .filter(peerInfo -> peerInfo.getIpAddress().equals(seedNodeIp) && peerInfo.isConnected())
                .findFirst()
                .orElse(null);
        if (seedNodePeer == null) {
            System.err.println("No connected peer found for the seed node. Ensure the connection exists before requesting the peer list.");
            return;
        }
        Socket seedNodeSocket = seedNodePeer.getSocket();
        Message peerDiscoveryRequest = new Message(MessageType.PEER_DISCOVERY_REQUEST, "Requesting peer list from seed node");
        sendOutgoingMessage(seedNodeSocket, peerDiscoveryRequest); // Reuse the existing connection
    }

    public void requestChainTipFromPeers() {
        List<PeerInfo> connectedPeers = peers.values().stream()
                .filter(PeerInfo::isConnected)
                .toList();
        if (connectedPeers.isEmpty()) {
            System.err.println("No connected peers to request blockchain tip from.");
            return;
        }
        PeerInfo randomPeer = connectedPeers.get(new Random().nextInt(connectedPeers.size())); // Pick a random peer from the connected peers
        try {
            requestTipFromPeer(randomPeer);
        } catch (IOException e) {
            System.err.println("Failed to request tip from peer " + randomPeer.getIpAddress() + ": " + e.getMessage());
        }
    }

    public void requestTipFromPeer(PeerInfo peerInfo) throws IOException { // Request tip from the chosen peer (using its socket)
        Socket peerSocket = peerInfo.getSocket();
        if (peerSocket != null && !peerSocket.isClosed()) {
            // We just send the message using the existing connection (no need for a new Node instance)
            Message tipRequest = new Message(MessageType.TIP_REQUEST, "Requesting blockchain tip");

            sendOutgoingMessage(peerSocket, tipRequest);  // Use the sendMessageToPeer method in NetworkManager
            System.out.println("Blockchain tip request sent to peer: " + peerInfo.getIpAddress());
        } else {
            throw new IOException("Peer socket is unavailable or closed.");
        }
    }

    public void syncWithPeers(int latestIndex) { // Syncing process
        int currentIndex = blockchain.getLastBlock().getIndex();
        int blocksToFetch = latestIndex - currentIndex;
        if (blocksToFetch <= 0) {
            System.out.println("No blocks to sync. Already up to date.");
            return;
        }

        System.out.println("Syncing " + blocksToFetch + " blocks from peers.");
        // Filter the list of peers to only include those that are connected
        List<PeerInfo> connectedPeers = peers.values().stream()
                .filter(PeerInfo::isConnected)  // Only peers that are actually connected
                .toList();  // Collect into a list
        if (connectedPeers.isEmpty()) {
            System.out.println("No connected peers available for syncing.");
            return;
        }

        // Divide the blocks to fetch across connected peers
        int blocksPerPeer = Math.max(1, blocksToFetch / connectedPeers.size());
        for (int i = 0; i < connectedPeers.size(); i++) {
            PeerInfo peer = connectedPeers.get(i);
            int startIndex = currentIndex + i * blocksPerPeer;
            int endIndex = Math.min(startIndex + blocksPerPeer, latestIndex);
            requestBlocksFromPeer(peer, startIndex, endIndex);
        }
    }

    private void requestBlocksFromPeer(PeerInfo peer, int startIndex, int endIndex) { // Request specific block range from a peer
        System.out.println("Requesting blocks " + startIndex + " to " + endIndex + " from peer " + peer.getIpAddress());
        String requestData = startIndex + "," + endIndex;
        Message blockRequest = new Message(MessageType.BLOCK_REQUEST, requestData);
        sendOutgoingMessage(peer.getSocket(), blockRequest);
    }

    public boolean isPeerConnected(String ipAddress) {
        Optional<PeerInfo> peerInfo = peers.values().stream()
                .filter(p -> p.getIpAddress().equals(ipAddress))
                .findFirst();
        return peerInfo.isPresent() && peerInfo.get().isConnected();
    }

    public synchronized void removePeer(String peerPublicKey) {peers.remove(peerPublicKey);}
    public void setBlockchain(Blockchain blockchain) {this.blockchain = blockchain;}
    public String getLocalPublicKey() {return StringUtil.getStringFromKey(localPublicKey);}
    public Map<String, PeerInfo> getPeers() {return peers;}
    public PublicKey getPeerPublicKey(Socket socket) {return StringUtil.getKeyFromString(socket.getInetAddress().getHostAddress());}
}