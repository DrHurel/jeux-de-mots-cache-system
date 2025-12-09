package fr.lirmm.jdm.cache;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A thread-safe Time-To-Live (TTL) cache implementation.
 *
 * <p>This cache evicts entries after they have existed for a specified duration. Entries are
 * checked for expiration both on access and via a background cleanup task.
 *
 * <p>Thread safety is provided through a ConcurrentHashMap for the main storage and atomic
 * counters for lock-free statistics tracking.
 *
 * @param <K> the type of keys maintained by this cache
 * @param <V> the type of mapped values
 */
public class TtlCache<K, V> implements Cache<K, V> {

  private static final Logger logger = LoggerFactory.getLogger(TtlCache.class);
  private static final int CLEANUP_DIVISOR = 2;
  private static final long MIN_CLEANUP_INTERVAL_MS = 1000L;
  private static final boolean TRACE_ENABLED = logger.isTraceEnabled();
  private static final boolean DEBUG_ENABLED = logger.isDebugEnabled();

  private final int maxSize;
  private final long ttlMillis;
  private final Map<K, CacheEntry<V>> cache;
  private final AtomicLong hits;
  private final AtomicLong misses;
  private final AtomicLong evictions;
  private final ScheduledExecutorService cleanupExecutor;

  /**
   * Creates a new TTL cache with the specified configuration.
   *
   * @param maxSize the maximum number of entries to store in the cache
   * @param ttl the time-to-live duration for cache entries
   * @throws IllegalArgumentException if maxSize is less than 1 or ttl is null/negative
   */
  public TtlCache(int maxSize, Duration ttl) {
    if (maxSize < 1) {
      throw new IllegalArgumentException("maxSize must be at least 1");
    }
    if (ttl == null || ttl.isNegative()) {
      throw new IllegalArgumentException("ttl must be non-null and non-negative");
    }

    this.maxSize = maxSize;
    this.ttlMillis = ttl.toMillis();
    this.cache = new ConcurrentHashMap<>(maxSize);
    this.hits = new AtomicLong(0);
    this.misses = new AtomicLong(0);
    this.evictions = new AtomicLong(0);

    // Background cleanup task runs every TTL/2 interval
    this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread thread = new Thread(r, "TTL-Cache-Cleanup");
      thread.setDaemon(true);
      return thread;
    });

    long cleanupIntervalMs = Math.max(ttlMillis / CLEANUP_DIVISOR, MIN_CLEANUP_INTERVAL_MS);
    cleanupExecutor.scheduleAtFixedRate(
        this::cleanupExpiredEntries, cleanupIntervalMs, cleanupIntervalMs, TimeUnit.MILLISECONDS);

    logger.info("Created TTL cache with maxSize={}, ttl={}ms", maxSize, ttlMillis);
  }

  /**
   * Creates a new TTL cache with the specified configuration.
   *
   * @param config the cache configuration
   * @throws IllegalArgumentException if config is null
   */
  public TtlCache(CacheConfig config) {
    this(config.getMaxSize(), config.getTtl());
    if (config.getEvictionStrategy() != CacheConfig.EvictionStrategy.TTL) {
      logger.warn(
          "TtlCache created with eviction strategy {}, expected TTL",
          config.getEvictionStrategy());
    }
  }

  @Override
  public V get(K key) {
    CacheEntry<V> entry = cache.get(key);

    if (entry == null) {
      misses.incrementAndGet();
      if (TRACE_ENABLED) {
        logger.trace("Cache miss for key: {}", key);
      }
      return null;
    }

    long now = System.currentTimeMillis();
    if (entry.isExpired(now)) {
      cache.remove(key);
      misses.incrementAndGet();
      evictions.incrementAndGet();
      if (TRACE_ENABLED) {
        logger.trace("Cache miss (expired) for key: {}", key);
      }
      return null;
    }

    hits.incrementAndGet();
    if (TRACE_ENABLED) {
      logger.trace("Cache hit for key: {}", key);
    }
    return entry.getValue();
  }

  @Override
  public void put(K key, V value) {
    if (key == null || value == null) {
      throw new IllegalArgumentException("Key and value must not be null");
    }

    // Enforce max size by removing oldest entry if necessary
    if (cache.size() >= maxSize && !cache.containsKey(key)) {
      evictOldestEntry();
    }

    long expiresAt = System.currentTimeMillis() + ttlMillis;
    CacheEntry<V> entry = new CacheEntry<>(value, expiresAt);
    cache.put(key, entry);
    if (TRACE_ENABLED) {
      logger.trace("Added entry to cache: key={}, expiresAt={}", key, expiresAt);
    }
  }

  @Override
  public void invalidate(K key) {
    CacheEntry<V> removed = cache.remove(key);
    if (removed != null && DEBUG_ENABLED) {
      logger.debug("Invalidated cache entry: {}", key);
    }
  }

  @Override
  public void clear() {
    int size = cache.size();
    cache.clear();
    hits.set(0);
    misses.set(0);
    evictions.set(0);
    logger.info("Cleared cache ({} entries removed)", size);
  }

  @Override
  public CacheStats getStats() {
    return new CacheStats(hits.get(), misses.get(), evictions.get(), cache.size());
  }

  /**
   * Returns the current size of the cache.
   *
   * @return the number of entries in the cache
   */
  @Override
  public int size() {
    return cache.size();
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
   * Returns the time-to-live duration.
   *
   * @return the TTL duration
   */
  public Duration getTtl() {
    return Duration.ofMillis(ttlMillis);
  }

  /**
   * Shuts down the background cleanup executor.
   *
   * <p>This method should be called when the cache is no longer needed to free up resources.
   */
  public void shutdown() {
    cleanupExecutor.shutdown();
    logger.info("TTL cache cleanup executor shut down");
  }

  private void cleanupExpiredEntries() {
    try {
      long now = System.currentTimeMillis();
      int removedCount = 0;

      for (Map.Entry<K, CacheEntry<V>> entry : cache.entrySet()) {
        if (entry.getValue().isExpired(now)) {
          if (cache.remove(entry.getKey(), entry.getValue())) {
            evictions.incrementAndGet();
            removedCount++;
          }
        }
      }

      if (removedCount > 0 && DEBUG_ENABLED) {
        logger.debug("Cleaned up {} expired entries", removedCount);
      }
    } catch (Exception e) {
      logger.error("Error during cache cleanup", e);
    }
  }

  private void evictOldestEntry() {
    // Find the entry with the earliest expiration time
    K oldestKey = null;
    long oldestExpiration = Long.MAX_VALUE;

    for (Map.Entry<K, CacheEntry<V>> entry : cache.entrySet()) {
      long expiration = entry.getValue().getExpiresAt();
      if (expiration < oldestExpiration) {
        oldestExpiration = expiration;
        oldestKey = entry.getKey();
      }
    }

    if (oldestKey != null) {
      cache.remove(oldestKey);
      evictions.incrementAndGet();
      if (DEBUG_ENABLED) {
        logger.debug("Evicted oldest entry: {}", oldestKey);
      }
    }
  }

  /**
   * A cache entry with an expiration timestamp in milliseconds.
   *
   * @param <V> the type of the cached value
   */
  private static class CacheEntry<V> {
    private final V value;
    private final long expiresAt;

    CacheEntry(V value, long expiresAt) {
      this.value = value;
      this.expiresAt = expiresAt;
    }

    V getValue() {
      return value;
    }

    long getExpiresAt() {
      return expiresAt;
    }

    boolean isExpired(long now) {
      return now > expiresAt;
    }
  }
}
