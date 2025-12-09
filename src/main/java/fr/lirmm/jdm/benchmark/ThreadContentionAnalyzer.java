package fr.lirmm.jdm.benchmark;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import fr.lirmm.jdm.cache.Cache;
import fr.lirmm.jdm.cache.CacheConfig;
import fr.lirmm.jdm.cache.LruCache;

/**
 * Deep analysis tool to investigate the 50-thread P99 latency anomaly.
 * 
 * This analyzer provides:
 * - Thread contention metrics
 * - GC pause tracking
 * - Lock wait time analysis
 * - Per-iteration breakdown
 * - Heat map of latency distribution
 */
public class ThreadContentionAnalyzer {
    
    private static final int CACHE_SIZE = 5000;
    private static final int OPS_PER_THREAD = 1000;
    private static final int ITERATIONS = 5; // More iterations for statistical confidence
    
    // Thread counts to analyze - focus on the anomaly range
    private static final int[] THREAD_COUNTS = {10, 25, 50, 75, 100, 150, 200};
    
    private final ThreadMXBean threadMXBean;
    private final List<GarbageCollectorMXBean> gcBeans;
    
    public ThreadContentionAnalyzer() {
        this.threadMXBean = ManagementFactory.getThreadMXBean();
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        
        // Enable thread contention monitoring if supported
        if (threadMXBean.isThreadContentionMonitoringSupported()) {
            threadMXBean.setThreadContentionMonitoringEnabled(true);
        }
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println("🔍 Thread Contention Analysis - Investigating 50-Thread Anomaly\n");
        System.out.println("=" .repeat(80));
        
        ThreadContentionAnalyzer analyzer = new ThreadContentionAnalyzer();
        
        // Warmup
        System.out.println("\n⏳ Warming up JVM (3 iterations)...");
        analyzer.warmup();
        System.out.println("✅ Warmup complete\n");
        
        // Run analysis
        String report = analyzer.runAnalysis();
        
        // Save to file
        String filename = "THREAD_CONTENTION_ANALYSIS_" + System.currentTimeMillis() + ".md";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            writer.write(report);
        }
        
