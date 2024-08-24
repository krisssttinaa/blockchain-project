package old_networking;

import networking.Node;

import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;

public class RendezvousHashing {

    /**
     * Calculate a weight for a given key and node.
     *
     * @param key  The key (usually the IP address of the node).
     * @param node The node to which we're calculating the weight.
     * @return A weight (higher is better).
     */
    public static long calculateWeight(String key, String node) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String data = key + node;
            byte[] hash = md.digest(data.getBytes());
            return bytesToLong(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Converts a byte array to a long.
     *
     * @param bytes the byte array.
     * @return the long value.
     */
    private static long bytesToLong(byte[] bytes) {
        long value = 0L;
        for (int i = 0; i < bytes.length && i < 8; i++) {
            value = (value << 8) | (bytes[i] & 0xff);
        }
        return value;
    }

    /**
     * Selects the node with the highest weight for a given key.
     *
     * @param key   The key (usually the IP address of the current node).
     * @param nodes The list of potential nodes.
     * @return The selected node.
     */
    public static Optional<String> selectNode(String key, Map<String, Node> nodes) {
        return nodes.keySet().stream()
                .max((node1, node2) -> Long.compare(calculateWeight(key, node1), calculateWeight(key, node2)));
    }
}