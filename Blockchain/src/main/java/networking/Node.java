package networking;

import java.io.*;
import java.net.Socket;

public class Node implements Runnable {
    private final Socket socket;
    private BufferedReader input;
    private PrintWriter output;

    public Node (Socket socket) {
        this.socket = socket;
        try {
            this.input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
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
        // Process the received message
    }

    public String getIp() {
        return socket.getInetAddress().getHostAddress();
    }

    public void sendMessage(String message) {
            output.write(message); // Write the message to the output stream of the socket connection to the peer node
            output.flush(); // Flush the output stream to send the message immediately to the peer node
    }

    public void closeConnection() {
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Request the latest block from the connected peer
    public void requestLatestBlock() {
        sendMessage("latest block request");
    }

    // Sync blockchain logic here
    public void syncBlockchain() {
        sendMessage("sync request");
    }

    public Socket getSocket() {
        return socket;
    }
}