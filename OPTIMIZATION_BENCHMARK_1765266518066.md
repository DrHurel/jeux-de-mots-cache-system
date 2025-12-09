# Cache Optimization Benchmark Results

**Date:** 2025-12-09T08:48:24.394119614
**Cache Size:** 5000
**Operations per Thread:** 1000
**Iterations:** 3

---

## 1. Throughput Comparison

| Threads | Baseline (ops/sec) | ThreadLocal (ops/sec) | Sharded (ops/sec) | ForkJoin (ops/sec) |
| ------- | ------------------ | --------------------- | ----------------- | ------------------ |
| 10      | 378 078            | 926 400               | 1 671 719         | 1 138 113          |
| 25      | 602 860            | 789 565               | 2 159 991         | 1 478 081          |
| 50      | 823 380            | 980 075               | 2 157 884         | 970 927            |
| 100     | 1 923 609          | 1 190 168             | 1 441 478         | 1 163 299          |
| 200     | 1 791 017          | 1 517 948             | 2 455 484         | 1 615 860          |

## 2. P99 Latency Comparison

| Threads | Baseline (μs) | ThreadLocal (μs) | Sharded (μs) | ForkJoin (μs) |
| ------- | ------------- | ---------------- | ------------ | ------------- |
| 10      | 247,24        | 117,75           | 8,75         | 91,79         |
| 25      | 436,83        | 402,47           | 139,81       | 199,70        |
| 50      | 800,04        | 761,00           | 393,82       | 660,75        |
| 100     | 512,49        | 1875,24          | 902,84       | 1552,61       |
| 200     | 633,58        | 708,47           | 819,75       | 1117,00       |

## 3. Per-Thread Efficiency

| Threads | Baseline | ThreadLocal | Sharded | ForkJoin |
| ------- | -------- | ----------- | ------- | -------- |
| 10      | 37 808   | 92 640      | 167 172 | 113 811  |
| 25      | 24 114   | 31 583      | 86 400  | 59 123   |
| 50      | 16 468   | 19 602      | 43 158  | 19 419   |
| 100     | 19 236   | 11 902      | 14 415  | 11 633   |
| 200     | 8 955    | 7 590       | 12 277  | 8 079    |

## 4. Improvement Analysis

### 10 Threads

- **ThreadLocal:** +145,0% throughput ✅
- **Sharded:** +342,2% throughput ✅
- **ForkJoin:** +201,0% throughput ✅

### 25 Threads

- **ThreadLocal:** +31,0% throughput ✅
- **Sharded:** +258,3% throughput ✅
- **ForkJoin:** +145,2% throughput ✅

### 50 Threads

- **ThreadLocal:** +19,0% throughput ✅
- **Sharded:** +162,1% throughput ✅
- **ForkJoin:** +17,9% throughput ✅

### 100 Threads

- **ThreadLocal:** -38,1% throughput ❌
- **Sharded:** -25,1% throughput ❌
- **ForkJoin:** -39,5% throughput ❌

### 200 Threads

- **ThreadLocal:** -15,2% throughput ❌
- **Sharded:** +37,1% throughput ✅
- **ForkJoin:** -9,8% throughput ❌

## 5. Recommendations

### Best Optimization by Thread Count:

- **10 threads:** ShardedCache (+342,2%)
- **25 threads:** ShardedCache (+258,3%)
- **50 threads:** ShardedCache (+162,1%)
- **100 threads:** Baseline (no optimization needed) (+0,0%)
- **200 threads:** ShardedCache (+37,1%)

### General Guidelines:

1. **ThreadLocalCache:** Best for read-heavy workloads with temporal locality
2. **ShardedCache:** Best for high-concurrency write-heavy workloads
3. **ForkJoinPool:** Best for uniform workloads with many small tasks
4. **Baseline:** Sufficient for <25 threads with mixed read/write

---

**Benchmark Complete**
