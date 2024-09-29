package ledger;

import java.util.LinkedHashMap;
import java.util.Map;

public class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private final int maxSize;

    // Constructor to initialize LRU cache with a fixed capacity
    public LRUCache(int capacity) {
        super(capacity, 0.75f, true); // 0.75f is the default load factor, 'true' enables access-order
        this.maxSize = capacity;
    }

    // This method determines if the eldest entry should be removed when capacity is exceeded
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxSize;
    }
}