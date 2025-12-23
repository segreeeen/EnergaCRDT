package at.felixb.energa.crdt;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BPlusListPerformanceTest {

    @Test
    void bPlusList_microBenchmark_smoke() {
        int n = 200_000;
        int t = 32;

        System.out.println("=== BPlusList Performance Test (JUnit) ===");
        System.out.println("n = " + n + ", t = " + t);

        // 1) Sequential append
        BPlusList<Integer> bplus = new BPlusList<>(t);
        List<Integer> arrayList = new ArrayList<>(n);

        long start = System.nanoTime();
        for (int i = 0; i < n; i++) bplus.add(i);
        long bplusAppendNs = System.nanoTime() - start;

        start = System.nanoTime();
        for (int i = 0; i < n; i++) arrayList.add(i);
        long arrayAppendNs = System.nanoTime() - start;

        System.out.printf(Locale.ROOT, "Append BPlusList:  %.3f ms%n", bplusAppendNs / 1_000_000.0);
        System.out.printf(Locale.ROOT, "Append ArrayList:  %.3f ms%n", arrayAppendNs / 1_000_000.0);

        assertEquals(n, bplus.size(), "BPlusList size after append");
        assertEquals(n, arrayList.size(), "ArrayList size after append");

        // 2) Random access
        Random rnd = new Random(42);
        int accesses = 100_000;

        start = System.nanoTime();
        long sum1 = 0;
        for (int i = 0; i < accesses; i++) {
            int idx = rnd.nextInt(n);
            sum1 += bplus.get(idx);
        }
        long bplusGetNs = System.nanoTime() - start;

        rnd = new Random(42);
        start = System.nanoTime();
        long sum2 = 0;
        for (int i = 0; i < accesses; i++) {
            int idx = rnd.nextInt(n);
            sum2 += arrayList.get(idx);
        }
        long arrayGetNs = System.nanoTime() - start;

        System.out.printf(Locale.ROOT, "Random get BPlusList: %.3f ms, sum=%d%n", bplusGetNs / 1_000_000.0, sum1);
        System.out.printf(Locale.ROOT, "Random get ArrayList: %.3f ms, sum=%d%n", arrayGetNs / 1_000_000.0, sum2);

        // The sums should match exactly (same random indices, same values)
        assertEquals(sum2, sum1, "Random-access sums must match");

        // 3) Random inserts
        bplus = new BPlusList<>(t);
        arrayList = new ArrayList<>();
        rnd = new Random(42);

        start = System.nanoTime();
        for (int i = 0; i < n; i++) {
            int size = bplus.size();
            int idx = (size == 0) ? 0 : rnd.nextInt(size + 1);
            bplus.add(idx, i);
        }
        long bplusInsertNs = System.nanoTime() - start;

        rnd = new Random(42);
        start = System.nanoTime();
        for (int i = 0; i < n; i++) {
            int size = arrayList.size();
            int idx = (size == 0) ? 0 : rnd.nextInt(size + 1);
            arrayList.add(idx, i);
        }
        long arrayInsertNs = System.nanoTime() - start;

        System.out.printf(Locale.ROOT, "Random insert BPlusList: %.3f ms (n=%d)%n", bplusInsertNs / 1_000_000.0, n);
        System.out.printf(Locale.ROOT, "Random insert ArrayList: %.3f ms (n=%d)%n", arrayInsertNs / 1_000_000.0, n);

        assertEquals(n, bplus.size(), "BPlusList size after random inserts");
        assertEquals(n, arrayList.size(), "ArrayList size after random inserts");

        // Optional integrity checks if you still have them
        // (If you removed validate/isValid in your current BPlusList, just delete these two lines.)
        bplus.validate();
        assertTrue(bplus.isValid(), "BPlusList should be valid after random inserts");
    }
}
