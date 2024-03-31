package networking;
import blockchain.Block;
import blockchain.Blockchain;
import blockchain.Transaction;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

public class Node implements NetworkNode {
    private Set<String> peers;
    private Blockchain blockchain;

    public Node(Blockchain blockchain) {
        this.blockchain = blockchain;
        this.peers = new HashSet<>(); // Using a Set to avoid duplicate peer addresses
    }


    public Socket getSocketForPeer(String peerAddress, int port) {
        try {
            // Attempt to create a new Socket object with the peer's address and port
            Socket socket = new Socket(peerAddress, port);
            return socket;
        } catch (UnknownHostException e) {
            System.err.println("Unknown host: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Error creating socket: " + e.getMessage());
        }
        return null;
    }

    @Override
    public void connectToPeer(String peerAddress) {
        try {
            // Assuming peerAddress is in the format "hostname:port"
            String[] addressComponents = peerAddress.split(":");
            String hostname = addressComponents[0];
            int port = Integer.parseInt(addressComponents[1]);

            // Create a new socket to connect to the peer
            Socket socket = new Socket(hostname, port);

            // At this point, the connection is established, and you can use the socket to communicate
            // You might want to store this socket in a list or map for later use

        } catch (IOException e) {
            e.printStackTrace();
            // Handle exceptions, possibly by logging or notifying the user
        }
    }


    @Override
    public void disconnectFromPeer(String peerAddress) {//FIX
        // Assuming you have a way to retrieve the Socket object for a given peer address
        Socket socket= new Socket();
        //Socket socket = getSocketForPeer(peerAddress);
        if (socket != null) {
            try {
                socket.close();
                // Optionally, remove the socket from your collection of active connections
            } catch (IOException e) {
                e.printStackTrace();
                // Handle exceptions, such as logging the error
            }
        }
    }


    @Override
    public void broadcastTransaction(Transaction transaction) {
        String transactionData = new Gson().toJson(transaction); // Convert transaction to JSON string
        for (String peerAddress : peers) {
            //Socket peerSocket = getSocketForPeer(peerAddress); // Method to get socket for a given peer
            Socket peerSocket= new Socket();
            if (peerSocket != null && !peerSocket.isClosed()) {
                try {
                    PrintWriter out = new PrintWriter(peerSocket.getOutputStream(), true);
                    out.println(transactionData); // Send the transaction data to the peer
                } catch (IOException e) {
                    System.err.println("Error broadcasting transaction to " + peerAddress + ": " + e.getMessage());
                }
            }
        }
    }

    @Override // Broadcast new block to all connected peers
    public void broadcastNewBlock(Block block) {
        // Convert block to JSON string
        String blockData = new Gson().toJson(block);
        // Iterate through each connected peer
        for (String peerAddress : peers) {
            // Get the socket for communication with this peer
            //Socket peerSocket = getSocketForPeer(peerAddress);
            Socket peerSocket= new Socket();
            // Check if the socket is valid and open
            if (peerSocket != null && !peerSocket.isClosed()) {
                try {
                    // Send the block data to the peer
                    PrintWriter out = new PrintWriter(peerSocket.getOutputStream(), true);
                    out.println(blockData);
                } catch (IOException e) {
                    System.err.println("Error broadcasting new block to " + peerAddress + ": " + e.getMessage());
                }
            }
        }
    }


    @Override
    public void validateBlock(Block block) {

    }

    @Override
    public void requestLatestBlock() {

    }

    @Override
    public void sharePeersList() {

    }

    @Override
    public void handleRequest(Message request) {

    }

    @Override
    public void syncBlockchain() {
        // Request the latest blockchain data from peers and synchronize
    }

    //methods for handling incoming messages, etc.
}