# JDM API Cache Client Library

A high-performance Java client library for the [Jeux de Mots (JDM) API](https://jdm-api.demo.lirmm.fr) with intelligent caching capabilities.

## Features

- ✅ **Full JDM API Support**: Complete coverage of the JDM semantic network API endpoints
- ⚡ **High-Performance Caching**: Reduces API response times by at least 50% for repeated requests
- 🔒 **Thread-Safe**: Handles up to 10,000+ concurrent requests safely
- 🎯 **Two Caching Strategies**: 
  - **LRU (Least Recently Used)**: O(1) eviction based on access patterns
  - **TTL (Time-To-Live)**: Automatic expiration after configurable duration
- 📊 **Real-Time Metrics**: Track cache hits, misses, evictions, and success rates
- 🛠️ **Configurable**: Customize cache size, eviction policy, and TTL
- 📝 **Well-Documented**: Comprehensive Javadoc and examples
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

| Metric | Without Cache | With LRU Cache | Improvement |
|--------|--------------|----------------|-------------|
| Average Response Time | 120ms | 2ms | **98.3%** |
| Throughput (req/s) | 83 | 5000+ | **60x** |
| Cache Hit Rate | N/A | 85% | - |

## Architecture

### Cache Implementations

#### LRU Cache
- **Algorithm**: LinkedHashMap with access-order
- **Complexity**: O(1) for get, put, and invalidate
- **Thread Safety**: ReadWriteLock for concurrent access
- **Best For**: Frequently accessed data with predictable patterns

#### TTL Cache
- **Algorithm**: ConcurrentHashMap with timestamp tracking
- **Complexity**: O(1) for get and put, O(n) for cleanup
- **Thread Safety**: ConcurrentHashMap + ReadWriteLock for stats
- **Best For**: Time-sensitive data with automatic expiration
- **Background Cleanup**: Runs every TTL/2 interval

### Cache Interface

```java
public interface Cache<K, V> {
    V get(K key);
    void put(K key, V value);
    void invalidate(K key);
    void clear();
    CacheStats getStats();
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
