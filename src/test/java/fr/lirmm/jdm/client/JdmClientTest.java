package fr.lirmm.jdm.client;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fr.lirmm.jdm.cache.CacheStats;
import fr.lirmm.jdm.client.model.PublicNode;
import fr.lirmm.jdm.client.model.PublicNodeType;
import fr.lirmm.jdm.client.model.RelationsResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

/** Integration tests for JdmClient with caching. */
class JdmClientTest {

  private MockWebServer mockServer;
  private JdmClient client;

  @BeforeEach
  void setUp() throws IOException {
    mockServer = new MockWebServer();
    mockServer.start();

    client =
        JdmClient.builder()
            .baseUrl(mockServer.url("/").toString())
            .lruCache(10)
            .build();
  }

  @AfterEach
  void tearDown() throws IOException {
    if (mockServer != null) {
      mockServer.shutdown();
    }
  }

  @Test
  void testGetNodeById() throws JdmApiException {
    String jsonResponse = """
        {
          "id": 123,
          "name": "chat",
          "type": 1,
          "w": 100
        }
        """;

    mockServer.enqueue(new MockResponse().setBody(jsonResponse).setResponseCode(200));

    PublicNode node = client.getNodeById(123);

    assertNotNull(node);
    assertEquals(123, node.getId());
    assertEquals("chat", node.getName());
  }

  @Test
  void testGetNodeByName() throws JdmApiException {
    String jsonResponse = """
        {
          "id": 456,
          "name": "chien",
          "type": 1,
          "w": 95
        }
        """;

    mockServer.enqueue(new MockResponse().setBody(jsonResponse).setResponseCode(200));

    PublicNode node = client.getNodeByName("chien");

    assertNotNull(node);
    assertEquals(456, node.getId());
    assertEquals("chien", node.getName());
  }

  @Test
  void testCacheHit() throws JdmApiException {
    String jsonResponse = """
        {
          "id": 123,
          "name": "chat",
          "type": 1
        }
        """;

    mockServer.enqueue(new MockResponse().setBody(jsonResponse).setResponseCode(200));

    // First call - cache miss
    PublicNode node1 = client.getNodeById(123);
    assertEquals(123, node1.getId());

    // Second call - should be cached, no server request
    PublicNode node2 = client.getNodeById(123);
    assertEquals(123, node2.getId());

    // Verify only one request was made to server
    assertEquals(1, mockServer.getRequestCount());

    // Verify cache stats
    CacheStats stats = client.getCacheStats();
    assertEquals(1, stats.getHitCount());
    assertEquals(1, stats.getMissCount());
  }

  @Test
  void testGetNodeTypes() throws JdmApiException {
    String jsonResponse = """
        [
          {
            "id": 1,
            "name": "term",
            "help": "A term"
          },
          {
            "id": 2,
            "name": "concept",
            "help": "A concept"
          }
        ]
        """;

    mockServer.enqueue(new MockResponse().setBody(jsonResponse).setResponseCode(200));

    List<PublicNodeType> types = client.getNodeTypes();

    assertNotNull(types);
    assertEquals(2, types.size());
    assertEquals("term", types.get(0).getName());
    assertEquals("concept", types.get(1).getName());
  }

  @Test
  void testGetRelationsFrom() throws JdmApiException {
    String jsonResponse = """
        {
          "nodes": [
            {
              "id": 1,
              "name": "chat",
              "type": 1
            }
          ],
          "relations": [
            {
              "id": 100,
              "node1": 1,
              "node2": 2,
              "type": 5,
              "w": 50.0
            }
          ]
        }
        """;

    mockServer.enqueue(new MockResponse().setBody(jsonResponse).setResponseCode(200));

    RelationsResponse response = client.getRelationsFrom("chat");

    assertNotNull(response);
    assertEquals(1, response.getNodes().size());
    assertEquals(1, response.getRelations().size());
    assertEquals(100, response.getRelations().get(0).getId());
  }

  @Test
  void testApiError() {
    mockServer.enqueue(new MockResponse().setResponseCode(404).setBody("Not found"));

    assertThrows(JdmApiException.class, () -> client.getNodeById(999));
  }

  @Test
  void testClearCache() throws JdmApiException {
    String jsonResponse = "{\"id\": 123, \"name\": \"test\"}";
    mockServer.enqueue(new MockResponse().setBody(jsonResponse).setResponseCode(200));
    mockServer.enqueue(new MockResponse().setBody(jsonResponse).setResponseCode(200));

    client.getNodeById(123);
    client.clearCache();
    client.getNodeById(123);

    // Should have 2 requests because cache was cleared
    assertEquals(2, mockServer.getRequestCount());
  }

  @Test
  void testCacheWithTtl() throws JdmApiException, InterruptedException {
    JdmClient ttlClient =
        JdmClient.builder()
            .baseUrl(mockServer.url("/").toString())
            .ttlCache(10, Duration.ofMillis(500))
            .build();

    String jsonResponse = "{\"id\": 123, \"name\": \"test\"}";
    mockServer.enqueue(new MockResponse().setBody(jsonResponse).setResponseCode(200));
    mockServer.enqueue(new MockResponse().setBody(jsonResponse).setResponseCode(200));

    ttlClient.getNodeById(123);

    // Wait for TTL to expire
    Thread.sleep(600);

    ttlClient.getNodeById(123);

    // Should have 2 requests because entry expired
    assertEquals(2, mockServer.getRequestCount());
  }

  @Test
  void testPerformanceImprovement() throws JdmApiException {
    String jsonResponse = "{\"id\": 123, \"name\": \"test\"}";

    // Enqueue 200 responses (100 unique + 10 for cache testing)
    for (int i = 0; i < 110; i++) {
      mockServer.enqueue(new MockResponse().setBody(jsonResponse).setResponseCode(200));
    }

    // First 10 calls (cache misses)
    long uncachedStart = System.nanoTime();
    for (int i = 0; i < 10; i++) {
      client.getNodeByName("test" + i);
    }
    long uncachedDuration = System.nanoTime() - uncachedStart;

    // Next 100 calls (cache hits for 10 keys)
    long cachedStart = System.nanoTime();
    for (int i = 0; i < 100; i++) {
      client.getNodeByName("test" + (i % 10)); // Reuse 10 keys
    }
    long cachedDuration = System.nanoTime() - cachedStart;

    // Cached calls should be significantly faster per operation
    double uncachedPerOp = (double) uncachedDuration / 10;
    double cachedPerOp = (double) cachedDuration / 100;
    assertTrue(cachedPerOp < uncachedPerOp);

    CacheStats stats = client.getCacheStats();
    assertTrue(stats.getHitRate() > 0.8); // Should have high hit rate
  }
}
