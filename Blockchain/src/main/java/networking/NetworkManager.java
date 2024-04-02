package networking;

import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.HashMap;

public class NetworkManager {
    //Hashmap of nodes where key is IP, and value is the connection
    private HashMap<String, Node> nodes;

    public NetworkManager(){
        nodes = new HashMap<>();
        getMyIpAddress();
        try {
            Server server = new Server(7777, this);
            server.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public void addNode(Node node){
        nodes.put(node.getIp(), node);
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
}
