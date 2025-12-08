package at.felixb.simplerga;

import at.felixb.energa.crdt.CrdtInsertOp;
import at.felixb.energa.crdt.CrdtNodeId;
import at.felixb.energa.crdt.CrdtOperation;
import at.felixb.energa.crdt.Document;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CrdtPerformanceTest {

    private static final int[] SIZES = { 1_000, 5_000, 10_000, 20_000, 50_000, 100_000, 200_000, 500_000, 1_000_000 }
            ;
    private static final int NUM_SITES = 3;
    private static final int WARMUP_RUNS = 2;
    private static final int MEASURE_RUNS = 5;
    private static final long RANDOM_SEED = 42L;

    // #### Helper: IDs / Random Ops ###########################################

    private CrdtNodeId rootId() {
        return new CrdtNodeId(Document.ROOT_SITE_ID, 0);
    }

    private static class SiteState {
        final UUID siteId;
        int counter = 1; // 0 ist Root vorbehalten

        SiteState(UUID siteId) {
            this.siteId = siteId;
        }

        CrdtNodeId nextNodeId() {
            return new CrdtNodeId(siteId, counter++);
        }
    }

    private List<CrdtOperation> generateRandomInsertOps(int numSites, int numOps, long seed) {
        Random random = new Random(seed);

        List<CrdtOperation> ops = new ArrayList<>();
        List<SiteState> sites = new ArrayList<>();
        for (int i = 0; i < numSites; i++) {
            sites.add(new SiteState(UUID.randomUUID()));
        }

        // Root existiert immer
        List<CrdtNodeId> existingNodeIds = new ArrayList<>();
        existingNodeIds.add(rootId());

        for (int i = 0; i < numOps; i++) {
            SiteState site = sites.get(random.nextInt(sites.size()));

            CrdtNodeId parentId = existingNodeIds.get(random.nextInt(existingNodeIds.size()));
            CrdtNodeId newId = site.nextNodeId();
            existingNodeIds.add(newId);

            char ch = randomPrintableChar(random);

            ops.add(new CrdtInsertOp(parentId, newId, ch));
        }

        return ops;
    }

    private char randomPrintableChar(Random random) {
        int base = 97; // 'a'
        int range = 26;
        return (char) (base + random.nextInt(range));
    }

    // #### Benchmark-Helfer ####################################################

    private long nanosToMillis(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(nanos);
    }

    private double avg(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }

    private void printStats(String label, int size, List<Long> samples) {
        double avgMs = avg(samples) / 1_000_000.0;
        long minNs = samples.stream().mapToLong(Long::longValue).min().orElse(0L);
        long maxNs = samples.stream().mapToLong(Long::longValue).max().orElse(0L);

        System.out.printf(
                Locale.ROOT,
                "%s | N=%d | runs=%d | avg=%.3f ms | min=%d ms | max=%d ms%n",
                label,
                size,
                samples.size(),
                avgMs,
                nanosToMillis(minNs),
                nanosToMillis(maxNs)
        );
    }

    // #### 1) apply()-Performance ##############################################

    @Test
    void benchmarkApplyInsertOps() {
        for (int size : SIZES) {
            List<CrdtOperation> ops = generateRandomInsertOps(NUM_SITES, size, RANDOM_SEED);

            // Warmup
            for (int i = 0; i < WARMUP_RUNS; i++) {
                Document doc = Document.create();
                for (CrdtOperation op : ops) {
                    doc.apply(op);
                }
            }

            // Messung
            List<Long> samples = new ArrayList<>();
            for (int run = 0; run < MEASURE_RUNS; run++) {
                Document doc = Document.create();
                long start = System.nanoTime();
                for (CrdtOperation op : ops) {
                    doc.apply(op);
                }
                long end = System.nanoTime();
                samples.add(end - start);
            }

            printStats("apply() random inserts", size, samples);
        }
    }

    // #### 2) fromLog()-Performance ###########################################

    @Test
    void benchmarkFromLogReplay() {
        for (int size : SIZES) {
            List<CrdtOperation> ops = generateRandomInsertOps(NUM_SITES, size, RANDOM_SEED);

            // Warmup
            for (int i = 0; i < WARMUP_RUNS; i++) {
                Document doc = Document.fromLog(ops);
                assertNotNull(doc);
            }

            // Messung
            List<Long> samples = new ArrayList<>();
            for (int run = 0; run < MEASURE_RUNS; run++) {
                long start = System.nanoTime();
                Document doc = Document.fromLog(ops);
                long end = System.nanoTime();
                assertNotNull(doc);
                samples.add(end - start);
            }

            printStats("Document.fromLog()", size, samples);
        }
    }

    // #### 3) render()-Performance ############################################

    @Test
    void benchmarkRenderOnBuiltDocument() {
        for (int size : SIZES) {
            List<CrdtOperation> ops = generateRandomInsertOps(NUM_SITES, size, RANDOM_SEED);

            // Baue einmal ein Dokument zu diesem Log
            Document doc = Document.fromLog(ops);
            assertNotNull(doc);

            // Warmup
            for (int i = 0; i < WARMUP_RUNS; i++) {
                doc.render();
            }

            // Messung
            List<Long> samples = new ArrayList<>();
            for (int run = 0; run < MEASURE_RUNS; run++) {
                long start = System.nanoTime();
                String s = doc.render();
                long end = System.nanoTime();
                // optional: leichte Kontrolle, dass wir nicht rausoptimiert werden
                if (s.length() == -1) {
                    throw new AssertionError("unreachable");
                }
                samples.add(end - start);
            }

            printStats("render()", size, samples);
        }
    }
}
