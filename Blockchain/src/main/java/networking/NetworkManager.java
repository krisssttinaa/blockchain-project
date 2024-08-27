package networking;

import blockchain.Block;
import blockchain.Blockchain;
import blockchain.StringUtil;
import blockchain.Transaction;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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

    public NetworkManager(Blockchain blockchain, int serverPort, PublicKey localPublicKey) {
        this.blockchain = blockchain;
        this.serverPort = serverPort;
        this.localPublicKey = localPublicKey;
    }

    // Starts the server to accept incoming connections
    public void startServer() {
        networkPool.submit(() -> {
            try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
                System.out.println("Server started, waiting for connections...");
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        handleNewConnection(clientSocket);
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
                handleNewConnection(socket);
                System.out.println("Successfully connected to peer: " + address);
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
        try {
            System.out.println("Sending message to peer " + ip);
            String messageJson = gson.toJson(message);
            try (Socket socket = new Socket(ip, serverPort)) { // Open connection
                PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
                output.println(messageJson); // Send message
            }
        } catch (IOException e) {
            System.err.println("Failed to send message to peer at " + ip + ": " + e.getMessage());
        }
    }

    // Handles a received message
    private void handleMessage(Message message, PublicKey peerPublicKey) {
        try {
            switch (message.getType()) {
                case NEW_TRANSACTION -> handleNewTransaction(message, peerPublicKey);
                case NEW_BLOCK -> handleNewBlock(message, peerPublicKey);
                case BLOCKCHAIN_REQUEST -> handleBlockchainRequest(peerPublicKey);
                case BLOCKCHAIN_RESPONSE -> handleBlockchainResponse(message);
                case PEER_DISCOVERY_REQUEST -> handlePeerDiscoveryRequest(peerPublicKey);
                case PEER_DISCOVERY_RESPONSE -> handlePeerDiscoveryResponse(message);
                case SHARE_PEER_LIST -> handleSharePeerList(message);
                default -> System.out.println("Unknown message type received");
            }
        } catch (Exception e) {
            System.err.println("Error handling message: " + e.getMessage());
        }
    }

    // Handles a new transaction message
    private void handleNewTransaction(Message message, PublicKey peerPublicKey) {
        try {
            System.out.println("Received new transaction from " + StringUtil.getStringFromKey(peerPublicKey));
            Transaction transaction = gson.fromJson(message.getData(), Transaction.class);
            if (blockchain.addTransaction(transaction)) {
                broadcastMessage(new Message(MessageType.NEW_TRANSACTION, gson.toJson(transaction))); // Broadcast the transaction to other peers
            }
        } catch (Exception e) {
            System.err.println("Failed to handle new transaction: " + e.getMessage());
        }
    }

    // Handles a new block message
    private void handleNewBlock(Message message, PublicKey peerPublicKey) {
        try {
            System.out.println("Received new block from " + StringUtil.getStringFromKey(peerPublicKey));
            Block block = gson.fromJson(message.getData(), Block.class);
            if (blockchain.addAndValidateBlock(block)) {
                broadcastMessage(new Message(MessageType.NEW_BLOCK, gson.toJson(block))); // Broadcast the block to other peers
            }
        } catch (Exception e) {
            System.err.println("Failed to handle new block: " + e.getMessage());
        }
    }

    // Handles a blockchain request message
    private void handleBlockchainRequest(PublicKey peerPublicKey) {
        try {
            System.out.println("Received blockchain request from " + StringUtil.getStringFromKey(peerPublicKey));
            String blockchainJson = gson.toJson(blockchain);
            sendMessageToPeer(peers.get(StringUtil.getStringFromKey(peerPublicKey)).getIpAddress(), new Message(MessageType.BLOCKCHAIN_RESPONSE, blockchainJson));
        } catch (IOException e) {
            System.err.println("Failed to send blockchain response: " + e.getMessage());
        }
    }

    // Handles a blockchain response message
    private void handleBlockchainResponse(Message message) {
        try {
            System.out.println("Received blockchain response");
            Blockchain receivedBlockchain = gson.fromJson(message.getData(), Blockchain.class);
            blockchain.compareAndReplace(receivedBlockchain); // Replace blockchain if the received one is more valid
        } catch (Exception e) {
            System.err.println("Failed to handle blockchain response: " + e.getMessage());
        }
    }

    // Handles a peer discovery request
    private void handlePeerDiscoveryRequest(PublicKey peerPublicKey) {
        try {
            System.out.println("Received peer discovery request from " + StringUtil.getStringFromKey(peerPublicKey));

            // Serialize the peers map to JSON
            String peersJson = gson.toJson(peers);
            System.out.println("Serialized peers map: " + peersJson);

            // Send the peer discovery response back to the requesting peer
            String peerIp = peers.get(StringUtil.getStringFromKey(peerPublicKey)).getIpAddress();
            sendMessageToPeer(peerIp, new Message(MessageType.PEER_DISCOVERY_RESPONSE, peersJson));
        } catch (IOException e) {
            System.err.println("Failed to handle peer discovery request: " + e.getMessage());
        }
    }

    // Handles the peer discovery response
    private void handlePeerDiscoveryResponse(Message message) {
        try {
            System.out.println("Received peer discovery response");

            // Deserialize the peers map from the response JSON
            Map<String, PeerInfo> receivedPeers = gson.fromJson(message.getData(), ConcurrentHashMap.class);

            // Log the deserialized map
            System.out.println("Deserialized peers map: " + receivedPeers);

            // Add any new peers to our list
            receivedPeers.forEach(peers::putIfAbsent);
        } catch (Exception e) {
            System.err.println("Failed to handle peer discovery response: " + e.getMessage());
        }
    }


    // Handles the SHARE_PEER_LIST message
    private void handleSharePeerList(Message message) {
        try {
            System.out.println("Received peer list from peer");
            Map<String, PeerInfo> receivedPeers = gson.fromJson(message.getData(), ConcurrentHashMap.class); // Deserialize the peers map
            // Add any new peers to our list
            receivedPeers.forEach(peers::putIfAbsent);
        } catch (Exception e) {
            System.err.println("Failed to handle peer list sharing: " + e.getMessage());
        }
    }

    public PublicKey getLocalPublicKey() {
        return localPublicKey;
    }

    public Map<String, PeerInfo> getPeers() {
        return peers;
    }
}