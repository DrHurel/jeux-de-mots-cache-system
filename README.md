# JDM API Cache Client Library

A high-performance Java client library for the [Jeux de Mots (JDM) API](https://jdm-api.demo.lirmm.fr) with intelligent caching capabilities.

## Features

- ✅ **Full JDM API Support**: Complete coverage of the JDM semantic network API endpoints
- ⚡ **High-Performance Caching**: Up to **342% throughput improvement** with optimized implementations
- 🔒 **Thread-Safe**: Handles up to 10,000+ concurrent requests safely
- 🎯 **Multiple Caching Strategies**: 
  - **LRU (Least Recently Used)**: O(1) eviction based on access patterns
  - **TTL (Time-To-Live)**: Automatic expiration after configurable duration
  - **ShardedCache**: +342% throughput for high concurrency (10-200 threads)
  - **ThreadLocalCache**: +145% throughput for read-heavy workloads
- 📊 **Real-Time Metrics**: Track cache hits, misses, evictions, and success rates
- 🛠️ **Configurable**: Customize cache size, eviction policy, and TTL
- 📝 **Well-Documented**: Comprehensive Javadoc and [optimization guide](OPTIMIZATION_GUIDE.md)
- ✨ **Java 21**: Modern Java features and best practices

## Requirements

- Java 21 or higher
- Maven 3.8+

## Installation

Add this dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>fr.lirmm.jdm</groupId>
    <artifactId>jdm-cache-client</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Quick Start

### Basic Usage

```java
import fr.lirmm.jdm.client.JdmClient;
import fr.lirmm.jdm.client.model.PublicNode;

public class Example {
    public static void main(String[] args) throws Exception {
        // Create a client with default LRU caching
        JdmClient client = JdmClient.createDefault();
        
        // Fetch a node by name
        PublicNode node = client.getNodeByName("chat");
        System.out.println("Node: " + node.getName() + " (ID: " + node.getId() + ")");
        
        // View cache statistics
        System.out.println("Cache stats: " + client.getCacheStats());
    }
}
```

### Cache Factory Pattern (Recommended)

The `CacheFactory` provides the easiest way to create optimized caches:

```java
import fr.lirmm.jdm.cache.Cache;
import fr.lirmm.jdm.cache.CacheFactory;
import fr.lirmm.jdm.cache.CacheConfig;

// Simple creation - factory chooses implementation based on config
CacheConfig config = new CacheConfig(1000, EvictionStrategy.LRU);
Cache<String, String> cache = CacheFactory.create(config);

// Automatic optimization based on workload characteristics
Cache<String, String> optimizedCache = CacheFactory.createOptimized(
    config,
    50,    // Expected 50 concurrent threads
    0.90   // 90% read ratio (read-heavy workload)
);
// Returns ThreadLocalCache automatically for this read-heavy scenario

// Explicit optimization for high concurrency (+342% throughput)
Cache<String, String> highConcurrency = CacheFactory.createHighConcurrency(config);
// Returns ShardedCache with optimal shard count

// Explicit optimization for read-heavy workloads (+145% throughput)
Cache<String, String> readHeavy = CacheFactory.createReadHeavy(config);
// Returns ThreadLocalCache with L1/L2 hierarchy

// For thread pools, use try-with-resources for automatic cleanup
try (var cache = CacheFactory.createReadHeavy(config)) {
    cache.put("key", "value");
    // ... use cache ...
} // Automatic ThreadLocal cleanup
```

### Advanced Configuration

```java
import fr.lirmm.jdm.cache.CacheConfig;
import fr.lirmm.jdm.client.JdmClient;
import java.time.Duration;

// LRU Cache with custom size
JdmClient lruClient = JdmClient.builder()
    .lruCache(5000)  // Cache up to 5000 entries
    .build();

// TTL Cache with time-based expiration
JdmClient ttlClient = JdmClient.builder()
    .ttlCache(1000, Duration.ofMinutes(10))  // Expire after 10 minutes
    .build();

// Full configuration
CacheConfig config = CacheConfig.builder()
    .maxSize(2000)
    .ttl(Duration.ofMinutes(5))
    .evictionStrategy(CacheConfig.EvictionStrategy.LRU)
    .build();

JdmClient client = JdmClient.builder()
    .cacheConfig(config)
    .build();
```

## API Examples

### Retrieve Node Information

```java
// Get node by ID
PublicNode node = client.getNodeById(123);

// Get node by name
PublicNode node = client.getNodeByName("chien");

// Get refinements
List<PublicNode> refinements = client.getRefinements("animal");
```

### Explore Relations

```java
// Get all relations from a node
RelationsResponse relations = client.getRelationsFrom("chat");
System.out.println("Found " + relations.getRelations().size() + " relations");

// Get relations between two nodes
RelationsResponse response = client.getRelationsFromTo("chat", "animal");

// Get relations to a node
RelationsResponse incoming = client.getRelationsTo("mammifère");
```

### Query Types

```java
// Get all node types
List<PublicNodeType> nodeTypes = client.getNodeTypes();

// Get all relation types
List<PublicRelationType> relationTypes = client.getRelationTypes();
```

## Cache Management

### View Cache Statistics

```java
CacheStats stats = client.getCacheStats();
System.out.printf("""
    Cache Statistics:
    - Hit Count: %d
    - Miss Count: %d
    - Hit Rate: %.2f%%
    - Evictions: %d
    - Current Size: %d
    """,
    stats.getHitCount(),
    stats.getMissCount(),
    stats.getHitRate() * 100,
    stats.getEvictionCount(),
    stats.getSize()
);
```

### Clear Cache

```java
// Clear entire cache
client.clearCache();

// Invalidate specific entry
client.invalidateCacheEntry("node:name:chat");
```

## Performance Benchmarks

Based on our tests with 10,000 requests:

| Metric                | Without Cache | With LRU Cache | Improvement |
| --------------------- | ------------- | -------------- | ----------- |
| Average Response Time | 120ms         | 2ms            | **98.3%**   |
| Throughput (req/s)    | 83            | 5000+          | **60x**     |
| Cache Hit Rate        | N/A           | 85%            | -           |

## Architecture

### Cache Factory Pattern

The `CacheFactory` provides a unified interface for creating caches, decoupling client code from concrete implementations:

```java
// Factory methods available:
CacheFactory.create(config)                              // Basic factory
CacheFactory.createDefault()                             // Default LRU cache
CacheFactory.createSharded(config)                       // High concurrency
CacheFactory.createSharded(config, shardCount)          // Custom shards
CacheFactory.createThreadLocal(config)                   // Read-heavy
CacheFactory.createThreadLocal(config, l1Size)          // Custom L1 size
CacheFactory.createOptimized(config, threads, readRatio) // Automatic selection
CacheFactory.createHighConcurrency(config)              // Alias for sharded
CacheFactory.createReadHeavy(config)                    // Alias for thread-local
```

**Automatic Optimization Logic** (`createOptimized`):
- High concurrency (≥20 threads) + write-heavy (<80% reads) → `ShardedCache`
- Read-heavy (≥80% reads) → `ThreadLocalCache`
- Otherwise → Standard cache based on eviction strategy

### Cache Implementations

#### LRU Cache
- **Algorithm**: LinkedHashMap with access-order
- **Complexity**: O(1) for get, put, and invalidate
- **Thread Safety**: ReadWriteLock for concurrent access
- **Best For**: Frequently accessed data with predictable patterns
- **Usage**: Default choice for general-purpose caching

#### TTL Cache
- **Algorithm**: ConcurrentHashMap with timestamp tracking
- **Complexity**: O(1) for get and put, O(n) for cleanup
- **Thread Safety**: ConcurrentHashMap + ReadWriteLock for stats
- **Best For**: Time-sensitive data with automatic expiration
- **Background Cleanup**: Runs every TTL/2 interval

#### Sharded Cache (+342% Throughput)
- **Algorithm**: Hash-based sharding with multiple cache instances
- **Complexity**: O(1) for all operations
- **Thread Safety**: Lock-free distribution across shards
- **Best For**: High-concurrency write-heavy workloads (10-200 threads)
- **Benchmark**: 342% throughput improvement at 50 threads
- **Configuration**: Auto-sized shards (2× thread count, max 64)

#### ThreadLocal Cache (+145% Throughput)
- **Algorithm**: L1 (thread-local) + L2 (shared) hierarchy
- **Complexity**: O(1) for reads from L1, O(1) for L2 fallback
- **Thread Safety**: Lock-free reads, synchronized writes to L2
- **Best For**: Read-heavy workloads (>80% reads)
- **Benchmark**: 145% throughput improvement for 90% read ratio
- **Resource Management**: Implements `AutoCloseable` for thread pool cleanup

### Cache Interface

All cache implementations share a common interface:

```java
public interface Cache<K, V> {
    V get(K key);
    void put(K key, V value);
    void invalidate(K key);
    void clear();
    CacheStats getStats();
    int size();  // Get current number of entries
}
```

## Testing

Run all tests:

```bash
mvn test
```

Run with coverage:

```bash
mvn test jacoco:report
```

### Test Coverage

- **Unit Tests**: Cache implementations, statistics, configuration
- **Integration Tests**: JDM API client with mock server
- **Concurrency Tests**: 10+ threads, 1000+ operations per thread
- **Performance Tests**: Benchmark cache vs. no-cache scenarios

## Performance Optimization

This library includes multiple high-performance cache implementations. See the **[Optimization Guide](OPTIMIZATION_GUIDE.md)** for:

- **Benchmark Results**: Comprehensive performance comparisons
- **Decision Matrix**: Choose the right strategy for your workload
- **Usage Examples**: Production-ready code samples
- **Best Practices**: Tuning and monitoring recommendations

### Quick Recommendations

| Concurrency   | Workload Type     | Recommended Strategy | Improvement        |
| ------------- | ----------------- | -------------------- | ------------------ |
| 10-50 threads | Any               | **ShardedCache**     | +162% to +342%     |
| Any           | Read-heavy (>90%) | **ThreadLocalCache** | +145%              |
| <10 threads   | Low load          | **Baseline LRU**     | Simple & effective |

**Example: High-Concurrency Setup**

```java
// Optimal for 50+ concurrent threads
CacheConfig config = CacheConfig.builder()
    .maxSize(10_000)
    .ttl(Duration.ofMinutes(15))
    .evictionStrategy(EvictionStrategy.LRU)
    .build();

Cache<String, Data> cache = new ShardedCache<>(config);
// Achieves ~2.1M ops/sec vs 820K baseline
```

## Contributing

1. Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
2. Write comprehensive unit tests
3. Document all public APIs with Javadoc
4. Ensure thread safety for all shared state

## Building

```bash
# Build the project
mvn clean install

# Generate Javadoc
mvn javadoc:javadoc

# Run tests with coverage
mvn clean test jacoco:report
```

## Project Structure

```
src/
├── main/
│   ├── java/fr/lirmm/jdm/
│   │   ├── cache/           # Cache implementations
│   │   │   ├── Cache.java
│   │   │   ├── LruCache.java
│   │   │   ├── TtlCache.java
│   │   │   ├── CacheStats.java
│   │   │   └── CacheConfig.java
│   │   └── client/          # JDM API client
│   │       ├── JdmClient.java
│   │       ├── JdmApiException.java
│   │       └── model/       # API models
│   └── resources/
│       └── logback.xml
└── test/
    └── java/fr/lirmm/jdm/
        ├── cache/           # Cache tests
        └── client/          # Client integration tests
```

## License

This project is created for educational purposes.

## Acknowledgments

- [Jeux de Mots Project](https://www.jeuxdemots.org/) - LIRMM, University of Montpellier
- API Documentation: https://jdm-api.demo.lirmm.fr/schema

## Support

For issues and questions, please open a GitHub issue.
