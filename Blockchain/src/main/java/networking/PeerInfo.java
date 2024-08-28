package networking;

import java.net.Socket;

public class PeerInfo {
    private String ipAddress;
    private boolean isConnected;
    private Socket socket; // New field to store the active socket connection

    // Constructor for initial connection without a socket (for backward compatibility or when a socket isn't available yet)
    public PeerInfo(String ipAddress) {
        this.ipAddress = ipAddress;
        this.isConnected = true;
        this.socket = null;
    }

    // New constructor that accepts a socket
    public PeerInfo(String ipAddress, Socket socket) {
        this.ipAddress = ipAddress;
        this.isConnected = true;
        this.socket = socket;
    }

    // Getters and setters
    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void setConnected(boolean connected) {
        isConnected = connected;
    }

    public Socket getSocket() {
        return socket;
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }
}
