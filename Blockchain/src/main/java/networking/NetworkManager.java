package networking;

import com.google.gson.Gson;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetworkManager {
    private final Map<String, Node> nodes;
    private final ExecutorService networkPool;
    private final int serverPort=7777;

    public NetworkManager() {
        this.nodes = new ConcurrentHashMap<>();
        this.networkPool = Executors.newCachedThreadPool();
        startServer();
    }

    private void startServer() {
        networkPool.submit(() -> {
            try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        Node node = new Node(clientSocket);
                        String nodeHash = String.valueOf(node.hashCode());
                        nodes.put(nodeHash, node);
                        networkPool.submit(node); // Assume Node implements Runnable
                    } catch (IOException e) {
                        e.printStackTrace(); // Handle as appropriate.
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void connectToPeer(String address, int port) {
        networkPool.submit(() -> {
            try {
                Socket socket = new Socket(address, port);
                Node node = new Node(socket);
                String nodeHash = String.valueOf(node.hashCode());
                nodes.put(nodeHash, node);
                networkPool.submit(node); // Assume Node implements Runnable
            } catch (IOException e) {
                e.printStackTrace(); // Handle as appropriate.
            }
        });
    }

    public void broadcastMessage(String message) {
        // Broadcast message to all connected peers
        nodes.values().forEach(node -> node.sendMessage(message));
    }
    public void getMyIpAddress(){
        try {
            NetworkInterface.getNetworkInterfaces().asIterator().forEachRemaining(networkInterface -> {
                networkInterface.getInterfaceAddresses().forEach(interfaceAddress -> {
                    System.out.println(networkInterface.getName() + ":" +interfaceAddress.getAddress());
                });
            });

        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    // Add node to the manager
    public void addNode(Node node) {
        if (node != null && node.getSocket() != null) {
            String ip = node.getIp();
            nodes.put(ip, node);
        }
    }

    public void removeNode(String ip) {
        Node node = nodes.get(ip);
        if (node != null) {
            node.closeConnection();
            nodes.remove(ip);
        }
    }
    /*
        public void broadcastMessage(String message) {
            Gson gson = new Gson();
            String jsonMessage = gson.toJson(message);

            for (Node peer : nodes.values()) {
                peer.sendMessage(jsonMessage);
            }
        }

            public void connectToPeer(String ip){
            try {
                Socket socket = new Socket(ip, 7777);
                Node node = new Node(socket);
                addNode(node);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // Handle request based on the type
        public void handleRequest(Message request) {
            // Implementation based on request type
        }
    */
    public Node getNode(String ip) {
        return nodes.get(ip);
    }



    // Close the connection with a node
    public void disconnectFromPeer(String peerAddress) {
        Node node = nodes.get(peerAddress);
        if (node != null) {
            node.closeConnection();
            nodes.remove(peerAddress);
        }
    }

    // Request the latest block from all nodes
    public void requestLatestBlock() {
        for (Node node : nodes.values()) {
            node.requestLatestBlock();
        }
    }

    // Share the peer list with all nodes
    public void sharePeersList() {
        String peersJson = new Gson().toJson(nodes.keySet());
        broadcastMessage(peersJson);
    }

    // Sync blockchain with all nodes
    public void syncBlockchain() {
        for (Node node : nodes.values()) {
            node.syncBlockchain();
        }
    }
}
