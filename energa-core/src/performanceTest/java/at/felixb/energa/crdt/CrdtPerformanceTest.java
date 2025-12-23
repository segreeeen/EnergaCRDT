package at.felixb.energa.crdt;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("perf")
public class CrdtPerformanceTest {

    private static final int[] SIZES = { 1_000, 5_000, 10_000, 20_000, 50_000, 100_000, 200_000, 500_000, 1_000_000 };
    private static final int NUM_SITES = 3;

    // warmup/measurement strategy
    private static final long WARMUP_TIME_MS = 800;     // time-based warmup is more stable than "2 runs"
    private static final int MEASURE_RUNS = 7;          // a bit more runs => better quantiles
    private static final int INNER_ITERATIONS = 8;      // do multiple iterations per run to reduce single-GC distortion
    private static final long RANDOM_SEED = 42L;

    // -------------- Helpers: ops generation ----------------

    private CrdtNodeId rootId() {
        return new CrdtNodeId(Document.ROOT_SITE_ID, 0);
    }

    private static class SiteState {
        final UUID siteId;
        int counter = 1;

        SiteState(UUID siteId) {
            this.siteId = siteId;
        }

        CrdtNodeId nextNodeId() {
            return new CrdtNodeId(siteId, counter++);
        }
    }

    private List<CrdtOperation> generateRandomInsertOps(int numSites, int numOps, long seed) {
        Random random = new Random(seed);

        List<CrdtOperation> ops = new ArrayList<>(numOps);
        List<SiteState> sites = new ArrayList<>(numSites);
        for (int i = 0; i < numSites; i++) sites.add(new SiteState(UUID.randomUUID()));

        List<CrdtNodeId> existingNodeIds = new ArrayList<>(numOps + 1);
        existingNodeIds.add(rootId());

        for (int i = 0; i < numOps; i++) {
            SiteState site = sites.get(random.nextInt(sites.size()));
            CrdtNodeId parentId = existingNodeIds.get(random.nextInt(existingNodeIds.size()));
            CrdtNodeId newId = site.nextNodeId();
            existingNodeIds.add(newId);

            char ch = (char) ('a' + random.nextInt(26));
            ops.add(new CrdtInsertOp(parentId, newId, ch));
        }

        return ops;
    }

    // -------------- Stats helpers ----------------

    private static void gcHint() {
        // not guaranteed, but helps reduce noise between runs
        System.gc();
        try { Thread.sleep(15); } catch (InterruptedException ignored) {}
    }

    private static double nanosToMs(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static double nanosToUs(long nanos) {
        return nanos / 1_000.0;
    }

    private static long percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil(p * sorted.size()) - 1;
        idx = Math.max(0, Math.min(idx, sorted.size() - 1));
        return sorted.get(idx);
    }

    private static void printStats(String label, int size, List<Long> samplesNanos) {
        List<Long> s = new ArrayList<>(samplesNanos);
        s.sort(Long::compareTo);

        long min = s.get(0);
        long med = percentile(s, 0.50);
        long p90 = percentile(s, 0.90);
        long p95 = percentile(s, 0.95);
        long max = s.get(s.size() - 1);

        double avg = s.stream().mapToLong(x -> x).average().orElse(0.0);

        // choose unit: below 1ms => print us, else ms
        boolean useUs = nanosToMs(med) < 1.0;

        if (useUs) {
            System.out.printf(
                    Locale.ROOT,
                    "%s | N=%d | samples=%d | avg=%.1f µs | med=%.1f µs | p90=%.1f µs | p95=%.1f µs | min=%.1f µs | max=%.1f µs%n",
                    label, size, s.size(),
                    nanosToUs((long) avg), nanosToUs(med), nanosToUs(p90), nanosToUs(p95), nanosToUs(min), nanosToUs(max)
            );
        } else {
            System.out.printf(
                    Locale.ROOT,
                    "%s | N=%d | samples=%d | avg=%.3f ms | med=%.3f ms | p90=%.3f ms | p95=%.3f ms | min=%.3f ms | max=%.3f ms%n",
                    label, size, s.size(),
                    nanosToMs((long) avg), nanosToMs(med), nanosToMs(p90), nanosToMs(p95), nanosToMs(min), nanosToMs(max)
            );
        }
    }

