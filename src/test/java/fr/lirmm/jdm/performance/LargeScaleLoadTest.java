package fr.lirmm.jdm.performance;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.lirmm.jdm.cache.Cache;
import fr.lirmm.jdm.cache.CacheConfig;
import fr.lirmm.jdm.cache.CacheStats;
import fr.lirmm.jdm.cache.LruCache;
import fr.lirmm.jdm.cache.TtlCache;

/**
 * Large-scale load testing with 100,000+ concurrent requests.
 * These tests validate cache behavior under realistic high-load conditions.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LargeScaleLoadTest {
    
    private static final Logger logger = LoggerFactory.getLogger(LargeScaleLoadTest.class);
    
    @Test
    @Order(1)
    @Tag("performance")
    @Tag("stress")
    @DisplayName("Stress Test: 100,000 concurrent requests on LRU cache")
    void testLruCache100kConcurrentRequests() throws Exception {
        logger.info("=== Starting 100K Concurrent Requests Test (LRU) ===");
        
        CacheConfig config = CacheConfig.builder()
                .evictionStrategy(CacheConfig.EvictionStrategy.LRU)
                .maxSize(10000)
                .build();
        
        Cache<String, String> cache = new LruCache<>(config);
        
        int totalRequests = 100_000;
        int threads = 500;
        int requestsPerThread = totalRequests / threads;
        
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(threads);
        
        AtomicInteger completedOperations = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);
        AtomicLong totalResponseTime = new AtomicLong(0);
        
        // Prepare workload
        logger.info("Preparing workload: {} threads × {} requests = {} total", 
            threads, requestsPerThread, totalRequests);
        
        // Submit all worker threads
        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    // Wait for start signal
                    startLatch.await();
                    
                    // Execute requests
                    for (int i = 0; i < requestsPerThread; i++) {
                        try {
                            long opStart = System.nanoTime();
                            
                            // Use realistic key distribution (Zipf-like)
                            // 20% of keys account for 80% of requests
                            String key;
                            int rand = ThreadLocalRandom.current().nextInt(100);
                            if (rand < 80) {
                                // Hot keys (20% of keyspace)
                                key = "hot-key-" + ThreadLocalRandom.current().nextInt(2000);
                            } else {
                                // Cold keys (80% of keyspace)
                                key = "cold-key-" + ThreadLocalRandom.current().nextInt(8000);
                            }
                            
                            String value = "value-" + threadId + "-" + i;
                            
                            // Mix of operations: 70% reads, 30% writes
                            if (ThreadLocalRandom.current().nextInt(100) < 70) {
                                cache.get(key);
                            } else {
                                cache.put(key, value);
                            }
                            
                            long opDuration = System.nanoTime() - opStart;
                            totalResponseTime.addAndGet(opDuration);
                            completedOperations.incrementAndGet();
                            
                        } catch (Exception e) {
                            errors.incrementAndGet();
                            logger.debug("Operation error: {}", e.getMessage());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errors.incrementAndGet();
                } finally {
                    completionLatch.countDown();
                }
            });
        }
        
        // Start all threads simultaneously
        logger.info("Starting load test...");
        long startTime = System.nanoTime();
        startLatch.countDown();
        
        // Wait for completion with timeout
        boolean completed = completionLatch.await(5, TimeUnit.MINUTES);
        long endTime = System.nanoTime();
        long totalDuration = endTime - startTime;
        
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        
        // Calculate metrics
        int completed_ops = completedOperations.get();
        int error_count = errors.get();
        double durationSeconds = totalDuration / 1_000_000_000.0;
        double throughput = completed_ops / durationSeconds;
        double avgResponseTime = (totalResponseTime.get() / (double)completed_ops) / 1_000_000.0; // ms
        
        CacheStats stats = cache.getStats();
        
        logger.info("=== 100K Load Test Results (LRU) ===");
        logger.info("Total requests: {}", totalRequests);
        logger.info("Completed operations: {}", completed_ops);
        logger.info("Errors: {}", error_count);
        logger.info("Success rate: {:.2f}%", (completed_ops / (double)totalRequests) * 100);
        logger.info("Total duration: {:.2f}s", durationSeconds);
        logger.info("Throughput: {:.2f} ops/sec", throughput);
        logger.info("Average response time: {:.4f}ms", avgResponseTime);
        logger.info("Cache hits: {}", stats.getHitCount());
        logger.info("Cache misses: {}", stats.getMissCount());
        logger.info("Hit rate: {:.2f}%", stats.getHitRate() * 100);
        logger.info("Cache size: {}", stats.getSize());
        logger.info("=====================================");
        
        // Assertions
        assertTrue(completed, "All operations should complete within timeout");
        assertTrue(completed_ops > totalRequests * 0.95, 
            "At least 95% of operations should succeed");
        assertTrue(throughput > 10_000, 
            "Throughput should exceed 10,000 ops/sec");
        assertTrue(avgResponseTime < 1.0, 
            "Average response time should be < 1ms");
        assertTrue(stats.getHitRate() > 0.5, 
            "Hit rate should be > 50% with hot keys");
    }
    
    @Test
    @Order(2)
    @Tag("performance")
    @Tag("stress")
    @DisplayName("Stress Test: 100,000 concurrent requests on TTL cache")
    void testTtlCache100kConcurrentRequests() throws Exception {
        logger.info("=== Starting 100K Concurrent Requests Test (TTL) ===");
        
        CacheConfig config = CacheConfig.builder()
                .evictionStrategy(CacheConfig.EvictionStrategy.TTL)
                .maxSize(10000)
                .ttl(Duration.ofMinutes(5))
                .build();
        
        Cache<String, String> cache = new TtlCache<>(config);
        
        int totalRequests = 100_000;
        int threads = 500;
        int requestsPerThread = totalRequests / threads;
        
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(threads);
        
        AtomicInteger completedOperations = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);
        AtomicLong totalResponseTime = new AtomicLong(0);
        
        logger.info("Preparing TTL cache workload: {} threads × {} requests = {} total", 
            threads, requestsPerThread, totalRequests);
        
        // Submit all worker threads
        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int i = 0; i < requestsPerThread; i++) {
                        try {
                            long opStart = System.nanoTime();
                            
                            // Zipf-like distribution
                            String key;
                            int rand = ThreadLocalRandom.current().nextInt(100);
                            if (rand < 80) {
                                key = "hot-" + ThreadLocalRandom.current().nextInt(2000);
                            } else {
                                key = "cold-" + ThreadLocalRandom.current().nextInt(8000);
                            }
                            
                            String value = "val-" + threadId + "-" + i;
                            
                            // 70% reads, 30% writes
                            if (ThreadLocalRandom.current().nextInt(100) < 70) {
                                cache.get(key);
                            } else {
                                cache.put(key, value);
                            }
                            
                            long opDuration = System.nanoTime() - opStart;
                            totalResponseTime.addAndGet(opDuration);
                            completedOperations.incrementAndGet();
                            
                        } catch (Exception e) {
                            errors.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errors.incrementAndGet();
                } finally {
                    completionLatch.countDown();
                }
            });
        }
        
        // Start load test
        logger.info("Starting TTL load test...");
        long startTime = System.nanoTime();
        startLatch.countDown();
        
        boolean completed = completionLatch.await(5, TimeUnit.MINUTES);
        long endTime = System.nanoTime();
        long totalDuration = endTime - startTime;
        
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        
        // Cleanup TTL cache
        if (cache instanceof AutoCloseable closeable) {
            closeable.close();
        }
        
        // Calculate metrics
        int completed_ops = completedOperations.get();
        int error_count = errors.get();
        double durationSeconds = totalDuration / 1_000_000_000.0;
        double throughput = completed_ops / durationSeconds;
        double avgResponseTime = (totalResponseTime.get() / (double)completed_ops) / 1_000_000.0;
        
        CacheStats stats = cache.getStats();
        
        logger.info("=== 100K Load Test Results (TTL) ===");
        logger.info("Total requests: {}", totalRequests);
        logger.info("Completed operations: {}", completed_ops);
        logger.info("Errors: {}", error_count);
        logger.info("Success rate: {:.2f}%", (completed_ops / (double)totalRequests) * 100);
        logger.info("Total duration: {:.2f}s", durationSeconds);
        logger.info("Throughput: {:.2f} ops/sec", throughput);
        logger.info("Average response time: {:.4f}ms", avgResponseTime);
        logger.info("Cache hits: {}", stats.getHitCount());
        logger.info("Cache misses: {}", stats.getMissCount());
        logger.info("Hit rate: {:.2f}%", stats.getHitRate() * 100);
        logger.info("Cache size: {}", stats.getSize());
        logger.info("=====================================");
        
        // Assertions
        assertTrue(completed, "All operations should complete within timeout");
        assertTrue(completed_ops > totalRequests * 0.95, 
            "At least 95% should succeed");
        assertTrue(throughput > 10_000, 
            "Throughput should exceed 10,000 ops/sec");
        assertTrue(avgResponseTime < 1.0, 
            "Average response time should be < 1ms");
    }
    
    @Test
    @Order(3)
    @Tag("performance")
    @Tag("stress")
    @DisplayName("Stress Test: Sustained load with 200K requests over time")
    void testSustainedLoad200kRequests() throws Exception {
        logger.info("=== Starting Sustained Load Test (200K requests) ===");
        
        Cache<String, String> cache = new LruCache<>(
            CacheConfig.builder()
                .evictionStrategy(CacheConfig.EvictionStrategy.LRU)
                .maxSize(5000)
                .build()
        );
        
        int totalRequests = 200_000;
        int threads = 100;
        int requestsPerThread = totalRequests / threads;
        
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch completionLatch = new CountDownLatch(threads);
        
        AtomicInteger completedOperations = new AtomicInteger(0);
        AtomicLong[] latencyBuckets = new AtomicLong[10];
        for (int i = 0; i < latencyBuckets.length; i++) {
            latencyBuckets[i] = new AtomicLong(0);
        }
        
        long startTime = System.nanoTime();
        
        // Submit sustained workload
        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < requestsPerThread; i++) {
                        long opStart = System.nanoTime();
                        
                        String key = "sustained-key-" + ThreadLocalRandom.current().nextInt(1000);
                        String value = "value-" + i;
                        
                        if (i % 3 == 0) {
                            cache.put(key, value);
                        } else {
                            cache.get(key);
                        }
                        
                        long duration = (System.nanoTime() - opStart) / 1_000_000; // ms
                        
                        // Categorize latency
                        if (duration < 0.1) latencyBuckets[0].incrementAndGet();
                        else if (duration < 0.5) latencyBuckets[1].incrementAndGet();
                        else if (duration < 1.0) latencyBuckets[2].incrementAndGet();
                        else if (duration < 2.0) latencyBuckets[3].incrementAndGet();
                        else if (duration < 5.0) latencyBuckets[4].incrementAndGet();
                        else if (duration < 10.0) latencyBuckets[5].incrementAndGet();
                        else if (duration < 50.0) latencyBuckets[6].incrementAndGet();
                        else if (duration < 100.0) latencyBuckets[7].incrementAndGet();
                        else if (duration < 500.0) latencyBuckets[8].incrementAndGet();
                        else latencyBuckets[9].incrementAndGet();
                        
                        completedOperations.incrementAndGet();
                        
                        // Small delay to simulate sustained load
                        if (i % 100 == 0) {
                            Thread.sleep(1);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completionLatch.countDown();
                }
            });
        }
        
        boolean completed = completionLatch.await(10, TimeUnit.MINUTES);
        long endTime = System.nanoTime();
        
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        
        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
        CacheStats stats = cache.getStats();
        
        logger.info("=== Sustained Load Test Results (200K) ===");
        logger.info("Total duration: {:.2f}s", durationSeconds);
        logger.info("Completed operations: {}", completedOperations.get());
        logger.info("Throughput: {:.2f} ops/sec", completedOperations.get() / durationSeconds);
        logger.info("");
        logger.info("Latency Distribution:");
        logger.info("  <0.1ms:  {} ops ({:.1f}%)", latencyBuckets[0].get(), 
            100.0 * latencyBuckets[0].get() / totalRequests);
        logger.info("  <0.5ms:  {} ops ({:.1f}%)", latencyBuckets[1].get(), 
            100.0 * latencyBuckets[1].get() / totalRequests);
        logger.info("  <1.0ms:  {} ops ({:.1f}%)", latencyBuckets[2].get(), 
            100.0 * latencyBuckets[2].get() / totalRequests);
        logger.info("  <2.0ms:  {} ops ({:.1f}%)", latencyBuckets[3].get(), 
            100.0 * latencyBuckets[3].get() / totalRequests);
        logger.info("  <5.0ms:  {} ops ({:.1f}%)", latencyBuckets[4].get(), 
            100.0 * latencyBuckets[4].get() / totalRequests);
        logger.info("  <10ms:   {} ops ({:.1f}%)", latencyBuckets[5].get(), 
            100.0 * latencyBuckets[5].get() / totalRequests);
        logger.info("  <50ms:   {} ops ({:.1f}%)", latencyBuckets[6].get(), 
            100.0 * latencyBuckets[6].get() / totalRequests);
        logger.info("  <100ms:  {} ops ({:.1f}%)", latencyBuckets[7].get(), 
            100.0 * latencyBuckets[7].get() / totalRequests);
        logger.info("  <500ms:  {} ops ({:.1f}%)", latencyBuckets[8].get(), 
            100.0 * latencyBuckets[8].get() / totalRequests);
        logger.info("  >=500ms: {} ops ({:.1f}%)", latencyBuckets[9].get(), 
            100.0 * latencyBuckets[9].get() / totalRequests);
        logger.info("");
        logger.info("Cache Statistics: {}", stats);
        logger.info("==========================================");
        
        assertTrue(completed, "Should complete within timeout");
        assertTrue(completedOperations.get() > totalRequests * 0.99, 
            "Should complete >99% of operations");
        
        // 95% of operations should be under 10ms
        long under10ms = 0;
        for (int i = 0; i <= 5; i++) {
            under10ms += latencyBuckets[i].get();
        }
        assertTrue(under10ms > totalRequests * 0.95, 
            "95% of operations should complete in <10ms");
    }
}
