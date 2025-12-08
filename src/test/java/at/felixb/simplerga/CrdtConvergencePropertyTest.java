package at.felixb.simplerga;

import at.felixb.energa.crdt.*;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CrdtConvergencePropertyTest {

    private static final int RANDOM_SEED = 42;
    private static final int NUM_SITES = 3;
    private static final int OPS_PER_RUN_INSERTS_ONLY = 50;
    private static final int OPS_PER_RUN_INSERTS_DELETES = 80;
    private static final int PERMUTATIONS_PER_RUN = 5;

    private final Random random = new Random(RANDOM_SEED);

    // --- Helpers -------------------------------------------------------------

    private static CrdtNodeId rootId() {
        // Root-Node hat im CrdtDocument-Constructor counter = 0
        return new CrdtNodeId(Document.ROOT_SITE_ID, 0);
    }

    private static class SiteState {
        final UUID siteId;
        int counter = 1; // 0 ist für Root reserviert

        SiteState(UUID siteId) {
            this.siteId = siteId;
        }

        CrdtNodeId nextNodeId() {
            return new CrdtNodeId(siteId, counter++);
        }
    }

    /**
     * Erzeugt nur Insert-Operationen für mehrere Sites.
     * Eltern werden immer aus bereits existierenden Knoten-IDs oder Root gewählt.
     */
    private List<CrdtOperation> generateRandomInsertOps(int numSites, int numOps) {
        List<CrdtOperation> ops = new ArrayList<>();

        // pro Site: eigene siteId + counter
        List<SiteState> sites = new ArrayList<>();
        for (int i = 0; i < numSites; i++) {
            sites.add(new SiteState(UUID.randomUUID()));
        }

        // Menge aller existierenden NodeIds (inkl. Root)
        List<CrdtNodeId> existingNodeIds = new ArrayList<>();
        existingNodeIds.add(rootId());

        for (int i = 0; i < numOps; i++) {
            SiteState site = sites.get(random.nextInt(sites.size()));

            // Parent: Root oder ein bereits existierender Knoten
            CrdtNodeId parentId = existingNodeIds.get(random.nextInt(existingNodeIds.size()));

            // Neue NodeId
            CrdtNodeId newId = site.nextNodeId();
            existingNodeIds.add(newId);

            char ch = randomPrintableChar();

            ops.add(new CrdtInsertOp(parentId, newId, ch));
        }

        return ops;
    }

    /**
     * Erzeugt sowohl Inserts als auch Deletes. Deletes zielen nur auf bereits
     * eingefügte (nicht Root-) Knoten.
     */
    private List<CrdtOperation> generateRandomInsertDeleteOps(int numSites, int numOps) {
        List<CrdtOperation> ops = new ArrayList<>();

        List<SiteState> sites = new ArrayList<>();
        for (int i = 0; i < numSites; i++) {
            sites.add(new SiteState(UUID.randomUUID()));
        }

        List<CrdtNodeId> existingNodeIds = new ArrayList<>();
        existingNodeIds.add(rootId());

        // Wir merken uns Nodes, auf die wir delete’en dürfen (keine Root)
        List<CrdtNodeId> deletableNodeIds = new ArrayList<>();

        for (int i = 0; i < numOps; i++) {
            boolean doInsert = deletableNodeIds.isEmpty() || random.nextBoolean();

            if (doInsert) {
                SiteState site = sites.get(random.nextInt(sites.size()));
                CrdtNodeId parentId = existingNodeIds.get(random.nextInt(existingNodeIds.size()));

                CrdtNodeId newId = site.nextNodeId();
                existingNodeIds.add(newId);
                deletableNodeIds.add(newId);

                char ch = randomPrintableChar();
                ops.add(new CrdtInsertOp(parentId, newId, ch));
            } else {
                // Delete: wähle zufälligen bereits eingefügten Node (kein Root)
                CrdtNodeId target = deletableNodeIds.get(random.nextInt(deletableNodeIds.size()));
                ops.add(new CrdtDeleteOp(target));
            }
        }

        return ops;
    }

    private char randomPrintableChar() {
        // einfache, lesbare ASCII-Range
        int base = 97; // 'a'
        int range = 26;
        return (char) (base + random.nextInt(range));
    }

    private List<CrdtOperation> shuffledCopy(List<CrdtOperation> ops, Random rnd) {
        List<CrdtOperation> copy = new ArrayList<>(ops);
        Collections.shuffle(copy, rnd);
        return copy;
    }

    // --- Tests ---------------------------------------------------------------

    @Test
    void randomInserts_convergeAcrossPermutations_singleRun() {
        List<CrdtOperation> ops = generateRandomInsertOps(NUM_SITES, OPS_PER_RUN_INSERTS_ONLY);

        String baseline = Document.fromLog(ops).render();

        // Mehrere Permutationen der gleichen Menge von Operationen
        for (int i = 0; i < PERMUTATIONS_PER_RUN; i++) {
            List<CrdtOperation> permuted = shuffledCopy(ops, new Random(RANDOM_SEED + i + 1));
            Document doc = Document.fromLog(permuted);
            assertEquals(baseline, doc.render(), "render() differiert bei anderer Permutation");
        }
    }

    @RepeatedTest(10)
    void randomInserts_convergeAcrossPermutations_multipleRuns() {
        List<CrdtOperation> ops = generateRandomInsertOps(NUM_SITES, OPS_PER_RUN_INSERTS_ONLY);

        String baseline = Document.fromLog(ops).render();

        for (int i = 0; i < PERMUTATIONS_PER_RUN; i++) {
            List<CrdtOperation> permuted = shuffledCopy(ops, new Random(random.nextLong()));
            Document doc = Document.fromLog(permuted);
            assertEquals(baseline, doc.render(), "render() differiert bei anderer Permutation");
        }
    }

    @RepeatedTest(10)
    void randomInsertsAndDeletes_convergeAcrossPermutations() {
        List<CrdtOperation> ops = generateRandomInsertDeleteOps(NUM_SITES, OPS_PER_RUN_INSERTS_DELETES);

        String baseline = Document.fromLog(ops).render();

        for (int i = 0; i < PERMUTATIONS_PER_RUN; i++) {
            List<CrdtOperation> permuted = shuffledCopy(ops, new Random(random.nextLong()));
            Document doc = Document.fromLog(permuted);
            assertEquals(baseline, doc.render(), "render() differiert bei anderer Permutation (mit Deletes)");
        }
    }
}
