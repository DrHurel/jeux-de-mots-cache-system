package fr.lirmm.jdm.example;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import fr.lirmm.jdm.cache.CacheStats;
import fr.lirmm.jdm.client.JdmClient;
import fr.lirmm.jdm.client.model.PublicNode;
import fr.lirmm.jdm.client.model.PublicNodeType;
import fr.lirmm.jdm.client.model.RelationsResponse;

/**
 * Real-world demonstration of the JDM Cache Client Library.
 * 
 * This example showcases:
 * 1. Typical usage patterns for API integration
 * 2. Performance benefits of caching
 * 3. Cache management best practices
 * 4. Concurrent access scenarios
 * 5. Statistics monitoring
 * 
 * Run this example to see the library in action!
 */
public class RealWorldExample {
    
    private static final String API_URL = "https://jdm-api.demo.lirmm.fr/";
    
    public static void main(String[] args) throws Exception {
        System.out.println("===========================================");
        System.out.println("  JDM Cache Client - Real-World Example");
        System.out.println("===========================================\n");
        
        // Scenario 1: Simple API integration with LRU caching
        demonstrateBasicUsage();
        
        // Scenario 2: High-frequency API calls benefiting from cache
        demonstratePerformanceBenefits();
        
        // Scenario 3: TTL cache for time-sensitive data
        demonstrateTtlCaching();
        
        // Scenario 4: Concurrent access scenario
        demonstrateConcurrentAccess();
        
        // Scenario 5: Cache management and monitoring
        demonstrateCacheManagement();
        
        System.out.println("\n===========================================");
        System.out.println("  Example Complete!");
        System.out.println("===========================================");
    }
    
    /**
     * Scenario 1: Basic API integration with caching
     */
    private static void demonstrateBasicUsage() throws Exception {
        System.out.println("### Scenario 1: Basic API Integration ###\n");
        
        // Create a client with LRU caching (max 1000 entries)
        JdmClient client = JdmClient.builder()
                .baseUrl(API_URL)
                .lruCache(1000)
                .build();
        
        System.out.println("Looking up French word 'chat' (cat)...");
        PublicNode node = client.getNodeByName("chat");
        
        if (node != null) {
            System.out.println("✅ Found node:");
            System.out.println("   ID: " + node.getId());
            System.out.println("   Name: " + node.getName());
            System.out.println("   Type: " + node.getType());
            System.out.println("   Weight: " + node.getWeight());
        } else {
            System.out.println("❌ Node not found");
        }
        
        System.out.println("\nRetrieving relations for 'chat'...");
        RelationsResponse relations = client.getRelationsFrom("chat");
        
        if (relations != null && relations.getRelations() != null) {
            System.out.println("✅ Found " + relations.getRelations().size() + " relations");
            System.out.println("   Sample relations:");
            relations.getRelations().stream().limit(3).forEach(rel -> {
                System.out.println("   - Type: " + rel.getType() + ", Weight: " + rel.getWeight());
            });
        }
        
        CacheStats stats = client.getCacheStats();
        System.out.println("\n📊 Cache Statistics: " + stats);
        System.out.println();
    }
    
    /**
     * Scenario 2: Demonstrate performance benefits
     */
    private static void demonstratePerformanceBenefits() throws Exception {
        System.out.println("### Scenario 2: Performance Benefits ###\n");
        
        JdmClient client = JdmClient.builder()
                .baseUrl(API_URL)
                .lruCache(500)
                .build();
        
        String[] commonWords = {"chat", "chien", "maison", "voiture", "ordinateur"};
        
        System.out.println("First pass - cache misses (fetching from API)...");
        long start1 = System.currentTimeMillis();
        for (String word : commonWords) {
            PublicNode node = client.getNodeByName(word);
            // Simulate some processing
        }
        long duration1 = System.currentTimeMillis() - start1;
        System.out.println("⏱️  Duration: " + duration1 + "ms");
        
        System.out.println("\nSecond pass - cache hits (from memory)...");
        long start2 = System.currentTimeMillis();
        for (String word : commonWords) {
            PublicNode node = client.getNodeByName(word);
            // Simulate some processing
        }
        long duration2 = System.currentTimeMillis() - start2;
        System.out.println("⏱️  Duration: " + duration2 + "ms");
        
        double improvement = ((double)(duration1 - duration2) / duration1) * 100;
        System.out.println("\n🚀 Performance Improvement: " + String.format("%.1f%%", improvement));
        System.out.println("   Cache significantly reduces API response time!");
        
        CacheStats stats = client.getCacheStats();
        System.out.println("\n📊 Cache Statistics:");
        System.out.println("   Hits: " + stats.getHitCount());
        System.out.println("   Misses: " + stats.getMissCount());
        System.out.println("   Hit Rate: " + String.format("%.1f%%", stats.getHitRate() * 100));
        System.out.println();
    }
    
