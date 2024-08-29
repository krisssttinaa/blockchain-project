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

import static blockchain.Main.NODE_PORT;

public class Node implements Runnable {
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

    public Node(Socket socket, Blockchain blockchain, NetworkManager networkManager) {
        this.socket = socket;
        this.blockchain = blockchain;
        this.networkManager = networkManager;
        this.peerIp = socket.getInetAddress().getHostAddress();

        try {
            this.input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.output = new PrintWriter(socket.getOutputStream(), true);

            // Send our public key first
            String localPublicKeyString = StringUtil.getStringFromKey(networkManager.getLocalPublicKey());
            sendMessage(new Message(MessageType.PUBLIC_KEY_EXCHANGE, localPublicKeyString));

        } catch (IOException e) {
            connected = false;
            System.err.println("Failed to establish connection with " + peerIp + ": " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            String receivedMessage;
            while (connected && (receivedMessage = input.readLine()) != null) {
                Message receivedMsg = gson.fromJson(receivedMessage, Message.class);
                if (!publicKeyExchanged) {
                    if (receivedMsg.getType() == MessageType.PUBLIC_KEY_EXCHANGE) {
                        handlePublicKeyExchange(receivedMsg);
                    } else {
                        System.out.println("Public key not exchanged yet with " + peerIp + ". Ignoring message of type: " + receivedMsg.getType());
                    }
                } else {
                    handleNetworkMessage(receivedMsg);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to read message from " + peerIp + ": " + e.getMessage());
            connected = false;
            // Handle disconnection
            //removePeerInfo();
        }
    }

    private void handlePublicKeyExchange(Message message) {
        String peerPublicKeyString = message.getData();
        this.peerPublicKey = StringUtil.getKeyFromString(peerPublicKeyString);
        publicKeyExchanged = true;
        storePeerInfo(peerPublicKeyString);
        System.out.println("Public key exchanged with peer: " + peerIp);

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
            default -> System.out.println("Unknown message type received from " + peerIp + ": " + message.getType());
        }
    }

    private void handleConnectionEstablished() {
        synchronized (networkManager.getPeers()) {
            System.out.println("Handling connection established with peer: " + peerIp);

            // Check status before updating
            System.out.println("Before updating, isConnected status for peer " + peerIp + ": " + isPeerConnected());

            // Update connection status
            networkManager.updatePeerConnectionStatus(peerIp, true);

            // Check status after updating
            System.out.println("After updating, isConnected status for peer " + peerIp + ": " + isPeerConnected());

            System.out.println("Connection fully established with peer: " + peerIp);
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
        System.out.println("Received gossip from peer: " + peerIp);
        ConcurrentHashMap<String, PeerInfo> receivedPeers = gson.fromJson(receivedMsg.getData(), new TypeToken<ConcurrentHashMap<String, PeerInfo>>(){}.getType());

        String localPublicKeyString = StringUtil.getStringFromKey(networkManager.getLocalPublicKey());

        receivedPeers.forEach((publicKey, peerInfo) -> {
            // Avoid adding self or existing peers
            if (!publicKey.equals(localPublicKeyString) && !networkManager.getPeers().containsKey(publicKey)) {
                // Add the peer with isConnected set to false
                networkManager.getPeers().put(publicKey, new PeerInfo(peerInfo.getIpAddress(), false));
                System.out.println("New peer added (disconnected): " + publicKey + " with IP: " + peerInfo.getIpAddress());

                // Try to connect to the newly discovered peer
                networkManager.connectToPeer(peerInfo.getIpAddress(), NODE_PORT);
            }
        });

        System.out.println("Updated peer list after receiving gossip.");
    }

    private void handleNewTransaction(Message receivedMsg) {
        Transaction transaction = gson.fromJson(receivedMsg.getData(), Transaction.class);
        if (blockchain.addTransaction(transaction)) {
            System.out.println("Transaction added to local pool and re-broadcasted.");
            networkManager.broadcastMessage(new Message(MessageType.NEW_TRANSACTION, gson.toJson(transaction)));
            System.out.println("Mining new block with the received transaction...");
            blockchain.createAndAddBlock();
            Block latestBlock = blockchain.getLatestBlock();
            networkManager.broadcastMessage(new Message(MessageType.NEW_BLOCK, gson.toJson(latestBlock)));
        } else {
            System.out.println("Transaction from " + peerIp + " failed validation and was not added.");
        }
    }

    private void handleNewBlock(Message receivedMsg) {
        Block block = gson.fromJson(receivedMsg.getData(), Block.class);
        if (blockchain.addAndValidateBlock(block)) {
            System.out.println("Block validated and added to the chain, broadcasting...");
            networkManager.broadcastMessage(new Message(MessageType.NEW_BLOCK, gson.toJson(block)));
        } else {
            System.out.println("Block validation failed for block from " + peerIp + ".");
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
                System.out.println("Added new peer from discovery: " + peerInfo.getIpAddress());
            }
        });
    }

    private void sendMessage(Message message) {
        String messageJson = gson.toJson(message);
        output.println(messageJson);
    }

    private void storePeerInfo(String incomingPublicKeyString) {
        PeerInfo peerInfo = new PeerInfo(peerIp, socket, true); // Initialize with the socket and connected status
        networkManager.getPeers().putIfAbsent(incomingPublicKeyString, peerInfo);
        System.out.println("Stored peer info: " + incomingPublicKeyString + " with IP: " + peerIp);
    }

    private void removePeerInfo() {
        networkManager.getPeers().remove(StringUtil.getStringFromKey(peerPublicKey));
        System.out.println("Removed peer info: " + peerIp + " due to disconnection.");
    }

}