package fr.lirmm.jdm.cache;

/**
 * Factory for creating cache instances.
 *
 * <p>This factory provides a centralized way to create cache instances without coupling client
 * code to concrete implementations. It supports various cache strategies and optimization
 * patterns.
 *
 * <h2>Basic Usage</h2>
 * <pre>{@code
 * // Create a cache based on configuration
 * CacheConfig config = CacheConfig.builder()
 *     .maxSize(1000)
 *     .evictionStrategy(EvictionStrategy.LRU)
 *     .build();
 * Cache<String, User> cache = CacheFactory.create(config);
 * }</pre>
 *
 * <h2>Optimized Caches</h2>
 * <pre>{@code
 * // High-concurrency workload (25-200 threads)
 * Cache<String, User> cache = CacheFactory.createSharded(config);
 *
 * // Read-heavy workload (>90% reads)
 * Cache<String, User> cache = CacheFactory.createThreadLocal(config);
 * }</pre>
 */
public class CacheFactory {

  /**
   * Creates a cache instance based on the provided configuration.
   *
   * <p>The cache implementation is chosen based on the eviction strategy in the configuration:
   * <ul>
   *   <li>{@code LRU} → {@link LruCache}
   *   <li>{@code TTL} → {@link TtlCache}
   * </ul>
   *
   * @param config the cache configuration
   * @param <K> the type of keys maintained by the cache
   * @param <V> the type of mapped values
   * @return a cache instance
   * @throws IllegalArgumentException if config is null
   */
  public static <K, V> Cache<K, V> create(CacheConfig config) {
    if (config == null) {
      throw new IllegalArgumentException("Cache configuration cannot be null");
    }

    return switch (config.getEvictionStrategy()) {
      case LRU -> new LruCache<>(config);
      case TTL -> new TtlCache<>(config);
    };
  }

  /**
   * Creates a cache with default configuration (LRU eviction, 1000 entries).
   *
   * @param <K> the type of keys maintained by the cache
   * @param <V> the type of mapped values
   * @return a cache instance with default settings
   */
  public static <K, V> Cache<K, V> createDefault() {
    return create(CacheConfig.defaultConfig());
  }

  /**
   * Creates a sharded cache optimized for high-concurrency workloads.
   *
   * <p>Sharded caches distribute keys across multiple cache instances to reduce lock contention.
   * This provides significant performance improvements for workloads with:
   * <ul>
   *   <li>High write contention (many threads writing simultaneously)
   *   <li>25-200 concurrent threads
   *   <li>Mixed read/write operations
   * </ul>
   *
   * <p><b>Performance:</b> +162% to +342% throughput improvement compared to baseline
   *
   * @param config the cache configuration
   * @param <K> the type of keys maintained by the cache
   * @param <V> the type of mapped values
   * @return a sharded cache instance
   * @throws IllegalArgumentException if config is null
   * @see ShardedCache
   */
  public static <K, V> Cache<K, V> createSharded(CacheConfig config) {
    if (config == null) {
      throw new IllegalArgumentException("Cache configuration cannot be null");
    }
    return new ShardedCache<>(config);
  }

  /**
   * Creates a sharded cache with custom shard count.
   *
   * <p>Allows fine-tuning of the shard count for specific workload characteristics.
   * Default shard count is {@code CPU_CORES × 4}.
   *
   * @param config the cache configuration
   * @param shardCount the number of shards (will be rounded to next power of 2)
   * @param <K> the type of keys maintained by the cache
   * @param <V> the type of mapped values
   * @return a sharded cache instance with specified shard count
   * @throws IllegalArgumentException if config is null or shardCount is not positive
   * @see ShardedCache
   */
  public static <K, V> Cache<K, V> createSharded(CacheConfig config, int shardCount) {
    if (config == null) {
      throw new IllegalArgumentException("Cache configuration cannot be null");
    }
    return new ShardedCache<>(config, shardCount);
  }

  /**
   * Creates a thread-local cache optimized for read-heavy workloads.
   *
   * <p>Thread-local caches maintain a per-thread L1 cache in front of a shared L2 cache.
   * This provides zero-contention reads for hot data. Best suited for:
   * <ul>
   *   <li>Read-heavy workloads (>90% reads)
   *   <li>High temporal locality (threads repeatedly access same keys)
   *   <li>Thread count < 50
   * </ul>
   *
   * <p><b>Performance:</b> +145% throughput improvement for read-heavy workloads
   *
   * <p><b>Trade-offs:</b>
   * <ul>
   *   <li>Increased memory usage (each thread has its own L1 cache)
   *   <li>Potential stale data (thread-local cache may lag behind L2)
   *   <li>Not suitable for write-heavy workloads
   * </ul>
   *
   * @param config the cache configuration
   * @param <K> the type of keys maintained by the cache
   * @param <V> the type of mapped values
   * @return a thread-local cache instance
   * @throws IllegalArgumentException if config is null
   * @see ThreadLocalCache
   */
  public static <K, V> Cache<K, V> createThreadLocal(CacheConfig config) {
    if (config == null) {
      throw new IllegalArgumentException("Cache configuration cannot be null");
    }
    Cache<K, V> backingCache = create(config);
    return new ThreadLocalCache<>(backingCache);
  }

