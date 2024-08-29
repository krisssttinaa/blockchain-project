package networking;

import java.net.Socket;

public class PeerInfo {
    private String ipAddress;
    private boolean isConnected;
    private transient Socket socket; // transient keyword ensures socket is not serialized

    // Constructor for initial connection without a socket
    public PeerInfo(String ipAddress, boolean isConnected) {
        this.ipAddress = ipAddress;
        this.isConnected = isConnected;  // Set the connection status
        printStateChange("Constructor (IP + isConnected)");
    }

    // Constructor that accepts a socket
    public PeerInfo(String ipAddress, Socket socket) {
        this.ipAddress = ipAddress;
        this.socket = socket;
        this.isConnected = true;  // Connected upon socket creation
        printStateChange("Constructor (IP + Socket)");
    }

    // Constructor for initializing with both socket and connection status
    public PeerInfo(String ipAddress, Socket socket, boolean isConnected) {
        this.ipAddress = ipAddress;
        this.socket = socket;
        this.isConnected = isConnected;
        printStateChange("Constructor (IP + Socket + isConnected)");
    }

    // Getters and setters
    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
        printStateChange("setIpAddress");
    }

    public boolean isConnected() {
        return isConnected;
    }

    public synchronized void setConnected(boolean connected) {
        boolean previousState = this.isConnected;
        this.isConnected = connected;
        printStateChange("setConnected", previousState, connected);
    }

    public Socket getSocket() {
        return socket;
    }

    public synchronized void setSocket(Socket socket) {
        Socket previousSocket = this.socket;
        this.socket = socket;
        printStateChange("setSocket", previousSocket, socket);
    }

    // Print every state change with details
    private void printStateChange(String method) {
        System.out.println("PeerInfo state changed via " + method + ". IP: " + ipAddress + ", isConnected: " + isConnected + ", Socket: " + socket);
    }

    // Print state change with details for boolean state
    private void printStateChange(String method, boolean previousState, boolean newState) {
        System.out.println("PeerInfo isConnected changed via " + method + ". IP: " + ipAddress + ", Previous: " + previousState + ", New: " + newState);
    }

    // Print state change with details for socket state
    private void printStateChange(String method, Socket previousSocket, Socket newSocket) {
        System.out.println("PeerInfo socket changed via " + method + ". IP: " + ipAddress + ", Previous Socket: " + previousSocket + ", New Socket: " + newSocket);
    }
}