package fr.lirmm.jdm.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** Unit tests for CacheStats. */
class CacheStatsTest {

  @Test
  void testBasicStats() {
    CacheStats stats = new CacheStats(10, 5, 2, 100);

    assertEquals(10, stats.getHitCount());
    assertEquals(5, stats.getMissCount());
    assertEquals(2, stats.getEvictionCount());
    assertEquals(100, stats.getSize());
    assertEquals(15, stats.getRequestCount());
  }

  @Test
  void testHitRate() {
    CacheStats stats = new CacheStats(8, 2, 0, 50);
    assertEquals(0.8, stats.getHitRate(), 0.001);
  }

  @Test
  void testMissRate() {
    CacheStats stats = new CacheStats(7, 3, 0, 50);
    assertEquals(0.3, stats.getMissRate(), 0.001);
  }

  @Test
  void testZeroRequests() {
    CacheStats stats = new CacheStats(0, 0, 0, 0);
    assertEquals(0.0, stats.getHitRate());
    assertEquals(1.0, stats.getMissRate()); // Miss rate is 1.0 - 0.0 = 1.0
  }

  @Test
  void testBuilder() {
    CacheStats.Builder builder = new CacheStats.Builder();

    builder.recordHit();
    builder.recordHit();
    builder.recordMiss();
    builder.recordEviction();

    CacheStats stats = builder.build(5);

    assertEquals(2, stats.getHitCount());
    assertEquals(1, stats.getMissCount());
    assertEquals(1, stats.getEvictionCount());
    assertEquals(5, stats.getSize());
  }

  @Test
  void testBuilderReset() {
    CacheStats.Builder builder = new CacheStats.Builder();

    builder.recordHit();
    builder.recordMiss();
    builder.reset();

    CacheStats stats = builder.build(0);

    assertEquals(0, stats.getHitCount());
    assertEquals(0, stats.getMissCount());
  }

  @Test
  void testToString() {
    CacheStats stats = new CacheStats(80, 20, 5, 100);
    String str = stats.toString();

    assertTrue(str.contains("hits=80"));
    assertTrue(str.contains("misses=20"));
    assertTrue(str.contains("hitRate=80"));
    assertTrue(str.contains("evictions=5"));
    assertTrue(str.contains("size=100"));
  }

  @Test
  void testBuilderThreadSafety() throws InterruptedException {
    CacheStats.Builder builder = new CacheStats.Builder();
    int threadCount = 10;
    int incrementsPerThread = 1000;

    Thread[] threads = new Thread[threadCount];
    for (int i = 0; i < threadCount; i++) {
      threads[i] =
          new Thread(
              () -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                  builder.recordHit();
                  builder.recordMiss();
                }
              });
      threads[i].start();
    }

    for (Thread thread : threads) {
      thread.join();
    }

    CacheStats stats = builder.build(0);
    assertEquals(threadCount * incrementsPerThread, stats.getHitCount());
    assertEquals(threadCount * incrementsPerThread, stats.getMissCount());
  }
}
