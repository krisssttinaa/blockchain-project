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
import java.net.Socket;
import java.security.PublicKey;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

public class Node implements Runnable {
    private final Socket socket;
    private final Blockchain blockchain;
    private final NetworkManager networkManager;
    private BufferedReader input;
    private PrintWriter output;

    // Queue to buffer messages
    private final Queue<Message> messageQueue = new LinkedList<>();
    private boolean keyExchangeComplete = false; // Track key exchange state
    private final Gson gson = new Gson();
    private PublicKey peerPublicKey;

    public Node(Socket socket, Blockchain blockchain, NetworkManager networkManager) {
        this.socket = socket;
        this.blockchain = blockchain;
        this.networkManager = networkManager;
        try {
            this.input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.output = new PrintWriter(socket.getOutputStream(), true);

            // Immediately send public key upon establishing connection
            sendPublicKey();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        try {
            String receivedMessage;
            while ((receivedMessage = input.readLine()) != null) {
                Message receivedMsg = gson.fromJson(receivedMessage, Message.class);

                // Always handle the key exchange message first
                if (!keyExchangeComplete && receivedMsg.getType() == MessageType.PUBLIC_KEY_EXCHANGE) {
                    handlePublicKeyExchange(receivedMsg);
                    keyExchangeComplete = true; // Set the flag to true after successful key exchange
                    processMessageQueue(); // Now that the key exchange is complete, process the queue
                } else {
                    // If not yet completed, buffer the messages
                    if (!keyExchangeComplete) {
                        addMessageToQueueIfUnique(receivedMsg);
                    } else {
                        // Process immediately if key exchange is done
                        processMessage(receivedMsg);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to read message: " + e.getMessage());
        }
    }

    // Method to send the local public key immediately upon connection
    private void sendPublicKey() {
        String publicKeyString = StringUtil.getStringFromKey(networkManager.getLocalPublicKey());
        Message publicKeyMessage = new Message(MessageType.PUBLIC_KEY_EXCHANGE, publicKeyString);
        sendMessage(publicKeyMessage);
        System.out.println("Sent Public Key: " + publicKeyString);
    }

    // Method to handle public key exchange
    private void handlePublicKeyExchange(Message receivedMsg) {
        String receivedKey = receivedMsg.getData();
        System.out.println("Public key exchange message received: " + receivedKey);

        // Convert the received public key string to a PublicKey object
        peerPublicKey = StringUtil.getKeyFromString(receivedKey);

        // Store the connected peer's information
        String peerIp = socket.getInetAddress().getHostAddress();
        networkManager.getPeers().put(receivedKey, new PeerInfo(peerIp));

        // Send a peer discovery request to the newly connected peer
        sendMessage(new Message(MessageType.PEER_DISCOVERY_REQUEST, ""));
    }

    // Method to check for duplicates before adding to the queue
    private void addMessageToQueueIfUnique(Message message) {
        boolean isDuplicate = messageQueue.stream().anyMatch(
                queuedMessage -> queuedMessage.getType() == message.getType() &&
                        queuedMessage.getData().equals(message.getData())
        );

        if (!isDuplicate) {
            messageQueue.offer(message);
        } else {
            System.out.println("Duplicate message detected, not adding to queue: " + message);
        }
    }

    private void processMessageQueue() {
        while (!messageQueue.isEmpty()) {
            Message message = messageQueue.poll(); // Get the next message in the queue
            processMessage(message); // Process the message
        }
    }

    private void processMessage(Message receivedMsg) {
        switch (receivedMsg.getType()) {
            case NEW_TRANSACTION -> handleNewTransaction(receivedMsg);
            case NEW_BLOCK -> handleNewBlock(receivedMsg);
            case BLOCKCHAIN_REQUEST -> handleBlockchainRequest();
            case SYNC_REQUEST -> handleSyncRequest();
            case SYNC_RESPONSE -> handleSyncResponse(receivedMsg);
            case PEER_DISCOVERY_REQUEST -> handlePeerDiscoveryRequest();
            case PEER_DISCOVERY_RESPONSE -> handlePeerDiscoveryResponse(receivedMsg);
            default -> System.out.println("Unknown message type received: " + receivedMsg.getType());
        }
    }

    private void handleNewTransaction(Message receivedMsg) {
        Transaction transaction = gson.fromJson(receivedMsg.getData(), Transaction.class);
        if (blockchain.addTransaction(transaction)) {
            networkManager.broadcastMessage(new Message(MessageType.NEW_TRANSACTION, gson.toJson(transaction)));
        }
    }

    private void handleNewBlock(Message receivedMsg) {
        Block block = gson.fromJson(receivedMsg.getData(), Block.class);
        if (blockchain.addAndValidateBlock(block)) {
            networkManager.broadcastMessage(new Message(MessageType.NEW_BLOCK, gson.toJson(block)));
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
        blockchain.compareAndReplace(receivedBlockchain); // Replace blockchain if the received one is more valid
    }

    private void handlePeerDiscoveryRequest() {
        String peersJson = gson.toJson(networkManager.getPeers()); // Serialize the peers map to JSON
        sendMessage(new Message(MessageType.PEER_DISCOVERY_RESPONSE, peersJson));
    }

    private void handlePeerDiscoveryResponse(Message receivedMsg) {
        Map<String, PeerInfo> receivedPeers = gson.fromJson(receivedMsg.getData(), ConcurrentHashMap.class); // Deserialize the peers map
        receivedPeers.forEach((publicKey, peerInfo) -> {
            networkManager.getPeers().putIfAbsent(publicKey, peerInfo); // Add any new peers to our list
        });
    }

    public String getIp() {
        return socket.getInetAddress().getHostAddress();
    }

    public void sendMessage(Message message) {
        String messageJson = gson.toJson(message);
        output.println(messageJson);
    }

    public void closeConnection() {
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method to request the blockchain from a connected peer
    public void requestBlockchain() {
        sendMessage(new Message(MessageType.BLOCKCHAIN_REQUEST, ""));
    }

    // Method to request a full blockchain for synchronization
    public void requestSync() {
        sendMessage(new Message(MessageType.SYNC_REQUEST, ""));
    }
}