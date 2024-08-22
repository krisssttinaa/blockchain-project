package networking;
import java.io.IOException;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import blockchain.Blockchain;

public class NetworkManager {
    private final Map<String, Node> nodes;
    private final ExecutorService networkPool;
    private final Blockchain blockchain;
    private final int serverPort;

    public NetworkManager(Blockchain blockchain, int serverPort) { // Update constructor to accept a Blockchain instance
        this.nodes = new ConcurrentHashMap<>();
        this.networkPool = Executors.newCachedThreadPool();
        this.blockchain = blockchain;
        this.serverPort = serverPort;
        startServer();
        startPeerDiscovery();
    }

    private void startServer() {
        networkPool.submit(() -> {
            try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        Node node = new Node(clientSocket, blockchain); // Pass the blockchain instance to Node
                        nodes.put(node.getIp(), node);
                        networkPool.submit(node);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void startPeerDiscovery() {
        networkPool.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                // Send peer discovery messages periodically
                broadcastMessage(new Message(MessageType.PEER_DISCOVERY_REQUEST, "Discover peers")); // Pass a Message object
                try {
                    Thread.sleep(60000); // Sleep for a minute before next discovery
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    public void connectToPeer(String address, int port) {
        networkPool.submit(() -> {
            try {
                Socket socket = new Socket(address, port);
                Node node = new Node(socket, blockchain); // Pass the blockchain instance to Node
                nodes.put(node.getIp(), node);
                networkPool.submit(node);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    // Broadcast a Message object to all nodes
    public void broadcastMessage(Message message) {
        nodes.values().forEach(node -> node.sendMessage(message));
    }

    public void removeNode(String ip) {
        Node node = nodes.get(ip);
        if (node != null) {
            node.closeConnection();
            nodes.remove(ip);
        }
    }

    public Node getNode(String ip) {
        return nodes.get(ip);
    }
}
