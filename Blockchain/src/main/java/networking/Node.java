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
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;

public class Node implements Runnable {
    private final Socket socket;
    private final Blockchain blockchain;
    private final NetworkManager networkManager;
    private BufferedReader input;
    private PrintWriter output;
    private final Gson gson = new Gson();
    private String localNonce;
    private String local;
    private String peerNonce;
    private PublicKey peerPublicKey;
    private boolean handshakeComplete = false;
    private final boolean isSeedNode;

    public Node(Socket socket, Blockchain blockchain, NetworkManager networkManager, boolean isSeedNode) {
        this.socket = socket;
        this.blockchain = blockchain;
        this.networkManager = networkManager;
        this.isSeedNode = isSeedNode;

        try {
            this.input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.output = new PrintWriter(socket.getOutputStream(), true);

            // Only initiate handshake if this node is connecting to the seed node
            if (isSeedNode) {
                initiateHandshake();
            }

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
                processMessage(receivedMsg); // Handle both handshake and general messages
            }
        } catch (IOException e) {
            System.err.println("Failed to read message: " + e.getMessage());
        }
    }

    private void initiateHandshake() {
        // Generate a nonce for this session
        localNonce = generateNonce();
        String publicKeyString = StringUtil.getStringFromKey(networkManager.getLocalPublicKey());

        // Send handshake initiation with local nonce
        HandshakeMessage handshakeInit = new HandshakeMessage(publicKeyString, localNonce);
        sendMessage(new Message(MessageType.HANDSHAKE_INIT, gson.toJson(handshakeInit)));
        System.out.println("Handshake initiation sent with nonce: " + localNonce);
    }

    private void processMessage(Message message) {
        if (!handshakeComplete) {
            // Handle handshake messages
            switch (message.getType()) {
                case HANDSHAKE_INIT -> handleHandshakeInit(message);
                case HANDSHAKE_RESPONSE -> handleHandshakeResponse(message);
                case HANDSHAKE_FINAL -> handleHandshakeFinal(message);
                default -> System.out.println("Unexpected message type during handshake: " + message.getType());
            }
        } else {
            // If handshake is complete, process other types of messages
            handleNetworkMessage(message);
        }
    }

    private void handleHandshakeInit(Message message) {
        HandshakeMessage handshakeMessage = gson.fromJson(message.getData(), HandshakeMessage.class);
        String receivedNonce = handshakeMessage.getNonce();
        String incomingPublicKeyString = handshakeMessage.getPublicKey();

        System.out.println("Received handshake init with nonce: " + receivedNonce);

        local = generateNonce(); // Generate a new nonce if not already set
        HandshakeMessage handshakeResponse = new HandshakeMessage(StringUtil.getStringFromKey(networkManager.getLocalPublicKey()), receivedNonce, local);
        sendMessage(new Message(MessageType.HANDSHAKE_RESPONSE, gson.toJson(handshakeResponse)));
        System.out.println("Handshake response sent with peer's nonce: " + receivedNonce + " and new nonce: " + local);

        // Store peer's public key and nonce
        peerPublicKey = StringUtil.getKeyFromString(incomingPublicKeyString);
        peerNonce = receivedNonce;

        // Temporarily store the peer info (will finalize after handshake)
        storePeerInfo(incomingPublicKeyString);
    }

    private void handleHandshakeResponse(Message message) {
        HandshakeMessage handshakeMessage = gson.fromJson(message.getData(), HandshakeMessage.class);
        String receivedNonce = handshakeMessage.getNonce();
        String receivedPeerNonce = handshakeMessage.getNewNonce();
        String incomingPublicKeyString = handshakeMessage.getPublicKey();

        peerPublicKey = StringUtil.getKeyFromString(incomingPublicKeyString);

        System.out.println("Received handshake response with nonce: " + receivedNonce + ", expected: " + localNonce);

        // Verify the nonce
        if (!receivedNonce.equals(localNonce)) {
            System.out.println("Nonce verification failed. Handshake aborted.");
            removePeerInfo(incomingPublicKeyString);  // Remove peer if handshake fails
            return;
        }

        // Finalize the handshake
        peerNonce = receivedPeerNonce;
        HandshakeMessage handshakeFinal = new HandshakeMessage(peerNonce);
        sendMessage(new Message(MessageType.HANDSHAKE_FINAL, gson.toJson(handshakeFinal)));
        System.out.println("Final handshake message sent. Handshake complete.");
        handshakeComplete = true;

        // Store the peer info after successful handshake
        storePeerInfo(incomingPublicKeyString);
    }

    private void handleHandshakeFinal(Message message) {
        HandshakeMessage handshakeMessage = gson.fromJson(message.getData(), HandshakeMessage.class);
        String receivedNonce = handshakeMessage.getNonce();

        System.out.println("Received final handshake with nonce: " + receivedNonce + ", expected: " + local);

        // Verify the nonce
        if (!receivedNonce.equals(local)) {
            System.out.println("Final nonce verification failed. Handshake aborted.");
            removePeerInfo(StringUtil.getStringFromKey(peerPublicKey)); // Remove peer if final verification fails
            return;
        }

        System.out.println("Final nonce verified. Handshake complete.");
        handshakeComplete = true;
    }

    private void handleNetworkMessage(Message message) {
        switch (message.getType()) {
            case NEW_TRANSACTION -> handleNewTransaction(message);
            case NEW_BLOCK -> handleNewBlock(message);
            case BLOCKCHAIN_REQUEST -> handleBlockchainRequest();
            case SYNC_REQUEST -> handleSyncRequest();
            case SYNC_RESPONSE -> handleSyncResponse(message);
            case PEER_DISCOVERY_REQUEST -> handlePeerDiscoveryRequest();
            case PEER_DISCOVERY_RESPONSE -> handlePeerDiscoveryResponse(message);
            case SHARE_PEER_LIST -> broadcastUpdatedPeerList(); // Share peer list only when explicitly requested
            default -> System.out.println("Unknown message type received: " + message.getType());
        }
    }

    private void handleNewTransaction(Message receivedMsg) {
        Transaction transaction = gson.fromJson(receivedMsg.getData(), Transaction.class);
        if (blockchain.addTransaction(transaction)) {
            System.out.println("Transaction added to local pool and re-broadcasted.");
            // Propagate the transaction further to other peers
            networkManager.broadcastMessage(new Message(MessageType.NEW_TRANSACTION, gson.toJson(transaction)));

            // Trigger block mining if the transaction was successfully added
            System.out.println("Mining new block with the received transaction...");
            blockchain.createAndAddBlock();

            // After mining, broadcast the new block
            Block latestBlock = blockchain.getLatestBlock();
            networkManager.broadcastMessage(new Message(MessageType.NEW_BLOCK, gson.toJson(latestBlock)));
        } else {
            System.out.println("Transaction failed validation and was not added.");
        }
    }

    private void handleNewBlock(Message receivedMsg) {
        Block block = gson.fromJson(receivedMsg.getData(), Block.class);
        if (blockchain.addAndValidateBlock(block)) {
            System.out.println("Block validated and added to the chain, broadcasting...");
            networkManager.broadcastMessage(new Message(MessageType.NEW_BLOCK, gson.toJson(block)));
        } else {
            System.out.println("Block validation failed.");
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
        ConcurrentHashMap<String, PeerInfo> receivedPeers = gson.fromJson(receivedMsg.getData(), ConcurrentHashMap.class); // Deserialize the peers map
        receivedPeers.forEach((publicKey, peerInfo) -> {
            networkManager.getPeers().putIfAbsent(publicKey, peerInfo); // Add any new peers to our list
        });
    }

    private void broadcastUpdatedPeerList() {
        System.out.println("Broadcasting updated peer list...");
        String peersJson = gson.toJson(networkManager.getPeers());
        networkManager.broadcastMessage(new Message(MessageType.PEER_DISCOVERY_RESPONSE, peersJson));
    }

    private String generateNonce() {
        SecureRandom random = new SecureRandom();
        byte[] nonceBytes = new byte[8];
        random.nextBytes(nonceBytes);
        StringBuilder nonceBuilder = new StringBuilder();
        for (byte b : nonceBytes) {
            nonceBuilder.append(String.format("%02x", b));
        }
        return nonceBuilder.toString();
    }

    private void sendMessage(Message message) {
        String messageJson = gson.toJson(message);
        output.println(messageJson);
    }

    private void storePeerInfo(String incomingPublicKeyString) {
        String peerIp = socket.getInetAddress().getHostAddress();
        PeerInfo peerInfo = new PeerInfo(peerIp);
        networkManager.getPeers().putIfAbsent(incomingPublicKeyString, peerInfo);
        System.out.println("Stored peer info: " + incomingPublicKeyString + " with IP: " + peerIp);
    }

    private void removePeerInfo(String incomingPublicKeyString) {
        networkManager.getPeers().remove(incomingPublicKeyString);
        System.out.println("Removed peer info: " + incomingPublicKeyString + " due to handshake failure.");
    }
}