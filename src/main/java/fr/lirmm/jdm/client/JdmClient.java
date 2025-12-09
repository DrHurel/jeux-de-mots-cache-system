package fr.lirmm.jdm.client;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import fr.lirmm.jdm.cache.Cache;
import fr.lirmm.jdm.cache.CacheConfig;
import fr.lirmm.jdm.cache.LruCache;
import fr.lirmm.jdm.cache.TtlCache;
import fr.lirmm.jdm.client.model.PublicNode;
import fr.lirmm.jdm.client.model.PublicNodeType;
import fr.lirmm.jdm.client.model.PublicRelationType;
import fr.lirmm.jdm.client.model.RelationsResponse;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * A cached client for the JDM (Jeux de Mots) API.
 *
 * <p>This client provides methods to interact with the JDM semantic network API while
 * transparently caching responses to improve performance. The cache can be configured with
 * different eviction strategies (LRU or TTL).
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * JdmClient client = JdmClient.builder()
 *     .cacheConfig(CacheConfig.builder()
 *         .maxSize(1000)
 *         .evictionStrategy(CacheConfig.EvictionStrategy.LRU)
 *         .build())
 *     .build();
 *
 * PublicNode node = client.getNodeByName("chat");
 * List<PublicNodeType> nodeTypes = client.getNodeTypes();
 * }</pre>
 */
public class JdmClient {

  private static final Logger logger = LoggerFactory.getLogger(JdmClient.class);
  private static final String DEFAULT_BASE_URL = "https://jdm-api.demo.lirmm.fr";

  private final String baseUrl;
  private final OkHttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final Cache<String, Object> cache;

  private JdmClient(Builder builder) {
    this.baseUrl = builder.baseUrl;
    this.httpClient = builder.httpClient != null ? builder.httpClient : new OkHttpClient();
    this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    // Initialize cache based on configuration
    CacheConfig config = builder.cacheConfig != null ? builder.cacheConfig : CacheConfig.defaultConfig();
    this.cache =
        switch (config.getEvictionStrategy()) {
          case LRU -> new LruCache<>(config);
          case TTL -> new TtlCache<>(config);
        };

    logger.info("JdmClient initialized with baseUrl={}, cache={}", baseUrl, config.getEvictionStrategy());
  }

  /**
   * Creates a new builder for JdmClient.
   *
   * @return a new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Creates a default JdmClient with LRU caching.
   *
   * @return a new JdmClient instance
   */
  public static JdmClient createDefault() {
    return builder().build();
  }

  /**
   * Retrieves a node by its ID.
   *
   * @param nodeId the node ID
   * @return the node, or null if not found
   * @throws JdmApiException if the API request fails
   */
  public PublicNode getNodeById(int nodeId) throws JdmApiException {
    String cacheKey = "node:id:" + nodeId;
    return getCached(cacheKey, () -> {
      String url = baseUrl + "/v0/node_by_id/" + nodeId;
      return fetchJson(url, PublicNode.class);
    });
  }

  /**
   * Retrieves a node by its name.
   *
   * @param nodeName the node name
   * @return the node, or null if not found
   * @throws JdmApiException if the API request fails
   * @throws IllegalArgumentException if nodeName is null or empty
   */
  public PublicNode getNodeByName(String nodeName) throws JdmApiException {
    if (nodeName == null || nodeName.trim().isEmpty()) {
      throw new IllegalArgumentException("Node name must not be null or empty");
    }
    String cacheKey = "node:name:" + nodeName;
    return getCached(cacheKey, () -> {
      String url = baseUrl + "/v0/node_by_name/" + nodeName;
      return fetchJson(url, PublicNode.class);
    });
  }

  /**
   * Retrieves refinements for a given node name.
   *
   * @param nodeName the node name
   * @return list of refinement nodes
   * @throws JdmApiException if the API request fails
   * @throws IllegalArgumentException if nodeName is null or empty
   */
  public List<PublicNode> getRefinements(String nodeName) throws JdmApiException {
    if (nodeName == null || nodeName.trim().isEmpty()) {
      throw new IllegalArgumentException("Node name must not be null or empty");
    }
    String cacheKey = "refinements:" + nodeName;
    return getCached(cacheKey, () -> {
      String url = baseUrl + "/v0/refinements/" + nodeName;
      return fetchJsonList(url, new TypeReference<List<PublicNode>>() {});
    });
  }

  /**
   * Retrieves all node types.
   *
   * @return list of node types
   * @throws JdmApiException if the API request fails
   */
  public List<PublicNodeType> getNodeTypes() throws JdmApiException {
    String cacheKey = "node_types";
    return getCached(cacheKey, () -> {
      String url = baseUrl + "/v0/nodes_types";
      return fetchJsonList(url, new TypeReference<List<PublicNodeType>>() {});
    });
  }

  /**
   * Retrieves all relation types.
   *
   * @return list of relation types
   * @throws JdmApiException if the API request fails
   */
  public List<PublicRelationType> getRelationTypes() throws JdmApiException {
    String cacheKey = "relation_types";
    return getCached(cacheKey, () -> {
      String url = baseUrl + "/v0/relations_types";
      return fetchJsonList(url, new TypeReference<List<PublicRelationType>>() {});
    });
  }

  /**
   * Retrieves relations from a given node by name.
   *
   * @param nodeName the source node name
   * @return relations response containing nodes and relations
   * @throws JdmApiException if the API request fails
   */
  public RelationsResponse getRelationsFrom(String nodeName) throws JdmApiException {
    String cacheKey = "relations:from:" + nodeName;
    return getCached(cacheKey, () -> {
      String url = baseUrl + "/v0/relations/from/" + nodeName;
      return fetchJson(url, RelationsResponse.class);
    });
  }

