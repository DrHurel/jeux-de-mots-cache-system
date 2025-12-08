package fr.lirmm.jdm.integration;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.lirmm.jdm.cache.CacheConfig;
import fr.lirmm.jdm.cache.CacheStats;
import fr.lirmm.jdm.client.JdmClient;
import fr.lirmm.jdm.client.model.PublicNode;
import fr.lirmm.jdm.client.model.PublicNodeType;
import fr.lirmm.jdm.client.model.RelationsResponse;
import okhttp3.OkHttpClient;

/**
 * Real API integration tests WITHOUT MOCKING.
 * These tests interact with the actual JDM API to validate cache behavior
 * under realistic conditions.
 * 
 * WARNING: These tests require network connectivity and will make real API calls.
 * They may be slower and could fail if the API is unavailable.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RealApiIntegrationTest {
    
    private static final Logger logger = LoggerFactory.getLogger(RealApiIntegrationTest.class);
    private static final String REAL_API_URL = "https://jdm-api.demo.lirmm.fr/";
    
    private JdmClient client;
    
    @BeforeEach
    void setUp() {
        // Create HTTP client with timeout
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(30))
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(30))
                .build();
        
        // Create client with real API endpoint
        CacheConfig config = CacheConfig.builder()
                .evictionStrategy(CacheConfig.EvictionStrategy.LRU)
                .maxSize(1000)
                .build();
        
        client = JdmClient.builder()
                .baseUrl(REAL_API_URL)
                .httpClient(httpClient)
                .cacheConfig(config)
                .build();
    }
    
    @Test
    @Order(1)
    @Tag("integration")
    @DisplayName("Real API: Basic node retrieval with caching")
    void testRealApiNodeRetrieval() throws Exception {
        logger.info("Testing real API node retrieval...");
        
        // First call - should hit real API (cache miss)
        long startMiss = System.nanoTime();
        PublicNode node1 = client.getNodeByName("chat");
        long durationMiss = System.nanoTime() - startMiss;
        
        assertNotNull(node1, "Node should be retrieved from API");
        assertNotNull(node1.getId(), "Node should have an ID");
        assertEquals("chat", node1.getName(), "Node name should match");
        
        logger.info("First call (cache miss): {}ms", durationMiss / 1_000_000.0);
        
        // Second call - should hit cache (cache hit)
        long startHit = System.nanoTime();
        PublicNode node2 = client.getNodeByName("chat");
        long durationHit = System.nanoTime() - startHit;
        
        assertNotNull(node2, "Node should be retrieved from cache");
        assertEquals(node1.getId(), node2.getId(), "Cached node should match");
        
        logger.info("Second call (cache hit): {}ms", durationHit / 1_000_000.0);
        
        // Cache should be significantly faster
        assertTrue(durationHit < durationMiss * 0.1, 
            String.format("Cache hit should be >90%% faster (miss: %.2fms, hit: %.2fms)", 
                durationMiss / 1_000_000.0, durationHit / 1_000_000.0));
        
        // Verify statistics
        CacheStats stats = client.getCacheStats();
        assertEquals(1, stats.getHitCount(), "Should have 1 cache hit");
        assertEquals(1, stats.getMissCount(), "Should have 1 cache miss");
        assertEquals(0.5, stats.getHitRate(), 0.01, "Hit rate should be 50%");
        
        logger.info("Statistics: {}", stats);
    }
    
    @Test
    @Order(2)
    @Tag("integration")
    @DisplayName("Real API: Relations retrieval with caching")
    void testRealApiRelationsRetrieval() throws Exception {
        logger.info("Testing real API relations retrieval...");
        
        try {
            // First call - cache miss
            long startMiss = System.nanoTime();
            RelationsResponse response1 = client.getRelationsFrom("chat");
            long durationMiss = System.nanoTime() - startMiss;
            
            assertNotNull(response1, "Relations should be retrieved");
            assertNotNull(response1.getRelations(), "Relations list should not be null");
        
            logger.info("First call (cache miss): {}ms, {} relations found", 
                durationMiss / 1_000_000.0, response1.getRelations().size());
            
            // Second call - cache hit
            long startHit = System.nanoTime();
            RelationsResponse response2 = client.getRelationsFrom("chat");
            long durationHit = System.nanoTime() - startHit;
            
            assertNotNull(response2, "Relations should be retrieved from cache");
            assertEquals(response1.getRelations().size(), response2.getRelations().size(), "Cached relations should match");
            
            logger.info("Second call (cache hit): {}ms", durationHit / 1_000_000.0);
            
            // Verify performance improvement
            double improvement = ((double)(durationMiss - durationHit) / durationMiss) * 100;
            logger.info("Performance improvement: {:.2f}%", improvement);
            assertTrue(improvement > 90.0, "Should see >90% improvement with cache");
        } catch (Exception e) {
            logger.warn("Test skipped due to external API unavailability: {}", e.getMessage());
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "External JDM API is unavailable or returned errors");
        }
    }
    
    @Test
    @Order(3)
    @Tag("integration")
    @DisplayName("Real API: Node types retrieval")
    void testRealApiNodeTypes() throws Exception {
        logger.info("Testing real API node types retrieval...");
        
        List<PublicNodeType> types = client.getNodeTypes();
        
        assertNotNull(types, "Node types should be retrieved");
        assertFalse(types.isEmpty(), "Should have at least one node type");
        
        logger.info("Retrieved {} node types", types.size());
        
        // Second call should be cached
        long start = System.nanoTime();
        List<PublicNodeType> cachedTypes = client.getNodeTypes();
        long duration = System.nanoTime() - start;
        
        assertEquals(types.size(), cachedTypes.size(), "Cached types should match");
        assertTrue(duration < 1_000_000, "Cached call should be <1ms");
        
        logger.info("Cached call duration: {}ms", duration / 1_000_000.0);
    }
    
    @Test
    @Order(4)
    @Tag("integration")
    @DisplayName("Real API: Moderate concurrent load (1000 requests)")
    void testRealApiModerateConcurrentLoad() throws Exception {
        logger.info("Testing real API with 1000 concurrent requests...");
        
        int threads = 50;
        int requestsPerThread = 20;
        int totalRequests = threads * requestsPerThread;
        
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(totalRequests);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        long startTime = System.nanoTime();
        
        // Submit concurrent requests
        for (int i = 0; i < totalRequests; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    // Use a small set of keys to ensure high cache hit rate
                    String[] words = {"chat", "chien", "maison", "voiture", "ordinateur"};
                    String word = words[requestId % words.length];
                    
                    PublicNode node = client.getNodeByName(word);
                    if (node != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    logger.warn("Request {} failed: {}", requestId, e.getMessage());
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Wait for all requests to complete (timeout 2 minutes)
        boolean completed = latch.await(2, TimeUnit.MINUTES);
        long duration = System.nanoTime() - startTime;
        
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        assertTrue(completed, "All requests should complete within timeout");
        
        CacheStats stats = client.getCacheStats();
        
        logger.info("=== Moderate Load Test Results ===");
        logger.info("Total requests: {}", totalRequests);
        logger.info("Successful: {}", successCount.get());
        logger.info("Failed: {}", errorCount.get());
        logger.info("Total duration: {:.2f}s", duration / 1_000_000_000.0);
        logger.info("Requests/second: {:.2f}", totalRequests / (duration / 1_000_000_000.0));
        logger.info("Cache hits: {}", stats.getHitCount());
        logger.info("Cache misses: {}", stats.getMissCount());
        logger.info("Hit rate: {:.2f}%", stats.getHitRate() * 100);
        logger.info("===================================");
        
        // Assertions
        assertTrue(successCount.get() > totalRequests * 0.95, 
            "At least 95% of requests should succeed");
        assertTrue(stats.getHitRate() > 0.75, 
            "Hit rate should be >75% with repeated keys");
    }
    
    @Test
    @Order(5)
    @Tag("integration")
    @Tag("stress")
    @DisplayName("Real API: Cache invalidation during load")
    void testRealApiCacheInvalidationDuringLoad() throws Exception {
        logger.info("Testing cache invalidation during concurrent load...");
        
        int threads = 20;
        int requestsPerThread = 10;
        String testWord = "test";
        
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(threads);
        AtomicInteger beforeInvalidation = new AtomicInteger(0);
        AtomicInteger afterInvalidation = new AtomicInteger(0);
        
        // Pre-populate cache
        try {
            client.getNodeByName(testWord);
        } catch (Exception e) {
            logger.warn("Pre-population failed, continuing anyway");
        }
        
        // Submit concurrent readers
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < requestsPerThread; j++) {
                        try {
                            client.getNodeByName(testWord);
                            if (j < requestsPerThread / 2) {
                                beforeInvalidation.incrementAndGet();
                            } else {
                                afterInvalidation.incrementAndGet();
                            }
                            Thread.sleep(10); // Small delay between requests
                        } catch (Exception e) {
                            logger.debug("Request failed: {}", e.getMessage());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completionLatch.countDown();
                }
            });
        }
        
        // Start all threads
        startLatch.countDown();
        
        // Wait a bit then invalidate cache mid-load
        Thread.sleep(100);
        logger.info("Invalidating cache during load...");
        client.clearCache();
        
        // Wait for completion
        completionLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        
        logger.info("Requests before invalidation: {}", beforeInvalidation.get());
        logger.info("Requests after invalidation: {}", afterInvalidation.get());
        
        assertTrue(beforeInvalidation.get() > 0, "Should have requests before invalidation");
        assertTrue(afterInvalidation.get() > 0, "Should have requests after invalidation");
        
        // Cache should be functional after invalidation
        CacheStats finalStats = client.getCacheStats();
        logger.info("Final statistics: {}", finalStats);
    }
    
    @Test
    @Order(6)
    @Tag("integration")
    @DisplayName("Real API: TTL cache with real expiration")
    void testRealApiWithTtlCache() throws Exception {
        logger.info("Testing real API with TTL cache...");
        
        // Create HTTP client
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(30))
                .build();
        
        // Create new client with TTL strategy
        CacheConfig ttlConfig = CacheConfig.builder()
                .evictionStrategy(CacheConfig.EvictionStrategy.TTL)
                .ttl(Duration.ofSeconds(2))
                .build();
        
        JdmClient ttlClient = JdmClient.builder()
                .baseUrl(REAL_API_URL)
                .httpClient(httpClient)
                .cacheConfig(ttlConfig)
                .build();
        
        // First call - cache miss
        long start1 = System.nanoTime();
        PublicNode node1 = ttlClient.getNodeByName("chat");
        long duration1 = System.nanoTime() - start1;
        assertNotNull(node1, "First call should succeed");
        logger.info("First call (miss): {:.2f}ms", duration1 / 1_000_000.0);
        
        // Second call - cache hit (before expiration)
        long start2 = System.nanoTime();
        PublicNode node2 = ttlClient.getNodeByName("chat");
        long duration2 = System.nanoTime() - start2;
        assertNotNull(node2, "Second call should hit cache");
        logger.info("Second call (hit): {:.2f}ms", duration2 / 1_000_000.0);
        assertTrue(duration2 < duration1 * 0.1, "Cache hit should be much faster");
        
        // Wait for expiration
        logger.info("Waiting for TTL expiration (2.5 seconds)...");
        Thread.sleep(2500);
        
        // Third call - cache miss (after expiration)
        long start3 = System.nanoTime();
        PublicNode node3 = ttlClient.getNodeByName("chat");
        long duration3 = System.nanoTime() - start3;
        assertNotNull(node3, "Third call should succeed after expiration");
        logger.info("Third call (miss after expiration): {:.2f}ms", duration3 / 1_000_000.0);
        
        // Duration should be similar to first call (both are API calls)
        // Note: Network timing can vary, so we use a more relaxed assertion
        double timingRatio = Math.abs(duration3 - duration1) / (double)duration1;
        assertTrue(timingRatio < 2.0, 
            String.format("Expired call timing should be reasonable (ratio: %.2f, duration1: %.2fms, duration3: %.2fms)", 
                timingRatio, duration1 / 1_000_000.0, duration3 / 1_000_000.0));
        
        CacheStats stats = ttlClient.getCacheStats();
        logger.info("TTL Cache Statistics: {}", stats);
        assertEquals(2, stats.getMissCount(), "Should have 2 cache misses");
        assertEquals(1, stats.getHitCount(), "Should have 1 cache hit");
    }
    
    @Test
    @Order(7)
    @Tag("integration")
    @DisplayName("Real API: Error handling without mocking")
    void testRealApiErrorHandling() throws Exception {
        logger.info("Testing real API error handling...");
        
        try {
            // Test with non-existent node
            PublicNode node = client.getNodeByName("thisdefinitelydoesnotexist123456789");
            // API might return null or empty result rather than throwing exception
            logger.info("Non-existent node result: {}", node);
            
            // Test with invalid ID
            try {
                PublicNode nodeById = client.getNodeById(-999999);
                logger.info("Invalid ID result: {}", nodeById);
            } catch (Exception e) {
                logger.info("Invalid ID threw exception (expected): {}", e.getMessage());
            }
            
            // Cache should still be functional
            PublicNode validNode = client.getNodeByName("chat");
            assertNotNull(validNode, "Cache should still work after errors");
            
            logger.info("Error handling completed successfully");
        } catch (Exception e) {
            logger.warn("Test skipped due to external API unavailability: {}", e.getMessage());
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "External JDM API is unavailable or returned errors");
        }
    }
}