    // -------------- Warmup helpers ----------------

    private static void warmupFor(long warmupMs, Runnable r) {
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(warmupMs);
        int it = 0;
        while (System.nanoTime() < end) {
            r.run();
            it++;
            // tiny pause occasionally to avoid CPU thermal boosting artifacts / OS scheduling weirdness
            if ((it & 63) == 0) Thread.onSpinWait();
        }
    }

    // -------------------------------------------------------------------------
    // 1) apply() random inserts
    // -------------------------------------------------------------------------

    @Test
    void benchmarkApplyInsertOps_moreStable() {
        for (int size : SIZES) {
            List<CrdtOperation> ops = generateRandomInsertOps(NUM_SITES, size, RANDOM_SEED);

            warmupFor(WARMUP_TIME_MS, () -> {
                Document doc = Document.create();
                for (CrdtOperation op : ops) doc.apply(op);
                assertNotNull(doc);
            });

            List<Long> samples = new ArrayList<>(MEASURE_RUNS * INNER_ITERATIONS);
            for (int run = 0; run < MEASURE_RUNS; run++) {
                gcHint();

                for (int it = 0; it < INNER_ITERATIONS; it++) {
                    Document doc = Document.create();
                    long start = System.nanoTime();
                    for (CrdtOperation op : ops) doc.apply(op);
                    long end = System.nanoTime();
                    assertNotNull(doc);
                    samples.add(end - start);
                }
            }

            printStats("apply() random inserts", size, samples);
        }
    }

    // -------------------------------------------------------------------------
    // 2) fromLog replay
    // -------------------------------------------------------------------------

    @Test
    void benchmarkFromLogReplay_moreStable() {
        for (int size : SIZES) {
            List<CrdtOperation> ops = generateRandomInsertOps(NUM_SITES, size, RANDOM_SEED);

            warmupFor(WARMUP_TIME_MS, () -> {
                Document doc = Document.fromLog(ops);
                assertNotNull(doc);
            });

            List<Long> samples = new ArrayList<>(MEASURE_RUNS * INNER_ITERATIONS);
            for (int run = 0; run < MEASURE_RUNS; run++) {
                gcHint();

                for (int it = 0; it < INNER_ITERATIONS; it++) {
                    long start = System.nanoTime();
                    Document doc = Document.fromLog(ops);
                    long end = System.nanoTime();
                    assertNotNull(doc);
                    samples.add(end - start);
                }
            }

            printStats("Document.fromLog()", size, samples);
        }
    }

    // -------------------------------------------------------------------------
    // 3) render() on built document
    // -------------------------------------------------------------------------

    @Test
    void benchmarkRenderOnBuiltDocument_moreStable() {
        for (int size : SIZES) {
            List<CrdtOperation> ops = generateRandomInsertOps(NUM_SITES, size, RANDOM_SEED);

            Document doc = Document.fromLog(ops);
            assertNotNull(doc);

            warmupFor(WARMUP_TIME_MS, () -> {
                String s = doc.render();
                if (s.length() == -1) throw new AssertionError("unreachable");
            });

            // Measure: do multiple renders per sample to reduce timer/GC noise
            List<Long> samples = new ArrayList<>(MEASURE_RUNS * INNER_ITERATIONS);
            for (int run = 0; run < MEASURE_RUNS; run++) {
                gcHint();

                for (int it = 0; it < INNER_ITERATIONS; it++) {
                    long start = System.nanoTime();

                    // batch within one sample
                    int batch = 10;
                    int totalLen = 0;
                    for (int j = 0; j < batch; j++) {
                        totalLen += doc.render().length();
                    }

                    long end = System.nanoTime();
                    if (totalLen == -1) throw new AssertionError("unreachable");

                    // store per-render time
                    samples.add((end - start) / batch);
                }
            }

            printStats("render()", size, samples);
        }
    }
}