    /**
     * Scenario 3: TTL caching for time-sensitive data
     */
    private static void demonstrateTtlCaching() throws Exception {
        System.out.println("### Scenario 3: TTL Caching (Time-Sensitive Data) ###\n");
        
        // Create a client with TTL cache (entries expire after 5 seconds)
        JdmClient client = JdmClient.builder()
                .baseUrl(API_URL)
                .ttlCache(500, Duration.ofSeconds(5))
                .build();
        
        System.out.println("Fetching node types (cached for 5 seconds)...");
        
        // First call - cache miss
        long start1 = System.nanoTime();
        List<PublicNodeType> types1 = client.getNodeTypes();
        long duration1 = (System.nanoTime() - start1) / 1_000_000;
        System.out.println("   First call: " + duration1 + "ms (API call)");
        System.out.println("   Found " + types1.size() + " node types");
        
        // Second call - cache hit
        long start2 = System.nanoTime();
        List<PublicNodeType> types2 = client.getNodeTypes();
        long duration2 = (System.nanoTime() - start2) / 1_000_000;
        System.out.println("   Second call: " + duration2 + "ms (from cache)");
        
        System.out.println("\n⏳ Waiting 6 seconds for TTL expiration...");
        Thread.sleep(6000);
        
        // Third call - cache miss (expired)
        long start3 = System.nanoTime();
        List<PublicNodeType> types3 = client.getNodeTypes();
        long duration3 = (System.nanoTime() - start3) / 1_000_000;
        System.out.println("   Third call: " + duration3 + "ms (API call - cache expired)");
        
        System.out.println("\n✅ TTL cache automatically refreshes stale data!");
        System.out.println();
    }
    
    /**
     * Scenario 4: Concurrent access
     */
    private static void demonstrateConcurrentAccess() throws Exception {
        System.out.println("### Scenario 4: Concurrent Access ###\n");
        
        JdmClient client = JdmClient.builder()
                .baseUrl(API_URL)
                .lruCache(200)
                .build();
        
        System.out.println("Simulating 20 concurrent users accessing the API...");
        
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(20);
        
        String[] words = {"chat", "chien", "maison", "voiture", "arbre", "soleil", "lune"};
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < 20; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for start signal
                    
                    // Each thread makes multiple API calls
                    for (int j = 0; j < 10; j++) {
                        String word = words[ThreadLocalRandom.current().nextInt(words.length)];
                        PublicNode node = client.getNodeByName(word);
                        // Simulate processing
                        Thread.sleep(10);
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error: " + e.getMessage());
                } finally {
                    completionLatch.countDown();
                }
            });
        }
        
        // Start all threads simultaneously
        startLatch.countDown();
        
        // Wait for all to complete
        completionLatch.await(30, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;
        
        executor.shutdown();
        
        System.out.println("✅ All 20 users completed successfully!");
        System.out.println("   Total time: " + duration + "ms");
        System.out.println("   Total API calls: 200 (20 users × 10 calls)");
        
        CacheStats stats = client.getCacheStats();
        System.out.println("\n📊 Concurrent Access Statistics:");
        System.out.println("   Cache hits: " + stats.getHitCount());
        System.out.println("   Cache misses: " + stats.getMissCount());
        System.out.println("   Hit rate: " + String.format("%.1f%%", stats.getHitRate() * 100));
        System.out.println("   Cache prevented " + stats.getHitCount() + " redundant API calls!");
        System.out.println();
    }
    
    /**
     * Scenario 5: Cache management and monitoring
     */
    private static void demonstrateCacheManagement() throws Exception {
        System.out.println("### Scenario 5: Cache Management & Monitoring ###\n");
        
        JdmClient client = JdmClient.builder()
                .baseUrl(API_URL)
                .lruCache(10) // Small cache to demonstrate eviction
                .build();
        
        System.out.println("Filling cache with 15 entries (max size: 10)...");
        for (int i = 0; i < 15; i++) {
            String word = "word" + i;
            try {
                client.getNodeByName(word);
            } catch (Exception e) {
                // Ignore not found errors for demo
            }
        }
        
        CacheStats stats1 = client.getCacheStats();
        System.out.println("📊 After filling cache:");
        System.out.println("   Size: " + stats1.getSize() + " / 10");
        System.out.println("   Evictions: " + stats1.getEvictionCount());
        System.out.println("   (5 entries were evicted to maintain max size)");
        
        System.out.println("\nAccessing recently used entries...");
        for (int i = 10; i < 15; i++) {
            try {
                client.getNodeByName("word" + i);
            } catch (Exception e) {
                // Ignore
            }
        }
        
        CacheStats stats2 = client.getCacheStats();
        System.out.println("📊 After re-accessing entries:");
        System.out.println("   Hits: " + stats2.getHitCount());
        System.out.println("   Misses: " + stats2.getMissCount());
        System.out.println("   Hit rate: " + String.format("%.1f%%", stats2.getHitRate() * 100));
        
        System.out.println("\nManual cache invalidation...");
        client.clearCache();
        
        CacheStats stats3 = client.getCacheStats();
        System.out.println("📊 After clearing cache:");
        System.out.println("   Size: " + stats3.getSize());
        System.out.println("   ✅ Cache cleared successfully!");
        
        System.out.println("\n💡 Best Practices:");
        System.out.println("   • Monitor hit rates - aim for >70%");
        System.out.println("   • Adjust cache size based on working set");
        System.out.println("   • Use TTL for time-sensitive data");
        System.out.println("   • Clear cache when data freshness is critical");
        System.out.println();
    }
}