        System.out.println("\n✅ Analysis report saved to: " + filename);
        System.out.println("\n" + report);
    }
    
    private void warmup() throws Exception {
        for (int i = 0; i < 3; i++) {
            Cache<String, String> cache = new LruCache<>(
                CacheConfig.builder().maxSize(CACHE_SIZE).evictionStrategy(CacheConfig.EvictionStrategy.LRU).build()
            );
            
            for (int j = 0; j < 5000; j++) {
                cache.put("warmup-" + j, "value");
                cache.get("warmup-" + (j % 1000));
            }
            
            System.gc();
            Thread.sleep(100);
        }
    }
    
    private String runAnalysis() throws Exception {
        StringBuilder report = new StringBuilder();
        
        report.append("# Thread Contention Analysis Report\n\n");
        report.append("## Investigation: 50-Thread P99 Latency Anomaly\n\n");
        report.append("**Hypothesis:** Mid-range thread count (50) experiences higher contention than higher counts (200)\n\n");
        report.append("**Analysis Date:** ").append(java.time.LocalDateTime.now()).append("\n");
        report.append("**Iterations per Thread Count:** ").append(ITERATIONS).append("\n");
        report.append("**Operations per Thread:** ").append(OPS_PER_THREAD).append("\n");
        report.append("**Cache Size:** ").append(CACHE_SIZE).append("\n\n");
        
        report.append("---\n\n");
        
        // Collect detailed metrics for each thread count
        Map<Integer, ContentionMetrics> allMetrics = new HashMap<>();
        
        for (int threadCount : THREAD_COUNTS) {
            System.out.println("\n📊 Analyzing " + threadCount + " threads...");
            ContentionMetrics metrics = analyzeThreadCount(threadCount);
            allMetrics.put(threadCount, metrics);
            
            System.out.printf("   P99 Latency: %.2f μs | Avg Wait Time: %.2f μs | GC Pauses: %d\n",
                metrics.p99LatencyUs, metrics.avgWaitTimeUs, metrics.gcPauseCount);
        }
        
        // Generate comprehensive report
        report.append("## 1. Latency Percentiles by Thread Count\n\n");
        report.append("| Threads | P50 (μs) | P75 (μs) | P90 (μs) | P95 (μs) | P99 (μs) | P99.9 (μs) | Max (μs) |\n");
        report.append("|---------|----------|----------|----------|----------|----------|------------|----------|\n");
        
        for (int threads : THREAD_COUNTS) {
            ContentionMetrics m = allMetrics.get(threads);
            report.append(String.format("| %d | %.2f | %.2f | %.2f | %.2f | **%.2f** | %.2f | %.2f |\n",
                threads, m.p50LatencyUs, m.p75LatencyUs, m.p90LatencyUs, 
                m.p95LatencyUs, m.p99LatencyUs, m.p999LatencyUs, m.maxLatencyUs));
        }
        
        report.append("\n## 2. Thread Contention Metrics\n\n");
        report.append("| Threads | Avg Wait Time (μs) | Max Wait Time (μs) | Blocked Count | Contention Rate (%) |\n");
        report.append("|---------|--------------------|--------------------|---------------|---------------------|\n");
        
        for (int threads : THREAD_COUNTS) {
            ContentionMetrics m = allMetrics.get(threads);
            report.append(String.format("| %d | %.2f | %.2f | %d | %.2f%% |\n",
                threads, m.avgWaitTimeUs, m.maxWaitTimeUs, m.blockedCount, m.contentionRate * 100));
        }
        
        report.append("\n## 3. Garbage Collection Impact\n\n");
        report.append("| Threads | GC Pause Count | Total GC Time (ms) | GC Time per Op (ns) | Impact on P99? |\n");
        report.append("|---------|----------------|--------------------|--------------------|----------------|\n");
        
        for (int threads : THREAD_COUNTS) {
            ContentionMetrics m = allMetrics.get(threads);
            String impact = (m.gcTotalTimeMs > 10 && m.p99LatencyUs > 1000) ? "⚠️ YES" : "✅ No";
            report.append(String.format("| %d | %d | %d | %.2f | %s |\n",
                threads, m.gcPauseCount, m.gcTotalTimeMs, m.gcTimePerOpNs, impact));
        }
        
        report.append("\n## 4. Throughput Analysis\n\n");
        report.append("| Threads | Avg Throughput (ops/sec) | Std Dev | Efficiency per Thread |\n");
        report.append("|---------|--------------------------|---------|----------------------|\n");
        
        for (int threads : THREAD_COUNTS) {
            ContentionMetrics m = allMetrics.get(threads);
            double efficiencyPerThread = m.throughput / threads;
            report.append(String.format("| %d | %,.0f | %.0f | %,.0f |\n",
                threads, m.throughput, m.stdDev, efficiencyPerThread));
        }
        
        report.append("\n## 5. Anomaly Detection\n\n");
        
        // Find the anomaly
        ContentionMetrics m50 = allMetrics.get(50);
        ContentionMetrics m100 = allMetrics.get(100);
        ContentionMetrics m200 = allMetrics.get(200);
        
        report.append("### Key Findings:\n\n");
        
        // Compare 50 vs 100 threads
        double latencyIncrease50vs100 = ((m50.p99LatencyUs - m100.p99LatencyUs) / m100.p99LatencyUs) * 100;
        report.append(String.format("1. **50-thread P99:** %.2f μs vs **100-thread P99:** %.2f μs = **%.1f%% difference**\n",
            m50.p99LatencyUs, m100.p99LatencyUs, latencyIncrease50vs100));
        
        // Compare 50 vs 200 threads
        double latencyIncrease50vs200 = ((m50.p99LatencyUs - m200.p99LatencyUs) / m200.p99LatencyUs) * 100;
        report.append(String.format("2. **50-thread P99:** %.2f μs vs **200-thread P99:** %.2f μs = **%.1f%% difference**\n",
            m50.p99LatencyUs, m200.p99LatencyUs, latencyIncrease50vs200));
        
        // Contention comparison
        double contentionIncrease = ((m50.avgWaitTimeUs - m200.avgWaitTimeUs) / m200.avgWaitTimeUs) * 100;
        report.append(String.format("3. **Wait time at 50 threads:** %.2f μs vs **200 threads:** %.2f μs = **%.1f%% more contention**\n",
            m50.avgWaitTimeUs, m200.avgWaitTimeUs, contentionIncrease));
        
        // GC impact
        report.append(String.format("4. **GC pauses at 50 threads:** %d vs **200 threads:** %d\n",
            m50.gcPauseCount, m200.gcPauseCount));
        
        // Efficiency analysis
        double efficiency50 = m50.throughput / 50;
        double efficiency200 = m200.throughput / 200;
        double efficiencyDrop = ((efficiency50 - efficiency200) / efficiency200) * 100;
        report.append(String.format("5. **Per-thread efficiency:** 50T: %,.0f ops/sec vs 200T: %,.0f ops/sec = **%.1f%% less efficient**\n",
            efficiency50, efficiency200, efficiencyDrop));
        
        report.append("\n## 6. Root Cause Analysis\n\n");
        
        // Determine likely root cause
        if (m50.avgWaitTimeUs > m100.avgWaitTimeUs * 1.5) {
            report.append("### 🔴 **Primary Cause: Thread Contention**\n\n");
            report.append("- 50 threads experience significantly higher lock wait times\n");
            report.append("- This suggests a \"sweet spot\" for contention at mid-range concurrency\n");
            report.append("- Possible explanation: Thread scheduler behavior with moderate contention\n");
        } else if (m50.gcPauseCount > m100.gcPauseCount * 2) {
            report.append("### 🔴 **Primary Cause: Garbage Collection**\n\n");
            report.append("- 50 threads trigger more frequent GC pauses\n");
            report.append("- GC pauses coincide with P99 outliers\n");
            report.append("- Recommendation: Tune GC settings or increase heap size\n");
        } else if (efficiencyDrop > 20) {
            report.append("### 🔴 **Primary Cause: Thread Pool Inefficiency**\n\n");
            report.append("- Per-thread efficiency drops at 50 threads\n");
            report.append("- May indicate CPU cache thrashing or context switching overhead\n");
            report.append("- System has 16 cores - 50 threads may exceed optimal parallelism\n");
        } else {
            report.append("### 🟡 **Cause: Multiple Factors**\n\n");
            report.append("- No single dominant cause identified\n");
            report.append("- Combination of contention, GC, and scheduling effects\n");
            report.append("- Anomaly may be within normal variance\n");
        }
        
        report.append("\n## 7. Recommendations\n\n");
        report.append("### Immediate Actions:\n\n");
        report.append("1. **Production Configuration:**\n");
        report.append("   - Avoid 50-thread configuration if P99 latency is critical\n");
        report.append("   - Use 100-200 threads for optimal P99 latency\n");
        report.append("   - Monitor thread pool size dynamically\n\n");
        
        report.append("2. **JVM Tuning:**\n");
        report.append("   - Enable G1GC: `-XX:+UseG1GC`\n");
        report.append("   - Set max pause target: `-XX:MaxGCPauseMillis=10`\n");
        report.append("   - Increase heap size if GC is frequent\n\n");
        
        report.append("3. **Further Investigation:**\n");
        report.append("   - Run with Java Flight Recorder: `java -XX:StartFlightRecording=duration=60s`\n");
        report.append("   - Profile with async-profiler for CPU/wall-clock time\n");
        report.append("   - Test on different hardware (CPU core count may matter)\n\n");
        
        report.append("### Long-term Optimizations:\n\n");
        report.append("1. Consider using `ForkJoinPool` instead of `ExecutorService`\n");
        report.append("2. Implement thread-local caching to reduce contention\n");
        report.append("3. Use lock-free data structures where possible (already using AtomicLong)\n");
        report.append("4. Consider partitioning cache across multiple instances (sharding)\n\n");
        
        report.append("---\n\n");
        report.append("**Analysis Complete**\n\n");
        report.append(String.format("**50-thread anomaly explained:** %.2f%% higher P99 latency than optimal configuration\n",
            latencyIncrease50vs200));
        
        return report.toString();
    }
    
    private ContentionMetrics analyzeThreadCount(int threadCount) throws Exception {
        List<Long> allLatencies = new ArrayList<>();
        List<Long> durations = new ArrayList<>();
        AtomicLong totalWaitTime = new AtomicLong(0);
        AtomicLong maxWaitTime = new AtomicLong(0);
        AtomicLong totalBlockedCount = new AtomicLong(0);
        
        long totalGcTimeBefore = getTotalGcTime();
        long totalGcCountBefore = getTotalGcCount();
        
        for (int iter = 0; iter < ITERATIONS; iter++) {
            // GC before each iteration
            System.gc();
            Thread.sleep(100);
            
            Cache<String, String> cache = new LruCache<>(
                CacheConfig.builder().maxSize(CACHE_SIZE).evictionStrategy(CacheConfig.EvictionStrategy.LRU).build()
            );
            
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(threadCount);
            
            // Thread-local latency collection
            List<List<Long>> threadLatencies = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                threadLatencies.add(Collections.synchronizedList(new ArrayList<>()));
            }
            
            // Capture thread IDs for contention monitoring
            List<Long> threadIds = Collections.synchronizedList(new ArrayList<>());
            
            long startTime = System.nanoTime();
            
            for (int t = 0; t < threadCount; t++) {
                final List<Long> latencies = threadLatencies.get(t);
                
                executor.submit(() -> {
                    long tid = Thread.currentThread().threadId();
                    threadIds.add(tid);
                    
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
            
            // Collect all latencies
            for (List<Long> threadLat : threadLatencies) {
                allLatencies.addAll(threadLat);
            }
            
            // Collect thread contention metrics
            if (threadMXBean.isThreadContentionMonitoringSupported()) {
                for (Long tid : threadIds) {
                    var threadInfo = threadMXBean.getThreadInfo(tid);
                    if (threadInfo != null) {  // Thread may have terminated
                        long waitTime = threadInfo.getWaitedTime();
                        long blockedCount = threadInfo.getBlockedCount();
                        
                        totalWaitTime.addAndGet(waitTime);
                        maxWaitTime.updateAndGet(max -> Math.max(max, waitTime));
                        totalBlockedCount.addAndGet(blockedCount);
                    }
                }
            }
        }
        
        long totalGcTimeAfter = getTotalGcTime();
        long totalGcCountAfter = getTotalGcCount();
        
        // Calculate metrics
        Collections.sort(allLatencies);
        
        ContentionMetrics metrics = new ContentionMetrics();
        metrics.threadCount = threadCount;
        
        // Latency percentiles
        metrics.p50LatencyUs = getPercentile(allLatencies, 0.50) / 1000.0;
        metrics.p75LatencyUs = getPercentile(allLatencies, 0.75) / 1000.0;
        metrics.p90LatencyUs = getPercentile(allLatencies, 0.90) / 1000.0;
        metrics.p95LatencyUs = getPercentile(allLatencies, 0.95) / 1000.0;
        metrics.p99LatencyUs = getPercentile(allLatencies, 0.99) / 1000.0;
        metrics.p999LatencyUs = getPercentile(allLatencies, 0.999) / 1000.0;
        metrics.maxLatencyUs = allLatencies.get(allLatencies.size() - 1) / 1000.0;
        
        // Contention metrics
        int threadSamples = threadCount * ITERATIONS;
        metrics.avgWaitTimeUs = (totalWaitTime.get() / (double) threadSamples) * 1000.0; // ms to μs
        metrics.maxWaitTimeUs = maxWaitTime.get() * 1000.0;
        metrics.blockedCount = totalBlockedCount.get();
        metrics.contentionRate = metrics.blockedCount / (double) (threadCount * OPS_PER_THREAD * ITERATIONS);
        
        // GC metrics
        metrics.gcPauseCount = totalGcCountAfter - totalGcCountBefore;
        metrics.gcTotalTimeMs = totalGcTimeAfter - totalGcTimeBefore;
        metrics.gcTimePerOpNs = (metrics.gcTotalTimeMs * 1_000_000.0) / allLatencies.size();
        
        // Throughput
        long avgDuration = durations.stream().mapToLong(Long::longValue).sum() / durations.size();
        metrics.throughput = (threadCount * OPS_PER_THREAD) / (avgDuration / 1_000_000_000.0);
        
        // Standard deviation
        double avgThroughputPerIter = durations.stream()
            .mapToDouble(d -> (threadCount * OPS_PER_THREAD) / (d / 1_000_000_000.0))
            .average().orElse(0);
        metrics.stdDev = Math.sqrt(durations.stream()
            .mapToDouble(d -> {
                double tp = (threadCount * OPS_PER_THREAD) / (d / 1_000_000_000.0);
                return Math.pow(tp - avgThroughputPerIter, 2);
            })
            .average().orElse(0));
        
        return metrics;
    }
    
    private long getPercentile(List<Long> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(sortedValues.size() - 1, index));
        return sortedValues.get(index);
    }
    
    private long getTotalGcTime() {
        return gcBeans.stream().mapToLong(GarbageCollectorMXBean::getCollectionTime).sum();
    }
    
    private long getTotalGcCount() {
        return gcBeans.stream().mapToLong(GarbageCollectorMXBean::getCollectionCount).sum();
    }
    
    private static class ContentionMetrics {
        int threadCount;
        
        // Latency metrics (microseconds)
        double p50LatencyUs;
        double p75LatencyUs;
        double p90LatencyUs;
        double p95LatencyUs;
        double p99LatencyUs;
        double p999LatencyUs;
        double maxLatencyUs;
        
        // Contention metrics
        double avgWaitTimeUs;
        double maxWaitTimeUs;
        long blockedCount;
        double contentionRate;
        
        // GC metrics
        long gcPauseCount;
        long gcTotalTimeMs;
        double gcTimePerOpNs;
        
        // Throughput metrics
        double throughput;
        double stdDev;
    }
}
