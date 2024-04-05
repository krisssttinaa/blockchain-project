package networking;

import blockchain.Blockchain;

import java.io.*;
import java.net.Socket;

public class Node  {
    private NetworkManager networkmanager;
    private Blockchain blockchain;
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

    public String getIp() {
        return socket.getInetAddress().getHostAddress();
    }

    public void sendMessage(String message) {
        output.println(message);
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

    // Handle the incoming message
}