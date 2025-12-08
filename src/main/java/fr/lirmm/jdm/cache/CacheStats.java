package fr.lirmm.jdm.cache;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Immutable statistics about cache performance.
 *
 * <p>This class tracks cache hits, misses, and calculates the hit rate for monitoring cache
 * effectiveness.
 */
public class CacheStats {

  private final long hitCount;
  private final long missCount;
  private final long evictionCount;
  private final long size;

  /**
   * Creates a new CacheStats instance.
   *
   * @param hitCount the number of cache hits
   * @param missCount the number of cache misses
   * @param evictionCount the number of evictions
   * @param size the current cache size
   */
  public CacheStats(long hitCount, long missCount, long evictionCount, long size) {
    this.hitCount = hitCount;
    this.missCount = missCount;
    this.evictionCount = evictionCount;
    this.size = size;
  }

  /**
   * Returns the number of cache hits.
   *
   * @return the hit count
   */
  public long getHitCount() {
    return hitCount;
  }

  /**
   * Returns the number of cache misses.
   *
   * @return the miss count
   */
  public long getMissCount() {
    return missCount;
  }

  /**
   * Returns the number of evictions.
   *
   * @return the eviction count
   */
  public long getEvictionCount() {
    return evictionCount;
  }

  /**
   * Returns the current cache size.
   *
   * @return the size
   */
  public long getSize() {
    return size;
  }

  /**
   * Returns the total number of requests (hits + misses).
   *
   * @return the total request count
   */
  public long getRequestCount() {
    return hitCount + missCount;
  }

  /**
   * Returns the cache hit rate as a value between 0.0 and 1.0.
   *
   * @return the hit rate, or 0.0 if there have been no requests
   */
  public double getHitRate() {
    long requests = getRequestCount();
    return requests == 0 ? 0.0 : (double) hitCount / requests;
  }

  /**
   * Returns the cache miss rate as a value between 0.0 and 1.0.
   *
   * @return the miss rate, or 0.0 if there have been no requests
   */
  public double getMissRate() {
    return 1.0 - getHitRate();
  }

  @Override
  public String toString() {
    return String.format(
        "CacheStats{hits=%d, misses=%d, hitRate=%.2f%%, evictions=%d, size=%d}",
        hitCount, missCount, getHitRate() * 100, evictionCount, size);
  }

  /** Builder for thread-safe cache statistics tracking. */
  public static class Builder {
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final AtomicLong evictionCount = new AtomicLong();

    /** Increments the hit count. */
    public void recordHit() {
      hitCount.incrementAndGet();
    }

    /** Increments the miss count. */
    public void recordMiss() {
      missCount.incrementAndGet();
    }

    /** Increments the eviction count. */
    public void recordEviction() {
      evictionCount.incrementAndGet();
    }

    /**
     * Builds an immutable CacheStats snapshot.
     *
     * @param currentSize the current cache size
     * @return a new CacheStats instance
     */
    public CacheStats build(long currentSize) {
      return new CacheStats(hitCount.get(), missCount.get(), evictionCount.get(), currentSize);
    }

    /** Resets all statistics to zero. */
    public void reset() {
      hitCount.set(0);
      missCount.set(0);
      evictionCount.set(0);
    }
  }
}
