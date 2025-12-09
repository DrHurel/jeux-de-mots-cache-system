package fr.lirmm.jdm.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sharded cache that distributes keys across multiple cache instances to reduce contention.
 * 
 * This optimization provides:
 * - Reduced lock contention by partitioning data
 * - Better scalability for high-concurrency workloads
 * - Consistent hashing for even distribution
 * - Independent eviction per shard
 * 
 * Use cases:
 * - High write contention (many threads writing simultaneously)
 * - Large cache sizes (>10K entries)
 * - Uniformly distributed keys
 * 
 * Trade-offs:
 * - Cannot easily get exact global size
 * - Eviction happens per shard, not globally
 * - Memory overhead for multiple cache instances
 * 
 * @param <K> Key type
 * @param <V> Value type
 */
public class ShardedCache<K, V> implements Cache<K, V> {
    
    /** Default multiplier for calculating optimal shard count based on CPU cores */
    private static final int DEFAULT_SHARD_MULTIPLIER = 4;
    
    private final List<Cache<K, V>> shards;
    private final int shardCount;
    private final AtomicLong totalHits = new AtomicLong(0);
    private final AtomicLong totalMisses = new AtomicLong(0);
    
    /**
     * Creates a sharded cache with optimal shard count (based on CPU cores).
     * Uses {@code Runtime.getRuntime().availableProcessors() * DEFAULT_SHARD_MULTIPLIER} shards.
     * 
     * @param config Cache configuration
     */
    public ShardedCache(CacheConfig config) {
        this(config, Runtime.getRuntime().availableProcessors() * DEFAULT_SHARD_MULTIPLIER);
    }
    
    /**
     * Creates a sharded cache with custom shard count.
     * 
     * @param config Cache configuration
     * @param shardCount Number of shards (must be power of 2 for best performance)
     * @throws IllegalArgumentException if config is null or shardCount is not positive
     */
    public ShardedCache(CacheConfig config, int shardCount) {
        if (config == null) {
            throw new IllegalArgumentException("Cache configuration must not be null");
        }
        if (shardCount <= 0) {
            throw new IllegalArgumentException("Shard count must be positive, got: " + shardCount);
        }
        
        // Round up to nearest power of 2 for fast modulo
        this.shardCount = nextPowerOfTwo(shardCount);
        this.shards = new ArrayList<>(this.shardCount);
        
        // Calculate size per shard
        int sizePerShard = Math.max(1, config.getMaxSize() / this.shardCount);
        
        // Create shards
        for (int i = 0; i < this.shardCount; i++) {
            CacheConfig shardConfig = CacheConfig.builder()
                .maxSize(sizePerShard)
                .ttl(config.getTtl())
                .evictionStrategy(config.getEvictionStrategy())
                .build();
            
            Cache<K, V> shard;
            if (config.getEvictionStrategy() == CacheConfig.EvictionStrategy.LRU) {
                shard = new LruCache<>(shardConfig);
            } else {
                shard = new TtlCache<>(shardConfig);
            }
            
            shards.add(shard);
        }
    }
    
    @Override
    public void put(K key, V value) {
        getShard(key).put(key, value);
    }
    
    @Override
    public V get(K key) {
        V value = getShard(key).get(key);
        
        // Track stats
        if (value != null) {
            totalHits.incrementAndGet();
        } else {
            totalMisses.incrementAndGet();
        }
        
        return value;
    }
    
    @Override
    public void invalidate(K key) {
        getShard(key).invalidate(key);
    }
    
    @Override
    public void clear() {
        for (Cache<K, V> shard : shards) {
            shard.clear();
        }
        totalHits.set(0);
        totalMisses.set(0);
    }
    
    @Override
    public CacheStats getStats() {
        long totalSize = 0;
        long totalEvictions = 0;
        
        for (Cache<K, V> shard : shards) {
            CacheStats shardStats = shard.getStats();
            totalSize += shardStats.getSize();
            totalEvictions += shardStats.getEvictionCount();
        }
        
        return new CacheStats(
            totalHits.get(),
            totalMisses.get(),
            totalEvictions,
            totalSize
        );
    }
    
    /**
     * Gets the total number of entries across all shards.
     * 
     * Note: This is an approximate count as shards may be changing concurrently.
     * 
     * @return Approximate total size
     */
    @Override
    public int size() {
        long totalSize = shards.stream()
            .mapToLong(shard -> shard.size())
            .sum();
        // Cap at Integer.MAX_VALUE to match interface contract
        return (int) Math.min(totalSize, Integer.MAX_VALUE);
    }
    
    /**
     * Gets the number of shards.
     * 
     * @return Shard count
     */
    public int getShardCount() {
        return shardCount;
    }
    
    /**
     * Gets statistics for a specific shard (useful for debugging).
     * 
     * @param shardIndex Shard index (0 to shardCount-1)
     * @return Shard statistics
     */
    public CacheStats getShardStats(int shardIndex) {
        if (shardIndex < 0 || shardIndex >= shardCount) {
            throw new IllegalArgumentException("Invalid shard index: " + shardIndex);
        }
        return shards.get(shardIndex).getStats();
    }
    
    /**
     * Gets the shard for a given key using consistent hashing.
     * 
     * @param key The key
     * @return The shard responsible for this key
     */
    private Cache<K, V> getShard(K key) {
        int hash = hash(key);
        int index = hash & (shardCount - 1); // Fast modulo for power of 2
        return shards.get(index);
    }
    
    /**
     * Improved hash function to reduce collisions.
     * Uses MurmurHash3-style mixing.
     * 
     * @param key The key to hash
     * @return Hash code
     */
    private int hash(K key) {
        int h = key.hashCode();
        
        // Spread bits using MurmurHash3 finalizer
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        
        return h;
    }
    
    /**
     * Rounds up to next power of 2 for fast modulo operations.
     * 
     * @param n Input number
     * @return Next power of 2 >= n
     */
    private int nextPowerOfTwo(int n) {
        if (n <= 1) return 1;
        if ((n & (n - 1)) == 0) return n; // Already power of 2
        
        int power = 1;
        while (power < n) {
            power *= 2;
        }
        return power;
    }
}
