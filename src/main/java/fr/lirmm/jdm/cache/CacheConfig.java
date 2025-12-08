package fr.lirmm.jdm.cache;

import java.time.Duration;

/**
 * Configuration for cache implementations.
 *
 * <p>This class provides configuration options for cache behavior including maximum size, eviction
 * strategy, and time-to-live settings.
 */
public class CacheConfig {

  /** Default maximum cache size. */
  public static final int DEFAULT_MAX_SIZE = 1000;

  /** Default time-to-live duration (5 minutes). */
  public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

  private final int maxSize;
  private final Duration ttl;
  private final EvictionStrategy evictionStrategy;

  private CacheConfig(Builder builder) {
    this.maxSize = builder.maxSize;
    this.ttl = builder.ttl;
    this.evictionStrategy = builder.evictionStrategy;
  }

  /**
   * Returns the maximum cache size.
   *
   * @return the max size
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
    return ttl;
  }

  /**
   * Returns the eviction strategy.
   *
   * @return the eviction strategy
   */
  public EvictionStrategy getEvictionStrategy() {
    return evictionStrategy;
  }

  /**
   * Creates a new builder for CacheConfig.
   *
   * @return a new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Creates a default configuration with LRU eviction strategy.
   *
   * @return a default CacheConfig instance
   */
  public static CacheConfig defaultConfig() {
    return builder().build();
  }

  /** Eviction strategies supported by the cache. */
  public enum EvictionStrategy {
    /** Least Recently Used - evicts the least recently accessed entry. */
    LRU,
    /** Time-To-Live - evicts entries after a specified duration. */
    TTL
  }

  /** Builder for CacheConfig. */
  public static class Builder {
    private int maxSize = DEFAULT_MAX_SIZE;
    private Duration ttl = DEFAULT_TTL;
    private EvictionStrategy evictionStrategy = EvictionStrategy.LRU;

    /**
     * Sets the maximum cache size.
     *
     * @param maxSize the maximum number of entries
     * @return this builder
     * @throws IllegalArgumentException if maxSize is less than 1
     */
    public Builder maxSize(int maxSize) {
      if (maxSize < 1) {
        throw new IllegalArgumentException("maxSize must be at least 1");
      }
      this.maxSize = maxSize;
      return this;
    }

    /**
     * Sets the time-to-live duration.
     *
     * @param ttl the TTL duration
     * @return this builder
     * @throws IllegalArgumentException if ttl is null or negative
     */
    public Builder ttl(Duration ttl) {
      if (ttl == null || ttl.isNegative()) {
        throw new IllegalArgumentException("ttl must be non-null and non-negative");
      }
      this.ttl = ttl;
      return this;
    }

    /**
     * Sets the eviction strategy.
     *
     * @param strategy the eviction strategy
     * @return this builder
     * @throws IllegalArgumentException if strategy is null
     */
    public Builder evictionStrategy(EvictionStrategy strategy) {
      if (strategy == null) {
        throw new IllegalArgumentException("evictionStrategy cannot be null");
      }
      this.evictionStrategy = strategy;
      return this;
    }

    /**
     * Builds the CacheConfig instance.
     *
     * @return a new CacheConfig
     */
    public CacheConfig build() {
      return new CacheConfig(this);
    }
  }
}
