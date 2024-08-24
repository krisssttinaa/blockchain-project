/*package old_networking;

import networking.NetworkManager;
import networking.Node;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server extends Thread {
    private final ServerSocket serverSocket;
    private final NetworkManager networkManager;

    public Server(int port, NetworkManager networkManager) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.networkManager = networkManager;
    }

    @Override
    public void run() {
        while (!interrupted()) {
            try {
                Socket clientSocket = serverSocket.accept();
                Node node = new Node(clientSocket, networkManager.getBlockchain(), networkManager); // Pass NetworkManager
                networkManager.addNode(node);
                System.out.println("New connection from " + clientSocket.getInetAddress().getHostAddress());
            } catch (IOException e) {
                e.printStackTrace();
                if (serverSocket.isClosed()) {
                    break;
                }
            }
        }
    }
}*/