package at.felixb.energa.crdt;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("perf")
public class CrdtAnchorPerformanceTest {

    // sizes to test (you can trim for quicker runs)
    private static final int[] SIZES = { 10_000, 50_000, 100_000, 200_000, 500_000, 1_000_000 };

    private static final long SEED = 42L;

    private static final long WARMUP_TIME_MS = 800;
    private static final int MEASURE_RUNS = 7;
    private static final int INNER_ITERATIONS = 6;

    // For per-op measurements: how many anchor resolutions per sample
    private static final int OPS_PER_SAMPLE = 50_000;

    // -------------------------------------------------------------------------
    // Stats printing (same spirit as your improved harness)
    // -------------------------------------------------------------------------

    private static void gcHint() {
        System.gc();
        try { Thread.sleep(15); } catch (InterruptedException ignored) {}
    }

    private static void warmupFor(long warmupMs, Runnable r) {
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(warmupMs);
        while (System.nanoTime() < end) r.run();
    }

    private static long percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil(p * sorted.size()) - 1;
        idx = Math.max(0, Math.min(idx, sorted.size() - 1));
        return sorted.get(idx);
    }

    private static void printNsPerOpStats(String label, int n, List<Long> nsPerOpSamples) {
        List<Long> s = new ArrayList<>(nsPerOpSamples);
        s.sort(Long::compareTo);

        long min = s.get(0);
        long med = percentile(s, 0.50);
        long p90 = percentile(s, 0.90);
        long p95 = percentile(s, 0.95);
        long max = s.get(s.size() - 1);
        double avg = s.stream().mapToLong(x -> x).average().orElse(0.0);

        System.out.printf(
                Locale.ROOT,
                "%s | N=%d | samples=%d | avg=%.1f ns/op | med=%d ns/op | p90=%d | p95=%d | min=%d | max=%d%n",
                label, n, s.size(), avg, med, p90, p95, min, max
        );
    }

    // -------------------------------------------------------------------------
    // Workload profiles (ops generators)
    // -------------------------------------------------------------------------

    private static CrdtNodeId rootId() {
        return new CrdtNodeId(Document.ROOT_SITE_ID, 0);
    }

    /**
     * PROFILE: CHAIN
     * ROOT->A->B->C->... (each insert under previous node)
     * This is the "text grows at end" friendly case.
     */
    private static List<CrdtOperation> generateChainInserts(int n, UUID site) {
        List<CrdtOperation> ops = new ArrayList<>(n);
        CrdtNodeId root = rootId();

        CrdtNodeId parent = root;
        for (int i = 1; i <= n; i++) {
            CrdtNodeId id = new CrdtNodeId(site, i);
            ops.add(new CrdtInsertOp(parent, id, 'a'));
            parent = id;
        }
        return ops;
    }

    /**
     * PROFILE: BUSHY
     * Many siblings under root: ROOT has N children.
     * This stresses TreeMap ordering + dfs insert index prefix sum.
     */
    private static List<CrdtOperation> generateBushyRootInserts(int n, UUID site) {
        List<CrdtOperation> ops = new ArrayList<>(n);
        CrdtNodeId root = rootId();

        for (int i = 1; i <= n; i++) {
            CrdtNodeId id = new CrdtNodeId(site, i);
            ops.add(new CrdtInsertOp(root, id, 'a'));
        }
        return ops;
    }

    /**
     * PROFILE: TOMBSTONE-HEAVY
     * Build chain, then delete a percentage of nodes (e.g. 70%).
     */
    private static List<CrdtOperation> generateTombstoneHeavy(int n, UUID site, long seed, double deleteRatio) {
        List<CrdtOperation> ops = new ArrayList<>(n + (int)(n * deleteRatio));
        ops.addAll(generateChainInserts(n, site));

        Random rnd = new Random(seed);
        for (int i = 1; i <= n; i++) {
            if (rnd.nextDouble() < deleteRatio) {
                ops.add(new CrdtDeleteOp(new CrdtNodeId(site, i)));
            }
        }
        return ops;
    }

    // -------------------------------------------------------------------------
    // Anchor sets (visible/deleted/mixed)
    // -------------------------------------------------------------------------

    private static List<Anchor> buildMixedAnchors(Document doc, long seed, int count) {
        Random rnd = new Random(seed);

        // full (with deleted) and visible-only
        CrdtDocument d = (CrdtDocument) doc;
        List<CrdtNode> full = d.getLinearOrder();
        List<CrdtNode> visible = d.getActiveOnlyLinearOrder();
        int visibleN = visible.size();

        List<Anchor> anchors = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            int mode = rnd.nextInt(3);
            Gravity g = rnd.nextBoolean() ? Gravity.LEFT : Gravity.RIGHT;

            if (mode == 0 && visibleN > 0) {
                // createAnchor based on caret index (visible space)
                int caret = rnd.nextInt(visibleN + 1);
                anchors.add(d.createAnchor(caret, g));
            } else if (mode == 1 && !full.isEmpty()) {
                // direct anchor to random node (might be deleted)
                CrdtNode n = full.get(rnd.nextInt(full.size()));
                anchors.add(new Anchor(n.getNodeId(), g));
            } else if (!visible.isEmpty()) {
                // direct anchor to visible node (pure hot-path)
                CrdtNode n = visible.get(rnd.nextInt(visible.size()));
                anchors.add(new Anchor(n.getNodeId(), g));
            } else {
                anchors.add(new Anchor(rootId(), Gravity.LEFT));
            }
        }

        return anchors;
    }

    // -------------------------------------------------------------------------
    // Bench 1: resolveAnchor() throughput (ns/op) for a mixed anchor set
    // -------------------------------------------------------------------------

    @Test
    void benchmarkResolveAnchor_nsPerOp_chain_bushy_tombstone() {
        runResolveAnchorBench("CHAIN", (n) -> generateChainInserts(n, UUID.randomUUID()));
        runResolveAnchorBench("BUSHY", (n) -> generateBushyRootInserts(n, UUID.randomUUID()));
        runResolveAnchorBench("TOMBSTONE70", (n) -> generateTombstoneHeavy(n, UUID.randomUUID(), SEED, 0.70));
    }

    private interface OpsFactory { List<CrdtOperation> create(int n); }

    private void runResolveAnchorBench(String profile, OpsFactory opsFactory) {
        for (int n : SIZES) {
            List<CrdtOperation> ops = opsFactory.create(n);

            // Build once per N
            Document doc = Document.fromLog(ops);
            assertNotNull(doc);

            // Prepare anchors once (so we only measure resolveAnchor)
            List<Anchor> anchors = buildMixedAnchors(doc, SEED, OPS_PER_SAMPLE);
            CrdtDocument crdt = (CrdtDocument) doc;

            warmupFor(WARMUP_TIME_MS, () -> {
                int sum = 0;
                for (Anchor a : anchors) sum += crdt.resolveAnchor(a);
                if (sum == 42) throw new AssertionError("unreachable");
            });

            List<Long> nsPerOpSamples = new ArrayList<>(MEASURE_RUNS * INNER_ITERATIONS);
            for (int run = 0; run < MEASURE_RUNS; run++) {
                gcHint();
                for (int it = 0; it < INNER_ITERATIONS; it++) {
                    long start = System.nanoTime();
                    int sum = 0;
                    for (Anchor a : anchors) sum += crdt.resolveAnchor(a);
                    long end = System.nanoTime();
                    if (sum == 42) throw new AssertionError("unreachable");

                    long totalNs = (end - start);
                    nsPerOpSamples.add(totalNs / anchors.size());
                }
            }

            printNsPerOpStats("resolveAnchor " + profile, n, nsPerOpSamples);
        }
    }

    // -------------------------------------------------------------------------
    // Bench 2: indexOfVisible() hot-path (ns/op)
    //   - sample visible nodes and repeatedly ask for indexOfVisible(node)
    // -------------------------------------------------------------------------

    @Test
    void benchmarkIndexOfVisible_nsPerOp_chain_bushy_tombstone() {
        runIndexOfVisibleBench("CHAIN", (n) -> generateChainInserts(n, UUID.randomUUID()));
        runIndexOfVisibleBench("BUSHY", (n) -> generateBushyRootInserts(n, UUID.randomUUID()));
        runIndexOfVisibleBench("TOMBSTONE70", (n) -> generateTombstoneHeavy(n, UUID.randomUUID(), SEED, 0.70));
    }

    private void runIndexOfVisibleBench(String profile, OpsFactory opsFactory) {
        for (int n : SIZES) {
            List<CrdtOperation> ops = opsFactory.create(n);

            Document doc = Document.fromLog(ops);
            assertNotNull(doc);

            CrdtDocument crdt = (CrdtDocument) doc;

            List<CrdtNode> visible = crdt.getActiveOnlyLinearOrder();
            if (visible.isEmpty()) {
                System.out.printf(Locale.ROOT, "indexOfVisible %s | N=%d | (no visible nodes)%n", profile, n);
                continue;
            }

            // sample nodes once
            Random rnd = new Random(SEED);
            int sampleCount = Math.min(OPS_PER_SAMPLE, visible.size());
            List<CrdtNode> sample = new ArrayList<>(sampleCount);
            for (int i = 0; i < sampleCount; i++) sample.add(visible.get(rnd.nextInt(visible.size())));

            warmupFor(WARMUP_TIME_MS, () -> {
                int sum = 0;
                for (CrdtNode node : sample) sum += crdt.indexOfVisible(node); // if not accessible, use a getter or wrapper
                if (sum == 42) throw new AssertionError("unreachable");
            });

            List<Long> nsPerOpSamples = new ArrayList<>(MEASURE_RUNS * INNER_ITERATIONS);
            for (int run = 0; run < MEASURE_RUNS; run++) {
                gcHint();
                for (int it = 0; it < INNER_ITERATIONS; it++) {
                    long start = System.nanoTime();
                    int sum = 0;
                    for (CrdtNode node : sample) sum += crdt.indexOfVisible(node); // same note
                    long end = System.nanoTime();
                    if (sum == 42) throw new AssertionError("unreachable");
                    nsPerOpSamples.add((end - start) / (long) sample.size());
                }
            }

            printNsPerOpStats("indexOfVisible " + profile, n, nsPerOpSamples);
        }
    }
}
