package networking;

public class PeerInfo {
    private String ipAddress;
    private boolean isConnected;

    public PeerInfo(String ipAddress) {
        this.ipAddress = ipAddress;
        this.isConnected = true;
    }

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
}