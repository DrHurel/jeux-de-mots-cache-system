package fr.lirmm.jdm.cache;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for LruCache. */
class LruCacheTest {

  private LruCache<String, String> cache;

  @BeforeEach
  void setUp() {
    cache = new LruCache<>(3);
  }

  @Test
  void testPutAndGet() {
    cache.put("key1", "value1");
    assertEquals("value1", cache.get("key1"));
  }

  @Test
  void testGetMiss() {
    assertNull(cache.get("nonexistent"));
  }

  @Test
  void testLruEviction() {
    cache.put("key1", "value1");
    cache.put("key2", "value2");
    cache.put("key3", "value3");

    // Cache is now full (maxSize=3)
    assertEquals(3, cache.size());

    // Access key1 to make it recently used
    cache.get("key1");

    // Add key4, should evict key2 (least recently used)
    cache.put("key4", "value4");

    assertEquals(3, cache.size());
    assertNotNull(cache.get("key1"));
    assertNull(cache.get("key2")); // Evicted
    assertNotNull(cache.get("key3"));
    assertNotNull(cache.get("key4"));
  }

  @Test
  void testInvalidate() {
    cache.put("key1", "value1");
    cache.invalidate("key1");
    assertNull(cache.get("key1"));
  }

  @Test
  void testClear() {
    cache.put("key1", "value1");
    cache.put("key2", "value2");
    cache.clear();
    assertEquals(0, cache.size());
    assertNull(cache.get("key1"));
    assertNull(cache.get("key2"));
  }

  @Test
  void testStats() {
    cache.put("key1", "value1");
    cache.get("key1"); // Hit
    cache.get("key2"); // Miss

    CacheStats stats = cache.getStats();
    assertEquals(1, stats.getHitCount());
    assertEquals(1, stats.getMissCount());
    assertEquals(0.5, stats.getHitRate(), 0.01);
  }

  @Test
  void testConcurrentAccess() throws InterruptedException {
    int threadCount = 10;
    int operationsPerThread = 100;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
      final int threadId = i;
      executor.submit(
          () -> {
            try {
              for (int j = 0; j < operationsPerThread; j++) {
                String key = "key-" + (j % 10);
                String value = "value-" + threadId + "-" + j;
                cache.put(key, value);
                cache.get(key);
              }
            } finally {
              latch.countDown();
            }
          });
    }

    assertTrue(latch.await(10, TimeUnit.SECONDS));
    executor.shutdown();

    // Verify cache is still in valid state
    assertTrue(cache.size() <= cache.getMaxSize());
    CacheStats stats = cache.getStats();
    assertTrue(stats.getRequestCount() > 0);
  }

  @Test
  void testNullKeyThrows() {
    assertThrows(IllegalArgumentException.class, () -> cache.put(null, "value"));
  }

  @Test
  void testNullValueThrows() {
    assertThrows(IllegalArgumentException.class, () -> cache.put("key", null));
  }

  @Test
  void testContainsKey() {
    cache.put("key1", "value1");
    assertTrue(cache.containsKey("key1"));
    assertFalse(cache.containsKey("key2"));
  }

  @Test
  void testUpdateExistingKey() {
    cache.put("key1", "value1");
    cache.put("key1", "value2");
    assertEquals("value2", cache.get("key1"));
    assertEquals(1, cache.size());
  }

  @Test
  void testPerformance() {
    int iterations = 10000;
    long startTime = System.nanoTime();

    for (int i = 0; i < iterations; i++) {
      cache.put("key" + i, "value" + i);
    }

    for (int i = 0; i < iterations; i++) {
      cache.get("key" + i);
    }

    long endTime = System.nanoTime();
    long durationMs = (endTime - startTime) / 1_000_000;

    // Should complete in reasonable time (< 1 second for 10k operations)
    assertTrue(durationMs < 1000, "Operations took too long: " + durationMs + "ms");
  }

  @Test
  void testEvictionCount() {
    cache.put("key1", "value1");
    cache.put("key2", "value2");
    cache.put("key3", "value3");
    cache.put("key4", "value4"); // Triggers eviction

    CacheStats stats = cache.getStats();
    assertTrue(stats.getEvictionCount() > 0);
  }
}
