package fr.lirmm.jdm.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A thread-safe Least Recently Used (LRU) cache implementation.
 *
 * <p>This cache evicts the least recently used entry when the maximum size is reached. All
 * operations are O(1) time complexity thanks to the LinkedHashMap's access-order mode.
 *
 * <p>Thread safety is provided through a StampedLock, offering better read performance through
 * optimistic reads and reduced contention.
 *
 * @param <K> the type of keys maintained by this cache
 * @param <V> the type of mapped values
 */
public class LruCache<K, V> implements Cache<K, V> {

  private static final Logger logger = LoggerFactory.getLogger(LruCache.class);
  private static final float LOAD_FACTOR = 0.75f;
  private static final boolean TRACE_ENABLED = logger.isTraceEnabled();
  private static final boolean DEBUG_ENABLED = logger.isDebugEnabled();

  private final int maxSize;
  private final Map<K, V> cache;
  private final StampedLock lock;
  private final AtomicLong hits;
  private final AtomicLong misses;
  private final AtomicLong evictions;

  /**
   * Creates a new LRU cache with the specified maximum size.
   *
   * @param maxSize the maximum number of entries to store in the cache
   * @throws IllegalArgumentException if maxSize is less than 1
   */
  public LruCache(int maxSize) {
    if (maxSize < 1) {
      throw new IllegalArgumentException("maxSize must be at least 1");
    }
    this.maxSize = maxSize;
    this.lock = new StampedLock();
    this.hits = new AtomicLong(0);
    this.misses = new AtomicLong(0);
    this.evictions = new AtomicLong(0);

    // LinkedHashMap with access-order mode (true) for LRU behavior
    this.cache =
        new LinkedHashMap<K, V>(maxSize, LOAD_FACTOR, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            boolean shouldRemove = size() > LruCache.this.maxSize;
            if (shouldRemove) {
              evictions.incrementAndGet();
              if (DEBUG_ENABLED) {
                logger.debug("Evicting LRU entry: {}", eldest.getKey());
              }
            }
            return shouldRemove;
          }
        };

    logger.info("Created LRU cache with maxSize={}", maxSize);
  }

  /**
   * Creates a new LRU cache with the specified configuration.
   *
   * @param config the cache configuration
   * @throws IllegalArgumentException if config is null
   */
  public LruCache(CacheConfig config) {
    this(config.getMaxSize());
    if (config.getEvictionStrategy() != CacheConfig.EvictionStrategy.LRU) {
      logger.warn(
          "LruCache created with eviction strategy {}, expected LRU",
          config.getEvictionStrategy());
    }
  }

  @Override
  public V get(K key) {
    // Try optimistic read first (zero-cost if no writers)
    long stamp = lock.tryOptimisticRead();
    V value = cache.get(key);
    
    if (!lock.validate(stamp)) {
      // Optimistic read failed, fall back to read lock
      stamp = lock.readLock();
      try {
        value = cache.get(key);
      } finally {
        lock.unlockRead(stamp);
      }
    }
    
    // Update stats (separate from read operation)
    if (value != null) {
      hits.incrementAndGet();
      if (TRACE_ENABLED) {
        logger.trace("Cache hit for key: {}", key);
      }
    } else {
      misses.incrementAndGet();
      if (TRACE_ENABLED) {
        logger.trace("Cache miss for key: {}", key);
      }
    }
    return value;
  }

  @Override
  public void put(K key, V value) {
    if (key == null || value == null) {
      throw new IllegalArgumentException("Key and value must not be null");
    }

    long stamp = lock.writeLock();
    try {
      cache.put(key, value);
      if (TRACE_ENABLED) {
        logger.trace("Added entry to cache: key={}", key);
      }
    } finally {
      lock.unlockWrite(stamp);
    }
  }

  @Override
  public void invalidate(K key) {
    long stamp = lock.writeLock();
    try {
      V removed = cache.remove(key);
      if (removed != null && DEBUG_ENABLED) {
        logger.debug("Invalidated cache entry: {}", key);
      }
    } finally {
      lock.unlockWrite(stamp);
    }
  }

  @Override
  public void clear() {
    long stamp = lock.writeLock();
    try {
      int size = cache.size();
      cache.clear();
      hits.set(0);
      misses.set(0);
      evictions.set(0);
      logger.info("Cleared cache ({} entries removed)", size);
    } finally {
      lock.unlockWrite(stamp);
    }
  }

  @Override
  public CacheStats getStats() {
    long stamp = lock.readLock();
    try {
      return new CacheStats(hits.get(), misses.get(), evictions.get(), cache.size());
    } finally {
      lock.unlockRead(stamp);
    }
  }

  /**
   * Returns the current size of the cache.
   *
   * @return the number of entries in the cache
   */
  public int size() {
    long stamp = lock.tryOptimisticRead();
    int size = cache.size();
    if (!lock.validate(stamp)) {
      stamp = lock.readLock();
      try {
        size = cache.size();
      } finally {
        lock.unlockRead(stamp);
      }
    }
    return size;
  }

  /**
   * Returns the maximum size of the cache.
   *
   * @return the maximum number of entries
   */
  public int getMaxSize() {
    return maxSize;
  }

  /**
   * Checks if the cache contains the specified key.
   *
   * @param key the key to check
   * @return true if the cache contains the key
   */
  public boolean containsKey(K key) {
    long stamp = lock.tryOptimisticRead();
    boolean contains = cache.containsKey(key);
    if (!lock.validate(stamp)) {
      stamp = lock.readLock();
      try {
        contains = cache.containsKey(key);
      } finally {
        lock.unlockRead(stamp);
      }
    }
    return contains;
  }
}