  /**
   * Retrieves relations from a given node by ID.
   *
   * @param nodeId the source node ID
   * @return relations response containing nodes and relations
   * @throws JdmApiException if the API request fails
   */
  public RelationsResponse getRelationsFromById(int nodeId) throws JdmApiException {
    String cacheKey = "relations:from:id:" + nodeId;
    return getCached(cacheKey, () -> {
      String url = baseUrl + "/v0/relations/from_by_id/" + nodeId;
      return fetchJson(url, RelationsResponse.class);
    });
  }

  /**
   * Retrieves relations to a given node by name.
   *
   * @param nodeName the target node name
   * @return relations response containing nodes and relations
   * @throws JdmApiException if the API request fails
   */
  public RelationsResponse getRelationsTo(String nodeName) throws JdmApiException {
    String cacheKey = "relations:to:" + nodeName;
    return getCached(cacheKey, () -> {
      String url = baseUrl + "/v0/relations/to/" + nodeName;
      return fetchJson(url, RelationsResponse.class);
    });
  }

  /**
   * Retrieves relations between two nodes by name.
   *
   * @param node1Name the source node name
   * @param node2Name the target node name
   * @return relations response containing nodes and relations
   * @throws JdmApiException if the API request fails
   */
  public RelationsResponse getRelationsFromTo(String node1Name, String node2Name)
      throws JdmApiException {
    String cacheKey = "relations:from:" + node1Name + ":to:" + node2Name;
    return getCached(cacheKey, () -> {
      String url = baseUrl + "/v0/relations/from/" + node1Name + "/to/" + node2Name;
      return fetchJson(url, RelationsResponse.class);
    });
  }

  /**
   * Returns the current cache statistics.
   *
   * @return cache statistics
   */
  public fr.lirmm.jdm.cache.CacheStats getCacheStats() {
    return cache.getStats();
  }

  /**
   * Clears all cached data.
   */
  public void clearCache() {
    cache.clear();
    logger.info("Cache cleared");
  }

  /**
   * Invalidates a specific cache entry.
   *
   * @param key the cache key to invalidate
   */
  public void invalidateCacheEntry(String key) {
    cache.invalidate(key);
  }

  @SuppressWarnings("unchecked")
  private <T> T getCached(String key, ThrowingSupplier<T> supplier) throws JdmApiException {
    T cached = (T) cache.get(key);
    if (cached != null) {
      logger.debug("Cache hit for key: {}", key);
      return cached;
    }

    logger.debug("Cache miss for key: {}, fetching from API", key);
    T result = supplier.get();
    if (result != null) {
      cache.put(key, result);
    }
    return result;
  }

  private <T> T fetchJson(String url, Class<T> clazz) throws JdmApiException {
    try {
      String json = executeRequest(url);
      return objectMapper.readValue(json, clazz);
    } catch (IOException e) {
      throw new JdmApiException("Failed to parse JSON response", e);
    }
  }

  private <T> T fetchJsonList(String url, TypeReference<T> typeRef) throws JdmApiException {
    try {
      String json = executeRequest(url);
      return objectMapper.readValue(json, typeRef);
    } catch (IOException e) {
      throw new JdmApiException("Failed to parse JSON response", e);
    }
  }

  @SuppressWarnings("NullAway")
  private String executeRequest(String url) throws JdmApiException {
    Request request = new Request.Builder().url(url).get().build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new JdmApiException("API request failed with status: " + response.code());
      }

      okhttp3.ResponseBody body = response.body();
      if (body == null) {
        throw new JdmApiException("Empty response body");
      }

      return body.string();
    } catch (IOException e) {
      throw new JdmApiException("HTTP request failed for URL: " + url, e);
    }
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws JdmApiException;
  }

  /** Builder for JdmClient. */
  public static class Builder {
    private String baseUrl = DEFAULT_BASE_URL;
    private OkHttpClient httpClient;
    private CacheConfig cacheConfig;

    /**
     * Sets the base URL for the JDM API.
     *
     * @param baseUrl the base URL
     * @return this builder
     */
    public Builder baseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
      return this;
    }

    /**
     * Sets the HTTP client to use.
     *
     * @param httpClient the OkHttp client
     * @return this builder
     */
    public Builder httpClient(OkHttpClient httpClient) {
      this.httpClient = httpClient;
      return this;
    }

    /**
     * Sets the cache configuration.
     *
     * @param cacheConfig the cache configuration
     * @return this builder
     */
    public Builder cacheConfig(CacheConfig cacheConfig) {
      this.cacheConfig = cacheConfig;
      return this;
    }

    /**
     * Configures the client to use LRU caching with the specified size.
     *
     * @param maxSize the maximum cache size
     * @return this builder
     */
    public Builder lruCache(int maxSize) {
      this.cacheConfig =
          CacheConfig.builder()
              .maxSize(maxSize)
              .evictionStrategy(CacheConfig.EvictionStrategy.LRU)
              .build();
      return this;
    }

    /**
     * Configures the client to use TTL caching with the specified parameters.
     *
     * @param maxSize the maximum cache size
     * @param ttl the time-to-live duration
     * @return this builder
     */
    public Builder ttlCache(int maxSize, Duration ttl) {
      this.cacheConfig =
          CacheConfig.builder()
              .maxSize(maxSize)
              .ttl(ttl)
              .evictionStrategy(CacheConfig.EvictionStrategy.TTL)
              .build();
      return this;
    }

    /**
     * Builds the JdmClient instance.
     *
     * @return a new JdmClient
     */
    public JdmClient build() {
      return new JdmClient(this);
    }
  }
}
