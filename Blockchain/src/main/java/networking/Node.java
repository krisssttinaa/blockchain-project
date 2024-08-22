package networking;
import blockchain.Blockchain;
import com.google.gson.Gson;
import networking.Message;
import networking.MessageType;

import java.io.*;
import java.net.Socket;

public class Node implements Runnable {
    private final Socket socket;
    private final Blockchain blockchain; // Add this field to store the blockchain instance
    private BufferedReader input;
    private PrintWriter output;

    public Node(Socket socket, Blockchain blockchain) { // Update constructor to accept a Blockchain instance
        this.socket = socket;
        this.blockchain = blockchain; // Initialize the blockchain field
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

    private void handleReceivedMessage(String message) {
        Gson gson = new Gson();
        Message receivedMsg = gson.fromJson(message, Message.class);
        switch (receivedMsg.getType()) {
            case NEW_TRANSACTION:
                // Handle new transaction
                break;
            case NEW_BLOCK:
                // Handle new block
                break;
            case BLOCKCHAIN_REQUEST:
                // Respond with the current blockchain
                String blockchainJson = gson.toJson(blockchain); // Use blockchain instance
                sendMessage(new Message(MessageType.BLOCKCHAIN_RESPONSE, blockchainJson));
                break;
            case SYNC_REQUEST:
                // Respond with the full blockchain for synchronization
                String syncBlockchainJson = gson.toJson(blockchain); // Use blockchain instance
                sendMessage(new Message(MessageType.SYNC_RESPONSE, syncBlockchainJson));
                break;
            case SYNC_RESPONSE:
                // Handle sync response by comparing and replacing the blockchain if needed
                Blockchain receivedBlockchain = gson.fromJson(receivedMsg.getData(), Blockchain.class);
                blockchain.compareAndReplace(receivedBlockchain); // Use blockchain instance
                break;
            default:
                System.out.println("Unknown message type received");
        }
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
}