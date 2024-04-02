package socket_try;

import java.io.*;
import java.net.*;

public class BlockchainClient {
    private String serverName;
    private int serverPort;

    public BlockchainClient(String serverName, int serverPort) {
        this.serverName = serverName;
        this.serverPort = serverPort;
    }

    public void connect() {
        try {
            System.out.println("Connecting to server on port " + serverPort);
            Socket client = new Socket(serverName, serverPort);
            System.out.println("Connected to " + client.getRemoteSocketAddress());
            OutputStream outToServer = client.getOutputStream();
            DataOutputStream out = new DataOutputStream(outToServer);
            out.writeUTF("Hello from " + client.getLocalSocketAddress());
            InputStream inFromServer = client.getInputStream();
            DataInputStream in = new DataInputStream(inFromServer);
            System.out.println("Server says " + in.readUTF());
            client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}