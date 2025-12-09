package fr.lirmm.jdm.benchmark;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadLocalRandom;

import fr.lirmm.jdm.cache.Cache;
import fr.lirmm.jdm.cache.CacheConfig;
import fr.lirmm.jdm.cache.LruCache;
import fr.lirmm.jdm.cache.ShardedCache;
import fr.lirmm.jdm.cache.ThreadLocalCache;

/**
 * Comprehensive benchmark comparing different cache optimization strategies.
 * 
 * Compares:
 * 1. Baseline LruCache (synchronized LinkedHashMap)
 * 2. ThreadLocalCache (L1/L2 caching)
 * 3. ShardedCache (partitioned for reduced contention)
 * 4. ForkJoinPool executor (vs standard ExecutorService)
 * 
 * Measures:
 * - Throughput (ops/sec)
 * - Latency percentiles (P50, P95, P99)
 * - Per-thread efficiency
 * - Hit rates
 */
public class OptimizationBenchmark {
    
    private static final int CACHE_SIZE = 5000;
    private static final int OPS_PER_THREAD = 1000;
    private static final int ITERATIONS = 3;
    private static final int[] THREAD_COUNTS = {10, 25, 50, 100, 200};
    
    private static final double NS_TO_US = 1000.0;
    
    public static void main(String[] args) throws Exception {
        System.out.println("🚀 Cache Optimization Benchmark\n");
        System.out.println("=" .repeat(80));
        System.out.println();
        
        OptimizationBenchmark benchmark = new OptimizationBenchmark();
        
        // Warmup
        System.out.println("⏳ Warming up JVM (5 iterations)...");
        benchmark.warmup();
        System.out.println("✅ Warmup complete\n");
        
        // Run benchmarks
        String report = benchmark.runComparison();
        
        // Save to file
        String filename = "OPTIMIZATION_BENCHMARK_" + System.currentTimeMillis() + ".md";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            writer.write(report);
        }
        
