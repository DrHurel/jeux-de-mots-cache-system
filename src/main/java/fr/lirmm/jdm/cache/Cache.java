package fr.lirmm.jdm.cache;

/**
 * A generic cache interface that provides basic caching operations.
 *
 * <p>This interface defines the minimal public API for a thread-safe cache implementation with
 * support for storing key-value pairs and tracking cache statistics.
 *
 * @param <K> the type of keys maintained by this cache
 * @param <V> the type of mapped values
 */
public interface Cache<K, V> {

  /**
   * Returns the value associated with the given key, or null if the key is not in the cache.
   *
   * @param key the key whose associated value is to be returned
   * @return the value associated with the key, or null if not present
   */
  V get(K key);

  /**
   * Associates the specified value with the specified key in this cache.
   *
   * <p>If the cache previously contained a mapping for the key, the old value is replaced.
   *
   * @param key the key with which the specified value is to be associated
   * @param value the value to be associated with the specified key
   */
  void put(K key, V value);

  /**
   * Removes the mapping for a key from this cache if it is present.
   *
   * @param key the key whose mapping is to be removed from the cache
   */
  void invalidate(K key);

  /** Removes all entries from the cache. */
  void clear();

  /**
   * Returns the current number of entries in the cache.
   *
   * @return the number of entries currently stored in the cache
   */
  int size();

  /**
   * Returns the current statistics for this cache.
   *
   * @return a CacheStats object containing hit/miss counts and other metrics
   */
  CacheStats getStats();
}
