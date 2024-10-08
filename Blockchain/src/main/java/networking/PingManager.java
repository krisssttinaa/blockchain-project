package networking;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class PingManager {
    private final Map<String, PeerInfo> peers;
    private final ExecutorService networkPool;
    private final Gson gson;
    private final NetworkManager networkManager;  // Reference to NetworkManager to reuse sendMessageToPeer
    private final int pingInterval = 50000; // Ping interval in milliseconds
    private final int timeoutThreshold = 150000; // Timeout threshold in milliseconds (150 seconds)

    public PingManager(Map<String, PeerInfo> peers, ExecutorService networkPool, Gson gson, NetworkManager networkManager) {
        this.peers = peers;
        this.networkPool = networkPool;
        this.gson = gson;
        this.networkManager = networkManager;
        startPingPong(); // Start ping-pong mechanism on initialization
        monitorPeersForTimeouts(); // Start monitoring peers for timeouts
    }

    // Initiates the ping-pong mechanism
    public void startPingPong() {
        networkPool.submit(() -> {
            while (true) {
                try {
                    Thread.sleep(pingInterval);  // Send ping every pingInterval milliseconds
                    sendPingToPeers();
                } catch (InterruptedException e) {
                    System.err.println("Ping-pong thread interrupted: " + e.getMessage());
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    // Sends a PING message to all connected peers
    public void sendPingToPeers() {
        Message pingMessage = new Message(MessageType.PING, "PING");

        peers.forEach((publicKey, peerInfo) -> {
            if (peerInfo.isConnected()) {
                try {
                    networkManager.sendMessageToPeer(peerInfo.getSocket(), pingMessage);  // Reusing method from NetworkManager
                    peerInfo.setLastPingTime(System.currentTimeMillis()); // Record the time of the last ping
                } catch (IOException e) {
                    System.err.println("Failed to send ping to peer " + peerInfo.getIpAddress() + ": " + e.getMessage());
                    peerInfo.setConnected(false);  // Mark the peer as disconnected if the ping fails
                }
            }
        });
    }

    // Monitors peers for timeouts and marks them as disconnected if they don't respond within the timeout threshold
    public void monitorPeersForTimeouts() {
        networkPool.submit(() -> {
            while (true) {
                try {
                    Thread.sleep(30000);  // Check every 30 seconds
                    long currentTime = System.currentTimeMillis();
                    peers.forEach((publicKey, peerInfo) -> {
                        if (peerInfo.isConnected() && (currentTime - peerInfo.getLastPingResponseTime()) > timeoutThreshold) {
                            System.out.println("Peer " + peerInfo.getIpAddress() + " timed out after " + timeoutThreshold / 1000 + " seconds. Marking as disconnected.");
                            peerInfo.setConnected(false);  // Mark peer as disconnected due to timeout
                            networkManager.removePeer(publicKey);  // Remove the peer from the map
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }
}