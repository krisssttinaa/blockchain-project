package networking;

import blockchain.Block;
import blockchain.Blockchain;
import blockchain.StringUtil;
import blockchain.Transaction;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.PublicKey;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import static blockchain.Main.NODE_PORT;

public class Node implements Runnable {
    // Static counter to generate unique IDs for each Node instance
    private static final AtomicInteger idCounter = new AtomicInteger(0);
    private final int nodeId;
    private final Socket socket;
    private final Blockchain blockchain;
    private final NetworkManager networkManager;
    private BufferedReader input;
    private PrintWriter output;
    private final Gson gson = new Gson();
    private final String peerIp;
    private PublicKey peerPublicKey;
    private boolean connected = true; // To track the connection status
    private boolean publicKeyExchanged = false; // To ensure public keys are exchanged before proceeding
    private int numTranasctionsToMine = 2; // Number of transactions to mine a block

    public Node(Socket socket, Blockchain blockchain, NetworkManager networkManager) {
        // Assign a unique ID to each Node instance
        this.nodeId = idCounter.incrementAndGet();

        this.socket = socket;
        this.blockchain = blockchain;
        this.networkManager = networkManager;
        this.peerIp = socket.getInetAddress().getHostAddress();

        try {
            this.input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.output = new PrintWriter(socket.getOutputStream(), true);

            // Send our public key first
            String localPublicKeyString = StringUtil.getStringFromKey(networkManager.getLocalPublicKey());
            log("Sending public key: " + localPublicKeyString);
            sendMessage(new Message(MessageType.PUBLIC_KEY_EXCHANGE, localPublicKeyString));

        } catch (IOException e) {
            connected = false;
            log("Failed to establish connection with " + peerIp + ": " + e.getMessage());
        }
    }

    @Override
    public void run() {
        log("Node thread started.");
        try {
            String receivedMessage;
            // Continuous listening without timeout
            while (connected && (receivedMessage = input.readLine()) != null) {
                log("Received message: " + receivedMessage);
                Message receivedMsg = gson.fromJson(receivedMessage, Message.class);
                if (!publicKeyExchanged) {
                    // Handle public key exchange before anything else
                    if (receivedMsg.getType() == MessageType.PUBLIC_KEY_EXCHANGE) {
                        handlePublicKeyExchange(receivedMsg);
                    } else {
                        log("Public key not exchanged yet with " + peerIp + ". Ignoring message of type: " + receivedMsg.getType());
                    }
                } else {
                    // Handle messages after public key exchange
                    handleNetworkMessage(receivedMsg);
                }
            }
        } catch (IOException e) {
            // If an IOException occurs, mark the peer as disconnected
            //log("Failed to read message from " + peerIp + ": " + e.getMessage());
            //handleDisconnection();
        }
    }

    private void handlePublicKeyExchange(Message message) {
        String peerPublicKeyString = message.getData();
        this.peerPublicKey = StringUtil.getKeyFromString(peerPublicKeyString);
        publicKeyExchanged = true;
        storePeerInfo(peerPublicKeyString);
        log("Public key exchanged with peer: " + peerIp);

        // Send back an acknowledgment or a message indicating the connection is fully established
        sendMessage(new Message(MessageType.CONNECTION_ESTABLISHED, "Connection established with peer: " + peerIp));
    }

    private void handleNetworkMessage(Message message) {
        switch (message.getType()) {
            case CONNECTION_ESTABLISHED -> handleConnectionEstablished();
            case NEW_TRANSACTION -> handleNewTransaction(message);
            case NEW_BLOCK -> handleNewBlock(message);
            case BLOCKCHAIN_REQUEST -> handleBlockchainRequest();
            case SYNC_REQUEST -> handleSyncRequest();
            case SYNC_RESPONSE -> handleSyncResponse(message);
            case PEER_DISCOVERY_REQUEST -> handlePeerDiscoveryRequest();
            case PEER_DISCOVERY_RESPONSE -> handlePeerDiscoveryResponse(message);
            case SHARE_PEER_LIST -> handleSharePeerList(message);
            default -> log("Unknown message type received from " + peerIp + ": " + message.getType());
        }
    }

    private void handleConnectionEstablished() {
        synchronized (networkManager.getPeers()) {
            log("Handling connection established with peer: " + peerIp);
            log("Before updating, isConnected status for peer " + peerIp + ": " + isPeerConnected());
            // Update connection status
            networkManager.updatePeerConnectionStatus(peerIp, true);
            log("After updating, isConnected status for peer " + peerIp + ": " + isPeerConnected());
            log("Connection fully established with peer: " + peerIp);
        }
    }

    private boolean isPeerConnected() {
        return networkManager.getPeers().values().stream()
                .filter(peerInfo -> peerInfo.getIpAddress().equals(peerIp))
                .map(PeerInfo::isConnected)
                .findFirst()
                .orElse(false);
    }

    private void handleSharePeerList(Message receivedMsg) {
        log("Received gossip from peer: " + peerIp);
        ConcurrentHashMap<String, PeerInfo> receivedPeers = gson.fromJson(receivedMsg.getData(), new TypeToken<ConcurrentHashMap<String, PeerInfo>>(){}.getType());

        String localPublicKeyString = StringUtil.getStringFromKey(networkManager.getLocalPublicKey());

        receivedPeers.forEach((publicKey, peerInfo) -> {
            // Avoid adding self or existing peers unless the new info is more reliable
            if (!publicKey.equals(localPublicKeyString)) {
                networkManager.getPeers().compute(publicKey, (key, existingPeerInfo) -> {
                    if (existingPeerInfo == null) {
                        log("New peer added (disconnected): " + publicKey + " with IP: " + peerInfo.getIpAddress());
                        // Try to connect to the newly discovered peer
                        networkManager.connectToPeer(peerInfo.getIpAddress(), NODE_PORT);
                        return new PeerInfo(peerInfo.getIpAddress(), false);
                    } else {
                        // Keep the existing peer if it's already connected or if the current peer info is more accurate
                        if (!existingPeerInfo.isConnected() && peerInfo.isConnected()) {
                            log("Updating peer info with better connection state: " + publicKey);
                            networkManager.connectToPeer(peerInfo.getIpAddress(), NODE_PORT);
                            return new PeerInfo(peerInfo.getIpAddress(), true);
                        }
                        return existingPeerInfo;
                    }
                });
            }
        });
        log("Updated peer list after receiving gossip.");
    }

