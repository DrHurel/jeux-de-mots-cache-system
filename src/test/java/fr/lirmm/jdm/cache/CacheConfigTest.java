package fr.lirmm.jdm.cache;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

/** Unit tests for CacheConfig. */
class CacheConfigTest {

  @Test
  void testDefaultConfig() {
    CacheConfig config = CacheConfig.defaultConfig();

    assertEquals(CacheConfig.DEFAULT_MAX_SIZE, config.getMaxSize());
    assertEquals(CacheConfig.DEFAULT_TTL, config.getTtl());
    assertEquals(CacheConfig.EvictionStrategy.LRU, config.getEvictionStrategy());
  }

  @Test
  void testBuilderCustomValues() {
    CacheConfig config =
        CacheConfig.builder()
            .maxSize(500)
            .ttl(Duration.ofMinutes(10))
            .evictionStrategy(CacheConfig.EvictionStrategy.TTL)
            .build();

    assertEquals(500, config.getMaxSize());
    assertEquals(Duration.ofMinutes(10), config.getTtl());
    assertEquals(CacheConfig.EvictionStrategy.TTL, config.getEvictionStrategy());
  }

  @Test
  void testInvalidMaxSize() {
    assertThrows(
        IllegalArgumentException.class, () -> CacheConfig.builder().maxSize(0).build());
  }

  @Test
  void testInvalidTtl() {
    assertThrows(
        IllegalArgumentException.class,
        () -> CacheConfig.builder().ttl(Duration.ofSeconds(-1)).build());
  }

  @Test
  void testNullTtl() {
    assertThrows(
        IllegalArgumentException.class, () -> CacheConfig.builder().ttl(null).build());
  }

  @Test
  void testNullEvictionStrategy() {
    assertThrows(
        IllegalArgumentException.class,
        () -> CacheConfig.builder().evictionStrategy(null).build());
  }

  @Test
  void testBuilderChaining() {
    CacheConfig config =
        CacheConfig.builder()
            .maxSize(1000)
            .ttl(Duration.ofHours(1))
            .evictionStrategy(CacheConfig.EvictionStrategy.LRU)
            .build();

    assertNotNull(config);
    assertEquals(1000, config.getMaxSize());
  }
}
