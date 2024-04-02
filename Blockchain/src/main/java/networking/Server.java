package networking;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server extends Thread{
    private ServerSocket serverSocket;
    private NetworkManager networkManager;
    public Server(int port, NetworkManager networkManager) throws IOException {
        serverSocket = new ServerSocket(port);
        networkManager = networkManager;
    }
    public void run(){
        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                //create new Node passing the socket in construtor
                //networkManager.addNode(new Node(clientSocket));
                System.out.println("New connection from " + clientSocket.getInetAddress().getHostAddress());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
