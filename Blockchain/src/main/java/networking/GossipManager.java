package networking;

import com.google.gson.Gson;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public class GossipManager {
    private final Map<String, PeerInfo> peers; // Peers are still stored in NetworkManager
    private final ExecutorService networkPool;
    private final Gson gson;
    private final NetworkManager networkManager; // Reference to NetworkManager to reuse sendMessageToPeer method
    private final int gossipInterval = 60000; // Gossip interval in milliseconds
    private final Random random = new Random(); // Random instance for selecting peers

    public GossipManager(Map<String, PeerInfo> peers, ExecutorService networkPool, Gson gson, NetworkManager networkManager) {
        this.peers = peers;
        this.networkPool = networkPool;
        this.gson = gson;
        this.networkManager = networkManager; // Initialize networkManager for message sending
        startGossiping(); // Start the gossip protocol on initialization
    }

    // Initiates the gossip protocol
    public void startGossiping() {
        networkPool.submit(() -> {
            while (true) {
                try {
                    Thread.sleep(gossipInterval);
                    gossip();
                } catch (InterruptedException e) {
                    System.err.println("Gossiping thread interrupted: " + e.getMessage());
                    break;
                } catch (Exception e) {
                    System.err.println("Unexpected error during gossiping: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }

    private void gossip() {
        List<PeerInfo> connectedPeers = peers.values().stream()
                .filter(PeerInfo::isConnected)
                .collect(Collectors.toList());

        int peerCount = connectedPeers.size();
        if (peerCount == 0) {
            System.out.println("No peers to gossip with.");
            return;
        }

        List<PeerInfo> selectedPeers;
        if (peerCount == 1) {
            // If there's only 1 peer, send gossip to that peer
            selectedPeers = List.of(connectedPeers.get(0));
        } else if (peerCount == 2) {
            // If there are exactly 2 peers, send gossip to one of them randomly
            selectedPeers = List.of(getRandomPeer(connectedPeers));
        } else {
            // If more than 2 peers, randomly select 2 peers
            selectedPeers = getRandomPeers(connectedPeers, 2);
        }

        Message gossipMessage = new Message(MessageType.SHARE_PEER_LIST, gson.toJson(peers));
        for (PeerInfo peerInfo : selectedPeers) {
            //System.out.println("Gossiping peer list to " + peerInfo.getIpAddress());
            networkManager.sendOutgoingMessage(peerInfo.getSocket(), gossipMessage); // Reuse NetworkManager's method
        }
    }

    private PeerInfo getRandomPeer(List<PeerInfo> connectedPeers) {return connectedPeers.get(random.nextInt(connectedPeers.size()));}
    private List<PeerInfo> getRandomPeers(List<PeerInfo> connectedPeers, int count) {
        // Shuffle the list of connected peers and select the first 'count' peers
        return connectedPeers.stream()
                .sorted((a, b) -> random.nextInt(2) - 1) // Random sort
                .limit(count)
                .collect(Collectors.toList());
    }
}