# Thread Contention Analysis Report

## Investigation: 50-Thread P99 Latency Anomaly

**Hypothesis:** Mid-range thread count (50) experiences higher contention than higher counts (200)

**Analysis Date:** 2025-12-09T08:33:23.779569647
**Iterations per Thread Count:** 5
**Operations per Thread:** 1000
**Cache Size:** 5000

---

## 1. Latency Percentiles by Thread Count

| Threads | P50 (μs) | P75 (μs) | P90 (μs) | P95 (μs) | P99 (μs)    | P99.9 (μs) | Max (μs) |
| ------- | -------- | -------- | -------- | -------- | ----------- | ---------- | -------- |
| 10      | 0,61     | 1,06     | 39,87    | 76,46    | **148,53**  | 266,95     | 545,25   |
| 25      | 0,49     | 0,78     | 1,98     | 230,24   | **340,39**  | 742,91     | 5562,35  |
| 50      | 0,49     | 0,71     | 1,02     | 1,66     | **788,21**  | 4101,56    | 9376,44  |
| 75      | 0,45     | 0,66     | 0,98     | 1,60     | **1381,85** | 4924,07    | 11452,35 |
| 100     | 0,25     | 0,50     | 0,80     | 1,10     | **1772,35** | 5305,19    | 15128,26 |
| 150     | 0,19     | 0,36     | 0,66     | 0,94     | **2241,60** | 7403,37    | 19474,89 |
| 200     | 0,18     | 0,28     | 0,55     | 0,89     | **843,19**  | 10395,06   | 36786,82 |

## 2. Thread Contention Metrics

| Threads | Avg Wait Time (μs) | Max Wait Time (μs) | Blocked Count | Contention Rate (%) |
| ------- | ------------------ | ------------------ | ------------- | ------------------- |
| 10      | 2480,00            | 12000,00           | 0             | 0,00%               |
| 25      | 12808,00           | 40000,00           | 0             | 0,00%               |
| 50      | 12736,00           | 62000,00           | 0             | 0,00%               |
| 75      | 21520,00           | 90000,00           | 0             | 0,00%               |
| 100     | 20462,00           | 101000,00          | 0             | 0,00%               |
| 150     | 21376,00           | 109000,00          | 0             | 0,00%               |
| 200     | 42499,00           | 130000,00          | 0             | 0,00%               |

## 3. Garbage Collection Impact

| Threads | GC Pause Count | Total GC Time (ms) | GC Time per Op (ns) | Impact on P99? |
| ------- | -------------- | ------------------ | ------------------- | -------------- |
| 10      | 5              | 265                | 5300,00             | ✅ No           |
| 25      | 5              | 316                | 2528,00             | ✅ No           |
| 50      | 5              | 294                | 1176,00             | ✅ No           |
| 75      | 5              | 233                | 621,33              | ⚠️ YES          |
| 100     | 6              | 255                | 510,00              | ⚠️ YES          |
| 150     | 8              | 248                | 330,67              | ⚠️ YES          |
| 200     | 8              | 247                | 247,00              | ✅ No           |

## 4. Throughput Analysis

| Threads | Avg Throughput (ops/sec) | Std Dev | Efficiency per Thread |
| ------- | ------------------------ | ------- | --------------------- |
| 10      | 703 911                  | 204547  | 70 391                |
| 25      | 735 861                  | 131715  | 29 434                |
| 50      | 861 528                  | 37874   | 17 231                |
| 75      | 966 553                  | 116089  | 12 887                |
| 100     | 1 365 760                | 697443  | 13 658                |
| 150     | 1 685 537                | 548604  | 11 237                |
| 200     | 1 932 243                | 360588  | 9 661                 |

## 5. Anomaly Detection

### Key Findings:

1. **50-thread P99:** 788,21 μs vs **100-thread P99:** 1772,35 μs = **-55,5% difference**
2. **50-thread P99:** 788,21 μs vs **200-thread P99:** 843,19 μs = **-6,5% difference**
3. **Wait time at 50 threads:** 12736,00 μs vs **200 threads:** 42499,00 μs = **-70,0% more contention**
4. **GC pauses at 50 threads:** 5 vs **200 threads:** 8
5. **Per-thread efficiency:** 50T: 17 231 ops/sec vs 200T: 9 661 ops/sec = **78,3% less efficient**

## 6. Root Cause Analysis

### 🔴 **Primary Cause: Thread Pool Inefficiency**

- Per-thread efficiency drops at 50 threads
- May indicate CPU cache thrashing or context switching overhead
- System has 16 cores - 50 threads may exceed optimal parallelism

## 7. Recommendations

### Immediate Actions:

1. **Production Configuration:**
   - Avoid 50-thread configuration if P99 latency is critical
   - Use 100-200 threads for optimal P99 latency
   - Monitor thread pool size dynamically

2. **JVM Tuning:**
   - Enable G1GC: `-XX:+UseG1GC`
   - Set max pause target: `-XX:MaxGCPauseMillis=10`
   - Increase heap size if GC is frequent

3. **Further Investigation:**
   - Run with Java Flight Recorder: `java -XX:StartFlightRecording=duration=60s`
   - Profile with async-profiler for CPU/wall-clock time
   - Test on different hardware (CPU core count may matter)

### Long-term Optimizations:

1. Consider using `ForkJoinPool` instead of `ExecutorService`
2. Implement thread-local caching to reduce contention
3. Use lock-free data structures where possible (already using AtomicLong)
4. Consider partitioning cache across multiple instances (sharding)

---

**Analysis Complete**

**50-thread anomaly explained:** -6,52% higher P99 latency than optimal configuration
