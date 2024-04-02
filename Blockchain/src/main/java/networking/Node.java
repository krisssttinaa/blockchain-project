package networking;

import blockchain.Block;
import blockchain.Blockchain;
import blockchain.Transaction;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;


public class Node implements NetworkNode {
    private HashMap<String, Socket> peerSockets = new HashMap<>(); int PORT = 7777;
    private Set<String> peers = new HashSet<>();
    private Blockchain blockchain;
    private ServerSocket serverSocket;
    public Node(int port) throws IOException {
        serverSocket = new ServerSocket(port);
    }

    public void start() {
        // Start a new thread to listen for incoming connections
        new Thread(() -> {
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    handleClient(clientSocket);  // Handle the client connection in a separate method/thread
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void handleClient(Socket clientSocket) {
        // Handle the client connection in a new thread or using a thread pool
        new Thread(() -> {
            try (InputStream in = clientSocket.getInputStream();
                 OutputStream out = clientSocket.getOutputStream()) {
                // Read data from the client, process it, and optionally write a response
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    // Method to broadcast data to all known peers
    public void broadcast(String data) {
        for (String peerAddress : peers) {
            try (Socket socket = new Socket(peerAddress, PORT)) {  // Assuming a common port for simplicity
                OutputStream out = socket.getOutputStream();
                out.write(data.getBytes());
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public Node(Blockchain blockchain) {
        this.blockchain = blockchain;
    }

    @Override
    public void connectToPeer(String peerAddress) {
        try {
            String[] addressComponents = peerAddress.split(":");
            String hostname = addressComponents[0];
            int port = Integer.parseInt(addressComponents[1]);
            Socket socket = new Socket(hostname, port);
            peers.add(peerAddress);
            peerSockets.put(peerAddress, socket);
            // Further logic to handle communication with the connected peer
        } catch (IOException e) {
            System.err.println("Error connecting to peer " + peerAddress + ": " + e.getMessage());
        }
    }

    @Override
    public void disconnectFromPeer(String peerAddress) {
        Socket socket = peerSockets.get(peerAddress);
        if (socket != null) {
            try {
                socket.close();
                peerSockets.remove(peerAddress);
                peers.remove(peerAddress);
            } catch (IOException e) {
                System.err.println("Error disconnecting from peer " + peerAddress + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void broadcastTransaction(Transaction transaction) {
        String transactionData = new Gson().toJson(transaction);
        broadcastMessage(transactionData);
    }

    @Override
    public void broadcastNewBlock(Block block) {
        String blockData = new Gson().toJson(block);
        broadcastMessage(blockData);
    }

    // Helper method to broadcast a message to all peers
    private void broadcastMessage(String message) {
        for (String peerAddress : peers) {
            try {
                Socket socket = peerSockets.get(peerAddress);
                if (socket != null && !socket.isClosed()) {
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    out.println(message);
                }
            } catch (IOException e) {
                System.err.println("Error broadcasting message to " + peerAddress + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void handleRequest(Message request) {
        switch (request.type) {
            case NEW_TRANSACTION:
                Transaction transaction = new Gson().fromJson(request.data, Transaction.class);
                blockchain.addTransaction(transaction);  // Assuming a method to add transactions to the pool
                //blockchain.createAndAddBlock();  //DO WE NEED THIS IDK
                break;
            case NEW_BLOCK:
                Block block = new Gson().fromJson(request.data, Block.class);
                if (blockchain.addAndValidateBlock(block)) {
                    System.out.println("New block validated and added to the blockchain.");
                } else {
                    //System.out.println("New block failed validation and was not added.");
                }
                break;
            case BLOCKCHAIN_REQUEST:
                sendBlockchainToPeer(request.data);  // Send the local blockchain to the requester
                break;
            case BLOCKCHAIN_RESPONSE:
                Blockchain receivedBlockchain = new Gson().fromJson(request.data, Blockchain.class);
                blockchain.compareAndReplace(receivedBlockchain);  // Compare and possibly replace the local blockchain
                break;
            case SHARE_PEER_LIST:
                Set<String> receivedPeers = new Gson().fromJson(request.data, new TypeToken<Set<String>>(){}.getType());
                peers.addAll(receivedPeers);  // Merge received peers with the local set
                break;
            default:
                System.out.println("Received an unknown message type.");
        }
    }

    @Override
    public void syncBlockchain() {
        for (String peerAddress : peers) {
            try {
                Socket socket = peerSockets.get(peerAddress);
                if (socket != null && !socket.isClosed()) {
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    // Send a request for the latest blockchain
                    Message blockchainRequest = new Message(MessageType.BLOCKCHAIN_REQUEST, "Requesting blockchain");
                    String requestJson = new Gson().toJson(blockchainRequest);
                    out.println(requestJson);
                }
            } catch (IOException e) {
                System.err.println("Error requesting blockchain from " + peerAddress + ": " + e.getMessage());
            }
        }
    }

    // Method to send the local blockchain to a peer in response to a request
    private void sendBlockchainToPeer(String peerAddress) {
        try {
            Socket socket = peerSockets.get(peerAddress);
            if (socket != null && !socket.isClosed()) {
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                String blockchainJson = new Gson().toJson(blockchain);
                Message blockchainResponse = new Message(MessageType.BLOCKCHAIN_RESPONSE, blockchainJson);
                String responseJson = new Gson().toJson(blockchainResponse);
                out.println(responseJson);
            }
        } catch (IOException e) {
            System.err.println("Error sending blockchain to " + peerAddress + ": " + e.getMessage());
        }
    }

    @Override
    public void sharePeersList() {
        String peersJson = new Gson().toJson(peers);
        for (String peerAddress : peers) {
            try {
                Socket socket = peerSockets.get(peerAddress);
                if (socket != null && !socket.isClosed()) {
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    Message sharePeersMessage = new Message(MessageType.SHARE_PEER_LIST, peersJson);
                    String messageJson = new Gson().toJson(sharePeersMessage);
                    out.println(messageJson);
                }
            } catch (IOException e) {
                System.err.println("Error sharing peer list with " + peerAddress + ": " + e.getMessage());
            }
        }
    }


    @Override
    public void validateBlock(Block block) {

    }

    @Override
    public void requestLatestBlock() {

    }

    public String getIp() {
        return serverSocket.getInetAddress().getHostAddress();
    }
}