    private void handleNewTransaction(Message receivedMsg) {
        Transaction transaction = gson.fromJson(receivedMsg.getData(), Transaction.class); // Deserialize the received transaction
        // Add the transaction to the blockchain's unconfirmed transaction pool if it's valid
        if (blockchain.addTransaction(transaction)) {
            log("Transaction validated and added to pool. Re-broadcasting...");
            networkManager.broadcastMessage(receivedMsg);// Re-broadcast the transaction to other peers

            // Mine pending transactions if there are enough to create a block
            Block newBlock = blockchain.minePendingTransactions(numTranasctionsToMine);
            if (newBlock != null) {
                log("New block mined successfully.");
                log("Broadcasting newly mined block...");
                networkManager.broadcastNewBlock(newBlock);
            }
        } else {
            log("Transaction validation failed for transaction from " + peerIp + ".");
        }
    }

    private void handleNewBlock(Message receivedMsg) {
        // Deserialize the received block from the message
        Block receivedBlock = gson.fromJson(receivedMsg.getData(), Block.class);

        // Attempt to validate and add the block to the chain
        if (blockchain.addAndValidateBlock(receivedBlock)) {
            log("Block validated and added to the chain.");

            // Broadcast the block to other peers, excluding the sender
            networkManager.broadcastMessageExceptSender(receivedMsg, peerIp); // Avoid broadcasting to the sender again
        } else {
            log("Block validation failed for block from " + peerIp + ".");
        }
    }

    private void handleBlockchainRequest() {
        String blockchainJson = gson.toJson(blockchain);
        sendMessage(new Message(MessageType.BLOCKCHAIN_RESPONSE, blockchainJson));
    }

    private void handleSyncRequest() {
        String syncBlockchainJson = gson.toJson(blockchain);
        sendMessage(new Message(MessageType.SYNC_RESPONSE, syncBlockchainJson));
    }

    private void handleSyncResponse(Message receivedMsg) {
        Blockchain receivedBlockchain = gson.fromJson(receivedMsg.getData(), Blockchain.class);
        blockchain.compareAndReplace(receivedBlockchain);
    }

    private void handlePeerDiscoveryRequest() {
        String peersJson = gson.toJson(networkManager.getPeers());
        sendMessage(new Message(MessageType.PEER_DISCOVERY_RESPONSE, peersJson));
    }

    private void handlePeerDiscoveryResponse(Message receivedMsg) {
        ConcurrentHashMap<String, PeerInfo> receivedPeers = gson.fromJson(receivedMsg.getData(), ConcurrentHashMap.class);
        receivedPeers.forEach((publicKey, peerInfo) -> {
            if (!networkManager.getPeers().containsKey(publicKey)) {
                networkManager.getPeers().put(publicKey, new PeerInfo(peerInfo.getIpAddress(), false));
                log("Added new peer from discovery: " + peerInfo.getIpAddress());
            }
        });
    }

    // Update sendMessage to ensure messages are sent properly
    private void sendMessage(Message message) {
        if (socket != null && !socket.isClosed() && output != null) {
            String messageJson = gson.toJson(message);
            output.println(messageJson);
        } else {
            System.err.println("Socket is closed, can't send message to " + peerIp);
        }
    }

    private void storePeerInfo(String incomingPublicKeyString) {
        synchronized (networkManager.getPeers()) {
            PeerInfo peerInfo = networkManager.getPeers().get(incomingPublicKeyString);

            if (peerInfo == null) {
                // Create new PeerInfo if it doesn't exist
                peerInfo = new PeerInfo(peerIp, socket, true);
                networkManager.getPeers().put(incomingPublicKeyString, peerInfo);
                log("Stored new peer info: " + incomingPublicKeyString + " with IP: " + peerIp);
            } else {
                // If PeerInfo exists, just update the socket and connection status
                peerInfo.setSocket(socket);
                peerInfo.setConnected(true);
                log("Updated existing peer info: " + incomingPublicKeyString + " with new socket.");
            }
        }
    }

    // Log with the node ID for easier tracking
    private void log(String message) {
        System.out.println("Node-" + nodeId + ": " + message);
    }
    private void removePeerInfo() {
        networkManager.getPeers().remove(StringUtil.getStringFromKey(peerPublicKey));
        log("Removed peer info: " + peerIp + " due to disconnection.");
    }
    // Method to mark the peer as disconnected without removing it
    private void markPeerAsDisconnected() {
        connected = false;
        log("Marking peer as disconnected: " + peerIp);

        // Update PeerInfo to reflect disconnection
        networkManager.updatePeerConnectionStatus(peerIp, false);  // Mark as disconnected
        networkManager.updatePeerSocket(peerIp, null);             // Remove the socket reference

        // Optionally notify the peer before closing the socket, if feasible
        try {
            sendMessage(new Message(MessageType.DISCONNECT, "Disconnecting from peer: " + peerIp));
            socket.close(); // Close the socket
        } catch (IOException e) {
            log("Failed to close socket gracefully: " + e.getMessage());
        }
    }
}