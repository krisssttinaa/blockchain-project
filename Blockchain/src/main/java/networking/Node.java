package networking;

import blockchain.Block;
import blockchain.Blockchain;
import blockchain.Transaction;
import com.google.gson.Gson;
import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Node implements Runnable {
    private final Socket socket;
    private final Blockchain blockchain;
    private final NetworkManager networkManager;
    private BufferedReader input;
    private PrintWriter output;

    public Node(Socket socket, Blockchain blockchain, NetworkManager networkManager) {
        this.socket = socket;
        this.blockchain = blockchain;
        this.networkManager = networkManager;
        try {
            this.input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.output = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        try {
            String receivedMessage;
            while ((receivedMessage = input.readLine()) != null) {
                handleReceivedMessage(receivedMessage);
            }
        } catch (IOException e) {
            System.err.println("Failed to read message: " + e.getMessage());
        }
    }

    private void handleReceivedMessage(String messageJson) {
        Gson gson = new Gson();
        Message receivedMsg = gson.fromJson(messageJson, Message.class);

        switch (receivedMsg.getType()) {
            case NEW_TRANSACTION -> handleNewTransaction(receivedMsg);
            case NEW_BLOCK -> handleNewBlock(receivedMsg);
            case BLOCKCHAIN_REQUEST -> handleBlockchainRequest();
            case SYNC_REQUEST -> handleSyncRequest();
            case SYNC_RESPONSE -> handleSyncResponse(receivedMsg);
            case PEER_DISCOVERY_REQUEST -> handlePeerDiscoveryRequest();
            case PEER_DISCOVERY_RESPONSE -> handlePeerDiscoveryResponse(receivedMsg);
            default -> System.out.println("Unknown message type received");
        }
    }

    private void handleNewTransaction(Message receivedMsg) {
        Gson gson = new Gson();
        Transaction transaction = gson.fromJson(receivedMsg.getData(), Transaction.class);
        if (blockchain.addTransaction(transaction)) {
            networkManager.broadcastMessage(new Message(MessageType.NEW_TRANSACTION, gson.toJson(transaction)));
        }
    }

    private void handleNewBlock(Message receivedMsg) {
        Gson gson = new Gson();
        Block block = gson.fromJson(receivedMsg.getData(), Block.class);
        if (blockchain.addAndValidateBlock(block)) {
            networkManager.broadcastMessage(new Message(MessageType.NEW_BLOCK, gson.toJson(block)));
        }
    }

    private void handleBlockchainRequest() {
        Gson gson = new Gson();
        String blockchainJson = gson.toJson(blockchain);
        sendMessage(new Message(MessageType.BLOCKCHAIN_RESPONSE, blockchainJson));
    }

    private void handleSyncRequest() {
        Gson gson = new Gson();
        String syncBlockchainJson = gson.toJson(blockchain);
        sendMessage(new Message(MessageType.SYNC_RESPONSE, syncBlockchainJson));
    }

    private void handleSyncResponse(Message receivedMsg) {
        Gson gson = new Gson();
        Blockchain receivedBlockchain = gson.fromJson(receivedMsg.getData(), Blockchain.class);
        blockchain.compareAndReplace(receivedBlockchain); // Replace blockchain if the received one is more valid
    }

    private void handlePeerDiscoveryRequest() {
        Gson gson = new Gson();
        String peersJson = gson.toJson(networkManager.getPeers()); // Serialize the peers map to JSON
        sendMessage(new Message(MessageType.PEER_DISCOVERY_RESPONSE, peersJson));
    }

    private void handlePeerDiscoveryResponse(Message receivedMsg) {
        Gson gson = new Gson();
        Map<String, PeerInfo> receivedPeers = gson.fromJson(receivedMsg.getData(), ConcurrentHashMap.class); // Deserialize the peers map
        receivedPeers.forEach((publicKey, peerInfo) -> {
            networkManager.getPeers().putIfAbsent(publicKey, peerInfo); // Add any new peers to our list
        });
    }

    public String getIp() {
        return socket.getInetAddress().getHostAddress();
    }

    public void sendMessage(Message message) {
        Gson gson = new Gson();
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