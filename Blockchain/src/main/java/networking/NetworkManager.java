package networking;

import com.google.gson.Gson;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;

public class NetworkManager {
    //Hashmap of nodes where key is IP, and value is the connection
    //so that if I want to send something I will say nodes.get(ip).send(message) //some helper functions
    public HashMap<String, Node> nodes;
    public NetworkManager(){
        nodes= new HashMap<>();
        getMyIpAddress();
        try {
            Server server = new Server(7777, this);
            server.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

    public void broadcastMessage(String message) {
        Gson gson = new Gson();
        String jsonMessage = gson.toJson(message);

        for (Node peer : nodes.values()) {
            peer.sendMessage(jsonMessage);
        }
    }

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
