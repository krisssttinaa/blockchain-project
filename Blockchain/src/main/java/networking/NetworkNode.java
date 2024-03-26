package networking;
import blockchain.Block;
import blockchain.Transaction;

public interface NetworkNode {
    void connectToPeer(String peerAddress);
    void disconnectFromPeer(String peerAddress);
    void broadcastTransaction(Transaction transaction);
    void broadcastNewBlock(Block block);
    void syncBlockchain();
}