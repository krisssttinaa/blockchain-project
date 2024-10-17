package networking;

import blockchain.Blockchain;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class BlockchainSyncManager {
    private final Blockchain blockchain;
    private final NetworkManager networkManager;

    public BlockchainSyncManager(Blockchain blockchain, NetworkManager networkManager) {
        this.blockchain = blockchain;
        this.networkManager = networkManager;
    }

    public void syncWithPeers(int latestIndex) {
        int currentIndex = blockchain.getLastBlock().getIndex();
        int blocksToFetch = latestIndex - currentIndex;
        if (blocksToFetch <= 0) {
            System.out.println("No blocks to sync. Already up to date.");
            return;
        }
        System.out.println("Syncing " + blocksToFetch + " blocks from peers.");
        List<PeerInfo> connectedPeers = networkManager.getPeers().values().stream()
                .filter(PeerInfo::isConnected)
                .collect(Collectors.toList());

        if (connectedPeers.isEmpty()) {
            System.out.println("No connected peers available for syncing.");
            return;
        }

        // Divide the blocks to fetch across connected peers
        int blocksPerPeer = Math.max(1, blocksToFetch / connectedPeers.size());
        for (int i = 0; i < connectedPeers.size(); i++) {
            PeerInfo peer = connectedPeers.get(i);
            int startIndex = currentIndex + i * blocksPerPeer;
            int endIndex = Math.min(startIndex + blocksPerPeer, latestIndex);
            System.out.println("Requesting blocks " + startIndex + " to " + endIndex + " from peer " + peer.getIpAddress());
            requestBlocksFromPeer(peer, startIndex, endIndex);
        }
    }

    public void requestBlocksFromPeer(PeerInfo peer, int startIndex, int endIndex) {
        System.out.println("Requesting blocks " + startIndex + " to " + endIndex + " from peer " + peer.getIpAddress());
        String requestData = startIndex + "," + endIndex;
        Message blockRequest = new Message(MessageType.BLOCK_REQUEST, requestData);
        try {
            networkManager.sendMessageToPeer(peer.getSocket(), blockRequest);
        } catch (IOException e) {
            System.err.println("Failed to request blocks from peer " + peer.getIpAddress() + ": " + e.getMessage());
        }
    }
}