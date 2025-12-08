package fr.lirmm.jdm.example;

import java.time.Duration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.lirmm.jdm.cache.CacheStats;
import fr.lirmm.jdm.client.JdmApiException;
import fr.lirmm.jdm.client.JdmClient;
import fr.lirmm.jdm.client.model.PublicNode;
import fr.lirmm.jdm.client.model.PublicNodeType;
import fr.lirmm.jdm.client.model.PublicRelationType;
import fr.lirmm.jdm.client.model.RelationsResponse;

/**
 * Example application demonstrating the JDM Cache Client library.
 *
 * <p>This example shows:
 * <ul>
 *   <li>Creating and configuring JDM clients with different caching strategies
 *   <li>Fetching nodes, relations, and types from the JDM API
 *   <li>Measuring cache performance and hit rates
 *   <li>Comparing LRU and TTL caching strategies
 * </ul>
 */
public class JdmClientExample {
  
  private static final Logger logger = Logger.getLogger(JdmClientExample.class.getName());

  public static void main(String[] args) {
    System.out.println("=== JDM Cache Client Example ===\n");

    try {
      // Example 1: Basic usage with default configuration
      basicExample();

      // Example 2: Performance comparison
      performanceComparison();

      // Example 3: Exploring semantic relations
      exploreRelations();

    } catch (JdmApiException e) {
      logger.log(Level.SEVERE, "JDM API error: " + e.getMessage(), e);
    } catch (RuntimeException e) {
      logger.log(Level.SEVERE, "Unexpected error: " + e.getMessage(), e);
    }
  }

  /**
   * Demonstrates basic client usage with default LRU caching.
   */
  private static void basicExample() throws JdmApiException {
    System.out.println("--- Example 1: Basic Usage ---");

    JdmClient client = JdmClient.createDefault();

    // Fetch a node by name
    System.out.println("\nFetching node 'chat'...");
    PublicNode node = client.getNodeByName("chat");
    if (node != null) {
      System.out.printf("Found: %s (ID: %d, Type: %d, Weight: %d)%n",
          node.getName(), node.getId(), node.getType(), node.getWeight());
    }

    // Fetch the same node again (should hit cache)
    System.out.println("\nFetching 'chat' again (from cache)...");
    PublicNode cachedNode = client.getNodeByName("chat");
    System.out.println("Retrieved from cache: " + (cachedNode != null));

    // Display cache statistics
    CacheStats stats = client.getCacheStats();
    System.out.println("\nCache Statistics:");
    System.out.println(stats);

    System.out.println();
  }

  /**
   * Compares performance between cached and uncached requests.
   */
  private static void performanceComparison() throws JdmApiException {
    System.out.println("--- Example 2: Performance Comparison ---");

    JdmClient lruClient = JdmClient.builder()
        .lruCache(100)
        .build();

    JdmClient ttlClient = JdmClient.builder()
        .ttlCache(100, Duration.ofMinutes(5))
        .build();

    String[] words = {"chat", "chien", "animal", "maison", "voiture",
        "ordinateur", "livre", "musique", "école", "ami"};

    // Test LRU cache
    System.out.println("\nTesting LRU Cache:");
    long lruStart = System.currentTimeMillis();
    for (int i = 0; i < 50; i++) {
      String word = words[i % words.length];
      lruClient.getNodeByName(word);
    }
    long lruDuration = System.currentTimeMillis() - lruStart;

    CacheStats lruStats = lruClient.getCacheStats();
    System.out.printf("Time: %dms, Hit Rate: %.1f%%, Hits: %d, Misses: %d%n",
        lruDuration, lruStats.getHitRate() * 100,
        lruStats.getHitCount(), lruStats.getMissCount());

    // Test TTL cache
    System.out.println("\nTesting TTL Cache:");
    long ttlStart = System.currentTimeMillis();
    for (int i = 0; i < 50; i++) {
      String word = words[i % words.length];
      ttlClient.getNodeByName(word);
    }
    long ttlDuration = System.currentTimeMillis() - ttlStart;

    CacheStats ttlStats = ttlClient.getCacheStats();
    System.out.printf("Time: %dms, Hit Rate: %.1f%%, Hits: %d, Misses: %d%n",
        ttlDuration, ttlStats.getHitRate() * 100,
        ttlStats.getHitCount(), ttlStats.getMissCount());

    System.out.println();
  }

  /**
   * Demonstrates exploring semantic relations in the JDM network.
   */
  private static void exploreRelations() throws JdmApiException {
    System.out.println("--- Example 3: Exploring Semantic Relations ---");

    JdmClient client = JdmClient.builder()
        .lruCache(200)
        .build();

    // Get node types
    System.out.println("\nFetching node types...");
    List<PublicNodeType> nodeTypes = client.getNodeTypes();
    System.out.printf("Found %d node types:%n", nodeTypes.size());
    nodeTypes.stream()
        .limit(5)
        .forEach(type -> System.out.printf("  - %s (ID: %d)%n", type.getName(), type.getId()));

    // Get relation types
    System.out.println("\nFetching relation types...");
    List<PublicRelationType> relationTypes = client.getRelationTypes();
    System.out.printf("Found %d relation types:%n", relationTypes.size());
    relationTypes.stream()
        .limit(5)
        .forEach(type -> System.out.printf("  - %s (ID: %d)%n", type.getName(), type.getId()));

    // Explore relations from a word
    System.out.println("\nExploring relations from 'chat'...");
    RelationsResponse relations = client.getRelationsFrom("chat");
    System.out.printf("Found %d relations and %d related nodes%n",
        relations.getRelations().size(),
        relations.getNodes().size());

    // Display some related nodes
    System.out.println("\nRelated nodes:");
    relations.getNodes().stream()
        .limit(10)
        .forEach(node -> System.out.printf("  - %s (ID: %d, Weight: %d)%n",
            node.getName(), node.getId(), node.getWeight()));

    // Get refinements
    System.out.println("\nGetting refinements for 'animal'...");
    List<PublicNode> refinements = client.getRefinements("animal");
    System.out.printf("Found %d refinements:%n", refinements.size());
    refinements.stream()
        .limit(5)
        .forEach(node -> System.out.printf("  - %s (ID: %d)%n", node.getName(), node.getId()));

    // Final cache statistics
    System.out.println("\nFinal Cache Statistics:");
    System.out.println(client.getCacheStats());

    System.out.println();
  }
}
