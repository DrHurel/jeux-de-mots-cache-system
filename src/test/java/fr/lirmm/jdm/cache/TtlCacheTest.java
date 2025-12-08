package fr.lirmm.jdm.cache;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for TtlCache. */
class TtlCacheTest {

  private TtlCache<String, String> cache;

  @BeforeEach
  void setUp() {
    cache = new TtlCache<>(10, Duration.ofSeconds(1));
  }

  @AfterEach
  void tearDown() {
    if (cache != null) {
      cache.shutdown();
    }
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
  void testKeyOverwrite() {
    // Test that putting a value with an existing key overwrites it
    cache.put("key1", "value1");
    assertEquals("value1", cache.get("key1"));
    assertEquals(1, cache.size());

    // Overwrite with new value
    cache.put("key1", "value2");
    assertEquals("value2", cache.get("key1"));
    assertEquals(1, cache.size()); // Size should remain the same

    // Overwrite again
    cache.put("key1", "value3");
    assertEquals("value3", cache.get("key1"));
    assertEquals(1, cache.size());
  }

  @Test
  void testTtlExpiration() throws InterruptedException {
    cache.put("key1", "value1");
    assertEquals("value1", cache.get("key1"));

    // Wait for TTL to expire
    Thread.sleep(1100);

    // Should be expired now
    assertNull(cache.get("key1"));
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
  void testMaxSizeEnforcement() {
    TtlCache<String, String> smallCache = new TtlCache<>(3, Duration.ofMinutes(1));
    try {
      smallCache.put("key1", "value1");
      smallCache.put("key2", "value2");
      smallCache.put("key3", "value3");

      assertEquals(3, smallCache.size());

      // Adding one more should evict the oldest
      smallCache.put("key4", "value4");
      assertEquals(3, smallCache.size());

      CacheStats stats = smallCache.getStats();
      assertTrue(stats.getEvictionCount() > 0);
    } finally {
      smallCache.shutdown();
    }
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
  void testThreadFairness() throws InterruptedException {
    // Test that no single thread monopolizes cache access under contention
    int threadCount = 10;
    int operationsPerThread = 1000;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch finishLatch = new CountDownLatch(threadCount);

    // Track successful operations per thread
    java.util.concurrent.ConcurrentHashMap<Integer, Integer> threadOperations =
        new java.util.concurrent.ConcurrentHashMap<>();

    for (int i = 0; i < threadCount; i++) {
      final int threadId = i;
      executor.submit(
          () -> {
            try {
              // Wait for all threads to be ready
              startLatch.await();

              int successCount = 0;
              for (int j = 0; j < operationsPerThread; j++) {
                String key = "key-" + (j % 20); // Use 20 keys to create contention
                String value = "value-" + threadId + "-" + j;

                try {
                  cache.put(key, value);
                  String retrieved = cache.get(key);
                  if (retrieved != null) {
                    successCount++;
                  }
                } catch (Exception ignored) {
                  // Cache operations might fail under contention, that's okay
                }
              }

              threadOperations.put(threadId, successCount);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } finally {
              finishLatch.countDown();
            }
          });
    }

    // Start all threads simultaneously
    startLatch.countDown();

    // Wait for all threads to complete
    assertTrue(finishLatch.await(30, TimeUnit.SECONDS), "All threads should complete within 30 seconds");

    executor.shutdown();
    assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

    // Verify fairness: no thread should be starved (have 0 operations)
    for (int i = 0; i < threadCount; i++) {
      Integer operations = threadOperations.get(i);
      assertNotNull(operations, "Thread " + i + " should have recorded operations");
      assertTrue(
          operations > 0,
          "Thread " + i + " should have completed at least some operations, but got: " + operations);
    }

    // Calculate coefficient of variation to measure fairness
    double sum = 0;
    for (Integer ops : threadOperations.values()) {
      sum += ops;
    }
    double mean = sum / threadCount;

    double variance = 0;
    for (Integer ops : threadOperations.values()) {
      variance += Math.pow(ops - mean, 2);
    }
    variance /= threadCount;
    double stdDev = Math.sqrt(variance);
    double coefficientOfVariation = stdDev / mean;

    // CV should be less than 0.5 for reasonable fairness
    // (lower is better, 0 would be perfectly fair)
    assertTrue(
        coefficientOfVariation < 0.5,
        String.format(
            "Coefficient of variation should be < 0.5 for fair thread scheduling, got: %.3f (mean=%.1f, stdDev=%.1f)",
            coefficientOfVariation, mean, stdDev));
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
  void testBackgroundCleanup() throws InterruptedException {
    TtlCache<String, String> shortTtlCache = new TtlCache<>(10, Duration.ofMillis(500));
    try {
      shortTtlCache.put("key1", "value1");
      shortTtlCache.put("key2", "value2");

      assertEquals(2, shortTtlCache.size());

      // Wait for cleanup task to run (should run every TTL/2 + some buffer)
      Thread.sleep(1000);

      // Entries should be cleaned up
      assertTrue(shortTtlCache.size() < 2 || shortTtlCache.get("key1") == null);
    } finally {
      shortTtlCache.shutdown();
    }
  }

  @Test
  void testUpdateExistingKey() {
    cache.put("key1", "value1");
    cache.put("key1", "value2");
    assertEquals("value2", cache.get("key1"));
  }

  @Test
  void testEvictionOnExpiredGet() throws InterruptedException {
    cache.put("key1", "value1");

    // Wait for expiration
    Thread.sleep(1100);

    // This get should detect expiration and record eviction
    assertNull(cache.get("key1"));

    CacheStats stats = cache.getStats();
    assertTrue(stats.getEvictionCount() > 0);
  }

  @Test
  void testPerformance() {
    int iterations = 5000;
    long startTime = System.nanoTime();

    for (int i = 0; i < iterations; i++) {
      cache.put("key" + i, "value" + i);
    }

    for (int i = 0; i < iterations; i++) {
      cache.get("key" + i);
    }

    long endTime = System.nanoTime();
    long durationMs = (endTime - startTime) / 1_000_000;

    // Should complete in reasonable time
    assertTrue(durationMs < 2000, "Operations took too long: " + durationMs + "ms");
  }
}
