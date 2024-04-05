package networking;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
public class Server extends Thread{
    private final ServerSocket serverSocket;
    private final NetworkManager networkManager;
    public Server(int port, NetworkManager networkManager) throws IOException {
        serverSocket = new ServerSocket(port);
        this.networkManager = networkManager;}
    @Override
    public void run() {
        while (!interrupted()) {
            try {
                Socket clientSocket = serverSocket.accept();
                //create new Node passing the socket in constructor//networkManager.addNode(new Node(clientSocket));
                networkManager.addNode(new Node(clientSocket));
                System.out.println("New connection from " + clientSocket.getInetAddress().getHostAddress());
            } catch (IOException e) {
                e.printStackTrace();
                if (serverSocket.isClosed()) {
                    break;
                }
            }
        }
    }
}