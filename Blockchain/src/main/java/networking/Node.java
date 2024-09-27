package networking;

import blockchain.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import static blockchain.Main.NODE_PORT;
import static blockchain.Main.unconfirmedTransactions;
import static blockchain.Blockchain.receivedBlockHashes;

public class Node implements Runnable{
    private static final AtomicInteger idCounter = new AtomicInteger(0); // Unique ID generator for nodes
    private final int nodeId;
    private final Socket socket;
    private final Blockchain blockchain;
    private final ForkResolution forkResolution; // Added ForkResolution reference
    private final NetworkManager networkManager;
    private BufferedReader input;
    private PrintWriter output;
    private final Gson gson = new Gson(); // Use standard Gson since we're using Strings for public keys
    private final String peerIp;
    private String peerPublicKey; // Store peer's public key as a string
    private boolean connected = true; // Track connection status
    private boolean publicKeyExchanged = false; // Ensure public keys are exchanged
    public Node(Socket socket, Blockchain blockchain, NetworkManager networkManager, ForkResolution forkResolution) {
        this.nodeId = idCounter.incrementAndGet();
        this.socket = socket;
        this.blockchain = blockchain;
        this.forkResolution = forkResolution; // Save ForkResolution instance for block processing
        this.networkManager = networkManager;
        this.peerIp = socket.getInetAddress().getHostAddress();
        try {
            this.input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.output = new PrintWriter(socket.getOutputStream(), true);
            String localPublicKeyString = networkManager.getLocalPublicKey(); // Send our public key (a string) first
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
            while (connected && (receivedMessage = input.readLine()) != null) {
                log("Received message: " + receivedMessage);
                Message receivedMsg = gson.fromJson(receivedMessage, Message.class);
                if (!publicKeyExchanged) {
                    if (receivedMsg.getType() == MessageType.PUBLIC_KEY_EXCHANGE) {
                        handlePublicKeyExchange(receivedMsg);
                    } else {
                        log("Public key not exchanged yet with " + peerIp + ". Ignoring message of type: " + receivedMsg.getType());
                    }
                } else {
                    handleNetworkMessage(receivedMsg);
                }
            }
        } catch (IOException e) {
            log("Failed to read message from " + peerIp + ": " + e.getMessage());
            //handleDisconnection();
        }
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
            networkManager.updatePeerConnectionStatus(peerIp, true);
            log("Connection fully established with peer: " + peerIp);
        }
    }

    private void handlePublicKeyExchange(Message message) {
        this.peerPublicKey = message.getData(); // Store peer's public key as a string
        publicKeyExchanged = true;
        storePeerInfo(peerPublicKey); // Store peer info in the network manager
        log("Public key exchanged with peer: " + peerIp);

        // Acknowledge the connection
        sendMessage(new Message(MessageType.CONNECTION_ESTABLISHED, "Connection established with peer: " + peerIp));
    }

    private void handleSharePeerList(Message receivedMsg) {
        log("Received gossip from peer: " + peerIp);
        ConcurrentHashMap<String, PeerInfo> receivedPeers = gson.fromJson(receivedMsg.getData(), new TypeToken<ConcurrentHashMap<String, PeerInfo>>(){}.getType());

        String localPublicKeyString = networkManager.getLocalPublicKey();

        receivedPeers.forEach((publicKey, peerInfo) -> {
            if (!publicKey.equals(localPublicKeyString)) {
                networkManager.getPeers().compute(publicKey, (key, existingPeerInfo) -> {
                    if (existingPeerInfo == null) {
                        log("New peer added (disconnected): " + publicKey + " with IP: " + peerInfo.getIpAddress());
                        networkManager.connectToPeer(peerInfo.getIpAddress(), NODE_PORT);
                        return new PeerInfo(peerInfo.getIpAddress(), false);
                    } else if (!existingPeerInfo.isConnected() && peerInfo.isConnected()) {
                        log("Updating peer info with better connection state: " + publicKey);
                        networkManager.connectToPeer(peerInfo.getIpAddress(), NODE_PORT);
                        return new PeerInfo(peerInfo.getIpAddress(), true);
                    }
                    return existingPeerInfo;
                });
            }
        });
        log("Updated peer list after receiving gossip.");
    }

    private void handleNewTransaction(Message receivedMsg) {
        log("Received NEW_TRANSACTION message.");
        try {
            Transaction transaction = gson.fromJson(receivedMsg.getData(), Transaction.class);
            if (Main.receivedTransactions.containsKey(transaction.transactionId)) {
                log("Transaction " + transaction.transactionId + " already processed. Ignoring...");
                return;
            }
            log("Transaction deserialized successfully: " + transaction);

            // Process the transaction and add it to the unconfirmed pool
            if (blockchain.addTransaction(transaction)) {
                log("Transaction validated and added to pool.");

                // Step 1: Broadcast the transaction to other peers
                networkManager.broadcastMessageExceptSender(receivedMsg, peerIp);
                log("Transaction broadcast to peers.");
                // Step 2: Check if we need to mine
                if (unconfirmedTransactions.size() >= Main.numTransactionsToMine) {
                    log("Mining 2 pending transactions...");
                    Block minedBlock = blockchain.minePendingTransactions(Main.numTransactionsToMine, forkResolution);
                    if (minedBlock != null) {
                        log("Block mined successfully: " + minedBlock.getHash());
                        // Step 3: Broadcast the mined block
                        networkManager.broadcastMessageExceptSender(new Message(MessageType.NEW_BLOCK, gson.toJson(minedBlock)), peerIp);
                    } else {
                        log("Mining failed.");
                    }
                }
                else {
                    log("Not enough transactions to mine yet.");
                }
            } else {
                log("Transaction validation failed for transaction from " + peerIp + ".");
            }
        } catch (Exception e) {
            log("Error deserializing transaction: " + e.getMessage());
        }
    }

    // Instead of directly adding the block to the blockchain, we use ForkResolution
    private void handleNewBlock(Message receivedMsg) {
        Block receivedBlock = gson.fromJson(receivedMsg.getData(), Block.class);
        if (receivedBlockHashes.contains(receivedBlock.getHash())) {
                System.out.println("Block already received: " + receivedBlock.getHash());
                return;
            }
            // Add the block to ForkResolution for processing, rather than adding it directly to the blockchain
            forkResolution.addBlock(receivedBlock);
            log("Block forwarded to ForkResolution for further processing.");
            networkManager.broadcastMessageExceptSender(receivedMsg, peerIp); // Broadcast to others except sender
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
        //blockchain.compareAndReplace(receivedBlockchain);
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
                peerInfo = new PeerInfo(peerIp, socket, true);
                networkManager.getPeers().put(incomingPublicKeyString, peerInfo);
                log("Stored new peer info: " + incomingPublicKeyString + " with IP: " + peerIp);
            } else {
                peerInfo.setSocket(socket);
                peerInfo.setConnected(true);
                log("Updated existing peer info: " + incomingPublicKeyString + " with new socket.");
            }
        }
    }

    private void handleDisconnection() {
        connected = false;
        log("Handling disconnection from " + peerIp);
        networkManager.updatePeerConnectionStatus(peerIp, false);
        try {
            socket.close();
        } catch (IOException e) {
            log("Failed to close socket: " + e.getMessage());
        }
    }

    private void log(String message) {
        System.out.println("Node-" + nodeId + ": " + message);
    }
}