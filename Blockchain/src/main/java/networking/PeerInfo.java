package networking;

import java.net.Socket;

public class PeerInfo {
    private String ipAddress;
    private boolean isConnected;
    private transient Socket socket; // transient keyword ensures socket is not serialized
    private long lastPingTime;  // Time when the last ping was sent
    private long lastPingResponseTime;  // Time when the last pong was received

    // Constructor for initial connection without a socket
    public PeerInfo(String ipAddress, boolean isConnected) {
        this.ipAddress = ipAddress;
        this.isConnected = isConnected;  // Set the connection status
        this.lastPingTime = System.currentTimeMillis();  // Initialize ping time
        this.lastPingResponseTime = System.currentTimeMillis();  // Initialize pong response time
        printStateChange("Constructor (IP + isConnected)");
    }

    // Constructor for initializing with both socket and connection status
    public PeerInfo(String ipAddress, Socket socket, boolean isConnected) {
        this.ipAddress = ipAddress;
        this.socket = socket;
        this.isConnected = isConnected;
        this.lastPingTime = System.currentTimeMillis();  // Initialize ping time
        this.lastPingResponseTime = System.currentTimeMillis();  // Initialize pong response time
        printStateChange("Constructor (IP + Socket + isConnected)");
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

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
        printStateChange("setIpAddress");
    }

    public long getLastPingTime() {
        return lastPingTime;
    }

    public void setLastPingTime(long lastPingTime) {
        long previousPingTime = this.lastPingTime;
        this.lastPingTime = lastPingTime;
        printStateChange("setLastPingTime", previousPingTime, lastPingTime);
    }

    public long getLastPingResponseTime() {
        return lastPingResponseTime;
    }

    public void setLastPingResponseTime(long lastPingResponseTime) {
        long previousPingResponseTime = this.lastPingResponseTime;
        this.lastPingResponseTime = lastPingResponseTime;
        printStateChange("setLastPingResponseTime", previousPingResponseTime, lastPingResponseTime);
    }

    // Helper to print state changes
    private void printStateChange(String method) {
        System.out.println("PeerInfo state changed via " + method + ". IP: " + ipAddress + ", isConnected: " + isConnected + ", Socket: " + socket);
    }

    private void printStateChange(String method, boolean previousState, boolean newState) {
        System.out.println("PeerInfo isConnected changed via " + method + ". IP: " + ipAddress + ", Previous: " + previousState + ", New: " + newState);
    }

    private void printStateChange(String method, Socket previousSocket, Socket newSocket) {
        System.out.println("PeerInfo socket changed via " + method + ". IP: " + ipAddress + ", Previous Socket: " + previousSocket + ", New Socket: " + newSocket);
    }

    private void printStateChange(String method, long previousTime, long newTime) {
        System.out.println("PeerInfo " + method + " changed. IP: " + ipAddress + ", Previous Time: " + previousTime + ", New Time: " + newTime);
    }
}