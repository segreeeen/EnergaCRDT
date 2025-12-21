package at.felixb.simplerga;


import at.felixb.energa.bpluslist.BPlusList;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
public class BPlusListPerformance {

    public static void main(String[] args) {
        int n = 200_000;
        int t = 32; // Ordnung des B+-Trees

        System.out.println("=== BPlusList Performance Test ===");
        System.out.println("n = " + n + ", t = " + t);

        BPlusList<Integer> bplus = new BPlusList<>(t);
        List<Integer> arrayList = new ArrayList<>();

        // 1) Sequentielles Append
        long start = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            bplus.add(i);
        }
        long bplusAppend = System.currentTimeMillis() - start;

        start = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            arrayList.add(i);
        }
        long arrayAppend = System.currentTimeMillis() - start;

        System.out.println("Append BPlusList:  " + bplusAppend + " ms");
        System.out.println("Append ArrayList:  " + arrayAppend + " ms");

        // 2) Random Access
        Random rnd = new Random(42);
        int accesses = 100_000;

        start = System.currentTimeMillis();
        long sum1 = 0;
        for (int i = 0; i < accesses; i++) {
            int idx = rnd.nextInt(n);
            sum1 += bplus.get(idx);
        }
        long bplusGet = System.currentTimeMillis() - start;

        rnd = new Random(42);
        start = System.currentTimeMillis();
        long sum2 = 0;
        for (int i = 0; i < accesses; i++) {
            int idx = rnd.nextInt(n);
            sum2 += arrayList.get(idx);
        }
        long arrayGet = System.currentTimeMillis() - start;

        System.out.println("Random get BPlusList: " + bplusGet + " ms, sum=" + sum1);
        System.out.println("Random get ArrayList: " + arrayGet + " ms, sum=" + sum2);

        // 3) Random Inserts
        bplus = new BPlusList<>(t);
        arrayList = new ArrayList<>();
        rnd = new Random(42);

        start = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            int size = bplus.size();
            int idx = size == 0 ? 0 : rnd.nextInt(size + 1);
            bplus.add(idx, i);
        }
        long bplusInsert = System.currentTimeMillis() - start;

        rnd = new Random(42);
        start = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            int size = arrayList.size();
            int idx = size == 0 ? 0 : rnd.nextInt(size + 1);
            arrayList.add(idx, i);
        }
        long arrayInsert = System.currentTimeMillis() - start;

        System.out.println("Random insert BPlusList: " + bplusInsert + " ms (n=" + n + ")");
        System.out.println("Random insert ArrayList: " + arrayInsert + " ms (n=" + n + ")");

        bplus.validate();
        System.out.println("valid: " + bplus.isValid());
    }
}
