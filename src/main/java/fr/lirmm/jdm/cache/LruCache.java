package fr.lirmm.jdm.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A thread-safe Least Recently Used (LRU) cache implementation.
 *
 * <p>This cache evicts the least recently used entry when the maximum size is reached. All
 * operations are O(1) time complexity thanks to the LinkedHashMap's access-order mode.
 *
 * <p>Thread safety is provided through a ReadWriteLock, allowing multiple concurrent readers but
 * exclusive writes.
 *
 * @param <K> the type of keys maintained by this cache
 * @param <V> the type of mapped values
 */
public class LruCache<K, V> implements Cache<K, V> {

  private static final Logger logger = LoggerFactory.getLogger(LruCache.class);
  private static final float LOAD_FACTOR = 0.75f;

  private final int maxSize;
  private final Map<K, V> cache;
  private final ReadWriteLock lock;
  private final CacheStats.Builder statsBuilder;

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
    this.lock = new ReentrantReadWriteLock();
    this.statsBuilder = new CacheStats.Builder();

    // LinkedHashMap with access-order mode (true) for LRU behavior
    this.cache =
        new LinkedHashMap<K, V>(maxSize, LOAD_FACTOR, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            boolean shouldRemove = size() > LruCache.this.maxSize;
            if (shouldRemove) {
              statsBuilder.recordEviction();
              logger.debug("Evicting LRU entry: {}", eldest.getKey());
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
    lock.readLock().lock();
    try {
      V value = cache.get(key);
      if (value != null) {
        statsBuilder.recordHit();
        logger.trace("Cache hit for key: {}", key);
      } else {
        statsBuilder.recordMiss();
        logger.trace("Cache miss for key: {}", key);
      }
      return value;
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public void put(K key, V value) {
    if (key == null || value == null) {
      throw new IllegalArgumentException("Key and value must not be null");
    }

    lock.writeLock().lock();
    try {
      cache.put(key, value);
      logger.trace("Added entry to cache: key={}", key);
    } finally {
      lock.writeLock().unlock();
    }
  }

  @Override
  public void invalidate(K key) {
    lock.writeLock().lock();
    try {
      V removed = cache.remove(key);
      if (removed != null) {
        logger.debug("Invalidated cache entry: {}", key);
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  @Override
  public void clear() {
    lock.writeLock().lock();
    try {
      int size = cache.size();
      cache.clear();
      statsBuilder.reset();
      logger.info("Cleared cache ({} entries removed)", size);
    } finally {
      lock.writeLock().unlock();
    }
  }

  @Override
  public CacheStats getStats() {
    lock.readLock().lock();
    try {
      return statsBuilder.build(cache.size());
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Returns the current size of the cache.
   *
   * @return the number of entries in the cache
   */
  public int size() {
    lock.readLock().lock();
    try {
      return cache.size();
    } finally {
      lock.readLock().unlock();
    }
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
    lock.readLock().lock();
    try {
      return cache.containsKey(key);
    } finally {
      lock.readLock().unlock();
    }
  }
}
