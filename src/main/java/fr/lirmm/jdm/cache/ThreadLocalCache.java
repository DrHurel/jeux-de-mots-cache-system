package fr.lirmm.jdm.cache;

import java.lang.ref.SoftReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-local L1 cache layer that sits in front of a shared L2 cache.
 * 
 * This optimization provides:
 * - Zero-contention reads for thread-local hot data
 * - Reduced pressure on shared cache synchronization
 * - Automatic memory management via soft references
 * - Per-thread size limits to prevent memory bloat
 * 
 * Use cases:
 * - High read/write ratio workloads (>90% reads)
 * - Temporal locality (threads repeatedly access same keys)
 * - Many threads competing for same hot keys
 * 
 * Trade-offs:
 * - Increased memory usage (each thread has its own cache)
 * - Potential staleness (thread-local cache may be outdated)
 * - Not suitable for write-heavy workloads
 * 
 * <p>This cache implements {@link AutoCloseable} for proper resource cleanup.
 * Use with try-with-resources when possible:
 * <pre>{@code
 * try (ThreadLocalCache<String, User> cache = new ThreadLocalCache<>(backingCache)) {
 *     // Use cache
 * } // Automatic cleanup
 * }</pre>
 * 
 * @param <K> Key type
 * @param <V> Value type
 */
public class ThreadLocalCache<K, V> implements Cache<K, V>, AutoCloseable {
    
    /** Default maximum size for thread-local L1 cache */
    private static final int DEFAULT_THREAD_LOCAL_MAX_SIZE = 100;
    
    private final Cache<K, V> backingCache;
    private final int threadLocalMaxSize;
    private final ThreadLocal<SoftReference<LocalCache<K, V>>> threadLocalCache;
    
    // Statistics
    private final AtomicLong l1Hits = new AtomicLong(0);
    private final AtomicLong l1Misses = new AtomicLong(0);
    private final AtomicLong l2Hits = new AtomicLong(0);
    private final AtomicLong l2Misses = new AtomicLong(0);
    
    /**
     * Creates a thread-local cache with default L1 size.
     * 
     * @param backingCache The shared L2 cache
     * @throws IllegalArgumentException if backingCache is null
     */
    public ThreadLocalCache(Cache<K, V> backingCache) {
        this(backingCache, DEFAULT_THREAD_LOCAL_MAX_SIZE);
    }
    
    /**
     * Creates a thread-local cache with custom L1 size.
     * 
     * @param backingCache The shared L2 cache
     * @param threadLocalMaxSize Max entries per thread's L1 cache
     * @throws IllegalArgumentException if backingCache is null or threadLocalMaxSize is not positive
     */
    public ThreadLocalCache(Cache<K, V> backingCache, int threadLocalMaxSize) {
        if (backingCache == null) {
            throw new IllegalArgumentException("Backing cache must not be null");
        }
        if (threadLocalMaxSize <= 0) {
            throw new IllegalArgumentException("Thread-local max size must be positive, got: " + threadLocalMaxSize);
        }
        this.backingCache = backingCache;
        this.threadLocalMaxSize = threadLocalMaxSize;
        this.threadLocalCache = ThreadLocal.withInitial(() -> 
            new SoftReference<>(new LocalCache<>(threadLocalMaxSize))
        );
    }
    
    @Override
    public void put(K key, V value) {
        // Write to L2 cache
        backingCache.put(key, value);
        
        // Update L1 cache for current thread
        getOrCreateLocalCache().put(key, value);
    }
    
    @Override
    public V get(K key) {
        LocalCache<K, V> l1Cache = getOrCreateLocalCache();
        
        // Try L1 cache first
        V l1Result = l1Cache.get(key);
        if (l1Result != null) {
            l1Hits.incrementAndGet();
            return l1Result;
        }
        
        l1Misses.incrementAndGet();
        
        // Try L2 cache
        V l2Result = backingCache.get(key);
        if (l2Result != null) {
            l2Hits.incrementAndGet();
            // Promote to L1 cache
            l1Cache.put(key, l2Result);
            return l2Result;
        }
        
        l2Misses.incrementAndGet();
        return null;
    }
    
    @Override
    public void invalidate(K key) {
        backingCache.invalidate(key);
        getOrCreateLocalCache().remove(key);
    }
    
    @Override
    public void clear() {
        backingCache.clear();
        threadLocalCache.remove(); // Clear thread-local cache
    }
    
    /**
     * Gets the current size from backing cache stats.
     */
    @Override
    public int size() {
        return (int) backingCache.getStats().getSize();
    }
    
    @Override
    public CacheStats getStats() {
        CacheStats backingStats = backingCache.getStats();
        
        // Combine L1 and L2 statistics
        long totalHits = l1Hits.get() + l2Hits.get();
        long totalMisses = l2Misses.get(); // Only L2 misses are true misses
        
        return new CacheStats(
            totalHits,
            totalMisses,
            backingStats.getEvictionCount(),
            backingStats.getSize()
        );
    }
    
    /**
     * Gets detailed L1/L2 cache statistics.
     * 
     * @return Statistics breakdown
     */
    public ThreadLocalCacheStats getDetailedStats() {
        return new ThreadLocalCacheStats(
            l1Hits.get(),
            l1Misses.get(),
            l2Hits.get(),
            l2Misses.get()
        );
    }
    
    /**
     * Clears only the current thread's L1 cache.
     */
    public void clearThreadLocal() {
        threadLocalCache.remove();
    }
    
    private LocalCache<K, V> getOrCreateLocalCache() {
        SoftReference<LocalCache<K, V>> ref = threadLocalCache.get();
        LocalCache<K, V> cache = ref.get();
        
        // If soft reference was cleared by GC, create new cache
        if (cache == null) {
            cache = new LocalCache<>(threadLocalMaxSize);
            threadLocalCache.set(new SoftReference<>(cache));
        }
        
        return cache;
    }
    
    /**
     * Simple LRU cache for thread-local storage.
     */
    private static class LocalCache<K, V> {
        private final Map<K, V> cache;
        
        LocalCache(int maxSize) {
            this.cache = new LinkedHashMap<>(maxSize, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                    return size() > maxSize;
                }
            };
        }
        
        void put(K key, V value) {
            cache.put(key, value);
        }
        
        V get(K key) {
            return cache.get(key);
        }
        
        void remove(K key) {
            cache.remove(key);
        }
    }
    
    /**
     * Detailed statistics for L1/L2 cache behavior.
     */
    public record ThreadLocalCacheStats(
        long l1Hits,
        long l1Misses,
        long l2Hits,
        long l2Misses
    ) {
        public double l1HitRate() {
            long total = l1Hits + l1Misses;
            return total == 0 ? 0.0 : (double) l1Hits / total;
        }
        
        public double l2HitRate() {
            long total = l2Hits + l2Misses;
            return total == 0 ? 0.0 : (double) l2Hits / total;
        }
        
        public double overallHitRate() {
            long totalHits = l1Hits + l2Hits;
            long totalRequests = l1Hits + l1Misses + l2Misses;
            return totalRequests == 0 ? 0.0 : (double) totalHits / totalRequests;
        }
    }
    
    /**
     * Cleans up thread-local resources.
     * 
     * <p>This method removes the thread-local cache for the current thread, helping to prevent
     * memory leaks in environments with thread pools. While SoftReferences provide automatic
     * memory management under pressure, explicit cleanup is recommended for long-lived threads.
     * 
     * <p>This method is idempotent and can be called multiple times safely.
     */
    @Override
    public void close() {
        threadLocalCache.remove();
    }
}