  /**
   * Creates a thread-local cache with custom L1 cache size.
   *
   * @param config the cache configuration for the L2 (backing) cache
   * @param threadLocalMaxSize the maximum size of each thread's L1 cache
   * @param <K> the type of keys maintained by the cache
   * @param <V> the type of mapped values
   * @return a thread-local cache instance with specified L1 size
   * @throws IllegalArgumentException if config is null or threadLocalMaxSize is not positive
   * @see ThreadLocalCache
   */
  public static <K, V> Cache<K, V> createThreadLocal(CacheConfig config, int threadLocalMaxSize) {
    if (config == null) {
      throw new IllegalArgumentException("Cache configuration cannot be null");
    }
    if (threadLocalMaxSize < 1) {
      throw new IllegalArgumentException("Thread-local max size must be at least 1");
    }
    Cache<K, V> backingCache = create(config);
    return new ThreadLocalCache<>(backingCache, threadLocalMaxSize);
  }

  /**
   * Creates an optimized cache based on workload characteristics.
   *
   * <p>This method automatically selects the best cache implementation based on:
   * <ul>
   *   <li>Expected thread count
   *   <li>Read/write ratio
   *   <li>Workload type
   * </ul>
   *
   * <h3>Selection Logic:</h3>
   * <pre>
   * Thread Count | Read Ratio | Selected Strategy
   * -------------|------------|------------------
   * < 10         | Any        | Baseline (LRU/TTL)
   * 10-50        | >90%       | ThreadLocal
   * 10-200       | <90%       | Sharded
   * > 200        | Any        | Sharded
   * </pre>
   *
   * @param config the cache configuration
   * @param expectedThreadCount the expected number of concurrent threads
   * @param readRatio the ratio of reads to total operations (0.0 to 1.0)
   * @param <K> the type of keys maintained by the cache
   * @param <V> the type of mapped values
   * @return an optimized cache instance
   * @throws IllegalArgumentException if config is null or parameters are out of range
   */
  public static <K, V> Cache<K, V> createOptimized(
      CacheConfig config, int expectedThreadCount, double readRatio) {
    if (config == null) {
      throw new IllegalArgumentException("Cache configuration cannot be null");
    }
    if (expectedThreadCount < 1) {
      throw new IllegalArgumentException("Expected thread count must be at least 1");
    }
    if (readRatio < 0.0 || readRatio > 1.0) {
      throw new IllegalArgumentException("Read ratio must be between 0.0 and 1.0");
    }

    // Low concurrency: use baseline cache
    if (expectedThreadCount < 10) {
      return create(config);
    }

    // Read-heavy workload with moderate concurrency: use thread-local cache
    if (expectedThreadCount <= 50 && readRatio > 0.90) {
      return createThreadLocal(config);
    }

    // High concurrency or mixed workload: use sharded cache
    return createSharded(config);
  }

  /**
   * Creates a cache optimized for high-concurrency scenarios.
   *
   * <p>Equivalent to {@link #createSharded(CacheConfig)} but with a more descriptive name.
   *
   * @param config the cache configuration
   * @param <K> the type of keys maintained by the cache
   * @param <V> the type of mapped values
   * @return a cache optimized for high concurrency
   * @throws IllegalArgumentException if config is null
   */
  public static <K, V> Cache<K, V> createHighConcurrency(CacheConfig config) {
    return createSharded(config);
  }

  /**
   * Creates a cache optimized for read-heavy scenarios.
   *
   * <p>Equivalent to {@link #createThreadLocal(CacheConfig)} but with a more descriptive name.
   *
   * @param config the cache configuration
   * @param <K> the type of keys maintained by the cache
   * @param <V> the type of mapped values
   * @return a cache optimized for read-heavy workloads
   * @throws IllegalArgumentException if config is null
   */
  public static <K, V> Cache<K, V> createReadHeavy(CacheConfig config) {
    return createThreadLocal(config);
  }

  // Private constructor to prevent instantiation
  private CacheFactory() {
    throw new UnsupportedOperationException("Utility class");
  }
}
