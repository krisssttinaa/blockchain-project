package networking;

import java.net.Socket;

public class PeerInfo {
    private String ipAddress;
    private boolean isConnected;
    private transient Socket socket; // `transient` keyword ensures `socket` is not serialized

    // Constructor for initial connection without a socket
    public PeerInfo(String ipAddress) {
        this.ipAddress = ipAddress;
        this.isConnected = true;
    }

    // Constructor that accepts a socket
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