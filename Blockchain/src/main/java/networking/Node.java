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
import java.util.concurrent.ConcurrentHashMap;

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
            System.err.println("Failed to establish connection: " + e.getMessage());
            //throw new RuntimeException(e);
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
                        System.out.println("Public key not exchanged yet. Ignoring message of type: " + receivedMsg.getType());
                    }
                } else {
                    handleNetworkMessage(receivedMsg);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to read message: " + e.getMessage());
            connected = false;
            //removePeerInfo();
        }
    }

    private void handlePublicKeyExchange(Message message) {
        String peerPublicKeyString = message.getData();
        this.peerPublicKey = StringUtil.getKeyFromString(peerPublicKeyString);
        publicKeyExchanged = true;
        storePeerInfo(peerPublicKeyString);
        System.out.println("Public key exchanged with peer: " + peerIp);
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
            case SHARE_PEER_LIST -> handleSharePeerList(message);
            default -> System.out.println("Unknown message type received: " + message.getType());
        }
    }

    private void handleSharePeerList(Message receivedMsg) {
        ConcurrentHashMap<String, PeerInfo> receivedPeers = gson.fromJson(receivedMsg.getData(), ConcurrentHashMap.class);
        receivedPeers.forEach((publicKey, peerInfo) -> {
            if (!networkManager.getPeers().containsKey(publicKey)) {
                // If we don't already have this peer, add it
                networkManager.getPeers().put(publicKey, new PeerInfo(peerInfo.getIpAddress()));
                System.out.println("Added new peer from gossip: " + peerInfo.getIpAddress());
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
                networkManager.getPeers().put(publicKey, new PeerInfo(peerInfo.getIpAddress()));
                System.out.println("Added new peer from discovery: " + peerInfo.getIpAddress());
            }
        });
    }

    private void sendMessage(Message message) {
        String messageJson = gson.toJson(message);
        output.println(messageJson);
    }

    private void storePeerInfo(String incomingPublicKeyString) {
        PeerInfo peerInfo = new PeerInfo(peerIp, socket);
        networkManager.getPeers().putIfAbsent(incomingPublicKeyString, peerInfo);
        System.out.println("Stored peer info: " + incomingPublicKeyString + " with IP: " + peerIp);
    }

    private void removePeerInfo() {
        networkManager.getPeers().remove(StringUtil.getStringFromKey(peerPublicKey));
        System.out.println("Removed peer info: " + peerIp + " due to disconnection.");
    }
}