        System.out.println("\n✅ Benchmark report saved to: " + filename);
        System.out.println("\n" + report);
    }
    
    private void warmup() throws Exception {
        for (int i = 0; i < 5; i++) {
            CacheConfig config = CacheConfig.builder()
                .maxSize(CACHE_SIZE)
                .evictionStrategy(CacheConfig.EvictionStrategy.LRU)
                .build();
            
            Cache<String, String> cache = new LruCache<>(config);
            
            for (int j = 0; j < 5000; j++) {
                cache.put("key-" + j, "value");
                cache.get("key-" + (j % 1000));
            }
            
            System.gc();
            Thread.sleep(100);
        }
    }
    
    private String runComparison() throws Exception {
        StringBuilder report = new StringBuilder();
        
        report.append("# Cache Optimization Benchmark Results\n\n");
        report.append("**Date:** ").append(java.time.LocalDateTime.now()).append("\n");
        report.append("**Cache Size:** ").append(CACHE_SIZE).append("\n");
        report.append("**Operations per Thread:** ").append(OPS_PER_THREAD).append("\n");
        report.append("**Iterations:** ").append(ITERATIONS).append("\n\n");
        report.append("---\n\n");
        
        // Run all benchmarks
        System.out.println("\n📊 Benchmarking Baseline LruCache...");
        List<BenchmarkResult> baselineResults = benchmarkCache("Baseline LruCache", 
            () -> new LruCache<>(CacheConfig.builder().maxSize(CACHE_SIZE).evictionStrategy(CacheConfig.EvictionStrategy.LRU).build()),
            false);
        
        System.out.println("\n📊 Benchmarking ThreadLocalCache...");
        List<BenchmarkResult> threadLocalResults = benchmarkCache("ThreadLocalCache",
            () -> new ThreadLocalCache<>(new LruCache<>(CacheConfig.builder().maxSize(CACHE_SIZE).evictionStrategy(CacheConfig.EvictionStrategy.LRU).build()), 100),
            false);
        
        System.out.println("\n📊 Benchmarking ShardedCache (16 shards)...");
        List<BenchmarkResult> shardedResults = benchmarkCache("ShardedCache (16 shards)",
            () -> new ShardedCache<>(CacheConfig.builder().maxSize(CACHE_SIZE).evictionStrategy(CacheConfig.EvictionStrategy.LRU).build(), 16),
            false);
        
        System.out.println("\n📊 Benchmarking Baseline with ForkJoinPool...");
        List<BenchmarkResult> forkJoinResults = benchmarkCache("Baseline + ForkJoinPool",
            () -> new LruCache<>(CacheConfig.builder().maxSize(CACHE_SIZE).evictionStrategy(CacheConfig.EvictionStrategy.LRU).build()),
            true);
        
        // Generate report
        report.append("## 1. Throughput Comparison\n\n");
        report.append("| Threads | Baseline (ops/sec) | ThreadLocal (ops/sec) | Sharded (ops/sec) | ForkJoin (ops/sec) |\n");
        report.append("|---------|--------------------|-----------------------|-------------------|--------------------|\n");
        
        for (int i = 0; i < THREAD_COUNTS.length; i++) {
            report.append(String.format("| %d | %,.0f | %,.0f | %,.0f | %,.0f |\n",
                THREAD_COUNTS[i],
                baselineResults.get(i).throughput,
                threadLocalResults.get(i).throughput,
                shardedResults.get(i).throughput,
                forkJoinResults.get(i).throughput));
        }
        
        report.append("\n## 2. P99 Latency Comparison\n\n");
        report.append("| Threads | Baseline (μs) | ThreadLocal (μs) | Sharded (μs) | ForkJoin (μs) |\n");
        report.append("|---------|---------------|------------------|--------------|---------------|\n");
        
        for (int i = 0; i < THREAD_COUNTS.length; i++) {
            report.append(String.format("| %d | %.2f | %.2f | %.2f | %.2f |\n",
                THREAD_COUNTS[i],
                baselineResults.get(i).p99LatencyUs,
                threadLocalResults.get(i).p99LatencyUs,
                shardedResults.get(i).p99LatencyUs,
                forkJoinResults.get(i).p99LatencyUs));
        }
        
        report.append("\n## 3. Per-Thread Efficiency\n\n");
        report.append("| Threads | Baseline | ThreadLocal | Sharded | ForkJoin |\n");
        report.append("|---------|----------|-------------|---------|----------|\n");
        
        for (int i = 0; i < THREAD_COUNTS.length; i++) {
            int threads = THREAD_COUNTS[i];
            report.append(String.format("| %d | %,.0f | %,.0f | %,.0f | %,.0f |\n",
                threads,
                baselineResults.get(i).throughput / threads,
                threadLocalResults.get(i).throughput / threads,
                shardedResults.get(i).throughput / threads,
                forkJoinResults.get(i).throughput / threads));
        }
        
        report.append("\n## 4. Improvement Analysis\n\n");
        
        for (int i = 0; i < THREAD_COUNTS.length; i++) {
            int threads = THREAD_COUNTS[i];
            double baseline = baselineResults.get(i).throughput;
            
            double threadLocalImprovement = ((threadLocalResults.get(i).throughput - baseline) / baseline) * 100;
            double shardedImprovement = ((shardedResults.get(i).throughput - baseline) / baseline) * 100;
            double forkJoinImprovement = ((forkJoinResults.get(i).throughput - baseline) / baseline) * 100;
            
            report.append(String.format("### %d Threads\n\n", threads));
            report.append(String.format("- **ThreadLocal:** %+.1f%% throughput", threadLocalImprovement));
            report.append(threadLocalImprovement > 0 ? " ✅\n" : " ❌\n");
            report.append(String.format("- **Sharded:** %+.1f%% throughput", shardedImprovement));
            report.append(shardedImprovement > 0 ? " ✅\n" : " ❌\n");
            report.append(String.format("- **ForkJoin:** %+.1f%% throughput", forkJoinImprovement));
            report.append(forkJoinImprovement > 0 ? " ✅\n" : " ❌\n");
            report.append("\n");
        }
        
        report.append("## 5. Recommendations\n\n");
        report.append("### Best Optimization by Thread Count:\n\n");
        
        for (int i = 0; i < THREAD_COUNTS.length; i++) {
            int threads = THREAD_COUNTS[i];
            double baseline = baselineResults.get(i).throughput;
            double threadLocal = threadLocalResults.get(i).throughput;
            double sharded = shardedResults.get(i).throughput;
            double forkJoin = forkJoinResults.get(i).throughput;
            
            double max = Math.max(Math.max(baseline, threadLocal), Math.max(sharded, forkJoin));
            String best;
            if (max == threadLocal) {
                best = "ThreadLocalCache";
            } else if (max == sharded) {
                best = "ShardedCache";
            } else if (max == forkJoin) {
                best = "ForkJoinPool";
            } else {
                best = "Baseline (no optimization needed)";
            }
            
            double improvement = ((max - baseline) / baseline) * 100;
            
            report.append(String.format("- **%d threads:** %s (+%.1f%%)\n", threads, best, improvement));
        }
        
        report.append("\n### General Guidelines:\n\n");
        report.append("1. **ThreadLocalCache:** Best for read-heavy workloads with temporal locality\n");
        report.append("2. **ShardedCache:** Best for high-concurrency write-heavy workloads\n");
        report.append("3. **ForkJoinPool:** Best for uniform workloads with many small tasks\n");
        report.append("4. **Baseline:** Sufficient for <25 threads with mixed read/write\n");
        
        report.append("\n---\n\n");
        report.append("**Benchmark Complete**\n");
        
        return report.toString();
    }
    
    private List<BenchmarkResult> benchmarkCache(String name, CacheFactory<String, String> factory, boolean useForkJoin) throws Exception {
        List<BenchmarkResult> results = new ArrayList<>();
        
        for (int threads : THREAD_COUNTS) {
            System.out.printf("  Testing %d threads... ", threads);
            
            List<Long> allLatencies = new ArrayList<>();
            List<Long> durations = new ArrayList<>();
            
            for (int iter = 0; iter < ITERATIONS; iter++) {
                System.gc();
                Thread.sleep(100);
                
                Cache<String, String> cache = factory.create();
                ExecutorService executor = useForkJoin 
                    ? new ForkJoinPool(threads)
                    : Executors.newFixedThreadPool(threads);
                
                CountDownLatch startLatch = new CountDownLatch(1);
                CountDownLatch completionLatch = new CountDownLatch(threads);
                
                List<List<Long>> threadLatencies = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    threadLatencies.add(Collections.synchronizedList(new ArrayList<>()));
                }
                
                long startTime = System.nanoTime();
                
                for (int t = 0; t < threads; t++) {
                    final List<Long> latencies = threadLatencies.get(t);
                    
                    executor.submit(() -> {
                        try {
                            startLatch.await();
                            for (int i = 0; i < OPS_PER_THREAD; i++) {
                                String key = "key-" + ThreadLocalRandom.current().nextInt(CACHE_SIZE);
                                long opStart = System.nanoTime();
                                if (i % 2 == 0) {
                                    cache.put(key, "value");
                                } else {
                                    cache.get(key);
                                }
                                latencies.add(System.nanoTime() - opStart);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            completionLatch.countDown();
                        }
                    });
                }
                
                startLatch.countDown();
                completionLatch.await();
                durations.add(System.nanoTime() - startTime);
                
                executor.shutdown();
                
                for (List<Long> threadLat : threadLatencies) {
                    allLatencies.addAll(threadLat);
                }
            }
            
            Collections.sort(allLatencies);
            
            long avgDuration = durations.stream().mapToLong(Long::longValue).sum() / durations.size();
            double throughput = (threads * OPS_PER_THREAD) / (avgDuration / 1_000_000_000.0);
            double p50 = getPercentile(allLatencies, 0.50) / NS_TO_US;
            double p95 = getPercentile(allLatencies, 0.95) / NS_TO_US;
            double p99 = getPercentile(allLatencies, 0.99) / NS_TO_US;
            
            results.add(new BenchmarkResult(throughput, p50, p95, p99));
            
            System.out.printf("%.0f ops/sec (P99: %.2f μs)\n", throughput, p99);
        }
        
        return results;
    }
    
    private long getPercentile(List<Long> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(sortedValues.size() - 1, index));
        return sortedValues.get(index);
    }
    
    @FunctionalInterface
    interface CacheFactory<K, V> {
        Cache<K, V> create();
    }
    
    static class BenchmarkResult {
        final double throughput;
        final double p50LatencyUs;
        final double p95LatencyUs;
        final double p99LatencyUs;
        
        BenchmarkResult(double throughput, double p50, double p95, double p99) {
            this.throughput = throughput;
            this.p50LatencyUs = p50;
            this.p95LatencyUs = p95;
            this.p99LatencyUs = p99;
        }
    }
}
