package networking;

import blockchain.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import ledger.Transaction;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import static blockchain.Main.*;
//import java.util.concurrent.BlockingQueue;
//import java.util.concurrent.LinkedBlockingQueue;

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
    private volatile boolean connected = true; // Ensure visibility across threads
    private boolean publicKeyExchanged = false; // Ensure public keys are exchanged
    //private final BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>(); // Blocking queue
    //private volatile boolean running = true;
    //private final Thread workerThread;
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
        //this.workerThread = new Thread(this::processMessages);
        //workerThread.start();
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
                    /*
                    try {
                        messageQueue.put(receivedMsg);  // Blocks until space is available
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();  // Restore the interrupt status
                        log("Thread interrupted while adding message to queue.");
                    }*/
                    handleNetworkMessage(receivedMsg);
                }
            }
        } catch (IOException e) {
            log("Failed to read message from " + peerIp + ": " + e.getMessage());
            handleDisconnection();
        }
    }

    private void handlePublicKeyExchange(Message message) {
        // Store peer's public key as a string
        String peerPublicKey = message.getData(); // Store peer's public key as a string
        publicKeyExchanged = true;
        storePeerInfo(peerPublicKey); // Store peer info in the network manager
        log("Public key exchanged with peer: " + peerIp);
        // Acknowledge the connection
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
            networkManager.updatePeerConnectionStatus(peerIp, true);
            log("Connection fully established with peer: " + peerIp);
        }
    }

    private void handleNewTransaction(Message receivedMsg) {
        log("Received NEW_TRANSACTION message.");
        try {
            Transaction transaction = gson.fromJson(receivedMsg.getData(), Transaction.class);
            if (blockchain.getReceivedTransactions().containsKey(transaction.transactionId)) {
                log("Transaction " + transaction.transactionId + " already processed. Ignoring...");
                return;
            }
            blockchain.handleNewTransaction(transaction, peerIp, networkManager, forkResolution);
        } catch (Exception e) {
            log("Error deserializing transaction: " + e.getMessage());
        }
    }

    private void handleNewBlock(Message receivedMsg) {
        Block receivedBlock = gson.fromJson(receivedMsg.getData(), Block.class);
        // Filter transaction sender and recipient strings after deserialization
        for (Transaction transaction : receivedBlock.getTransactions()) {
            transaction.sender = transaction.sender.replace("\\u003d", "=");
            transaction.recipient = transaction.recipient.replace("\\u003d", "=");
        }
        if (blockchain.getReceivedBlockHashes().contains(receivedBlock.getHash())) {
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
        //Blockchain receivedBlockchain = gson.fromJson(receivedMsg.getData(), Blockchain.class);
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
    /*
    private void processMessages() {
        while (running && connected) {
            try {
                Message message = messageQueue.take(); // This blocks until a message is available
                handleNetworkMessage(message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void handleDisconnection() {
        connected = false;
        running = false;
        log("Handling disconnection from " + peerIp);
        networkManager.updatePeerConnectionStatus(peerIp, false);
        try {
            socket.close();
        } catch (IOException e) {
            log("Failed to close socket: " + e.getMessage());
        }
        try {
            workerThread.join(); // Wait for the worker thread to finish
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore the interrupt status
            //In case the thread is interrupted during shutdown, it's good to set the interrupt status again
            log("Worker thread interrupted during shutdown: " + e.getMessage());
        }

    }*/
}