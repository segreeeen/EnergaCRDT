package at.felixb.simplerga;

import at.felixb.energa.crdt.*;
import org.junit.jupiter.api.RepeatedTest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class CrdtStructuralPropertyTest {

    private static final int RANDOM_SEED = 1337;
    private static final int NUM_SITES = 3;
    private static final int OPS_PER_RUN = 80;
    private static final int PERMUTATIONS_PER_RUN = 4;

    private final Random random = new Random(RANDOM_SEED);

    // ---- Helpers -----------------------------------------------------------------

    private static CrdtNodeId rootId() {
        return new CrdtNodeId(Document.ROOT_SITE_ID, 0);
    }

    private static class SiteState {
        final UUID siteId;
        int counter = 1; // 0: Root

        SiteState(UUID siteId) {
            this.siteId = siteId;
        }

        CrdtNodeId nextNodeId() {
            return new CrdtNodeId(siteId, counter++);
        }
    }

    private char randomPrintableChar() {
        int base = 97; // 'a'
        int range = 26;
        return (char) (base + random.nextInt(range));
    }

    private List<CrdtOperation> generateRandomInsertDeleteOps(int numSites, int numOps) {
        List<CrdtOperation> ops = new ArrayList<>();

        List<SiteState> sites = new ArrayList<>();
        for (int i = 0; i < numSites; i++) {
            sites.add(new SiteState(UUID.randomUUID()));
        }

        List<CrdtNodeId> existingNodeIds = new ArrayList<>();
        existingNodeIds.add(rootId());

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
                CrdtNodeId target = deletableNodeIds.get(random.nextInt(deletableNodeIds.size()));
                ops.add(new CrdtDeleteOp(target));
            }
        }

        return ops;
    }

    private List<CrdtOperation> shuffledCopy(List<CrdtOperation> ops, Random rnd) {
        List<CrdtOperation> copy = new ArrayList<>(ops);
        Collections.shuffle(copy, rnd);
        return copy;
    }

    private Map<CrdtNodeId, CrdtNode> indexNodes(CrdtDocument doc) {
        Map<CrdtNodeId, CrdtNode> map = new HashMap<>();
        // Root explizit
        CrdtNode root = doc.getRoot();
        map.put(root.getNodeId(), root);

        // alle anderen Nodes via traverse()
        for (CrdtNode n : doc.traverse()) {
            map.put(n.getNodeId(), n);
        }
        return map;
    }

    private int computeSubTreeSize(CrdtNode node) {
        int size = 1;
        for (CrdtNode child : node.getChildren()) {
            size += computeSubTreeSize(child);
        }
        return size;
    }

    private void assertSubtreeSizeInvariant(CrdtDocument doc) {
        CrdtNode root = doc.getRoot();
        // Root selbst: sein subTreeSize sollte Anzahl aller Nodes inkl. Root sein
        int computedRootSize = computeSubTreeSize(root);
        assertEquals(computedRootSize, root.getSubTreeSize(), "subTreeSize-Invariante verletzt für Root");

        // Alle anderen Nodes über traverse()
        for (CrdtNode node : doc.traverse()) {
            int expected = computeSubTreeSize(node);
            assertEquals(expected, node.getSubTreeSize(),
                    "subTreeSize-Invariante verletzt für Node " + node.getNodeId());
        }
    }

    private void assertTreeAndCacheConsistent(CrdtDocument doc) {
        // traverse() benutzt die Baumstruktur,
        // getLinearOrder() benutzt den Cache.
        List<CrdtNode> dfsFromTree = doc.traverse();
        List<CrdtNode> dfsFromCache = doc.getLinearOrder();

        assertEquals(
                dfsFromTree.size(),
                dfsFromCache.size(),
                "Tree-DFS und Cache-Länge stimmen nicht überein"
        );

        for (int i = 0; i < dfsFromTree.size(); i++) {
            CrdtNode t = dfsFromTree.get(i);
            CrdtNode c = dfsFromCache.get(i);
            assertEquals(t.getNodeId(), c.getNodeId(),
                    "Tree-DFS und Cache unterscheiden sich bei Index " + i);
        }
    }

    private void assertStructuralEquality(CrdtDocument d1, CrdtDocument d2) {
        CrdtNode root1 = d1.getRoot();
        CrdtNode root2 = d2.getRoot();

        // Root-NodeId sollte identisch sein (gleiche ROOT_SITE_ID, counter 0)
        assertEquals(root1.getNodeId(), root2.getNodeId(), "Root-NodeId differiert");

        Map<CrdtNodeId, CrdtNode> m1 = indexNodes(d1);
        Map<CrdtNodeId, CrdtNode> m2 = indexNodes(d2);

        assertEquals(m1.keySet(), m2.keySet(), "Menge der NodeIds unterscheidet sich");

        for (CrdtNodeId id : m1.keySet()) {
            CrdtNode n1 = m1.get(id);
            CrdtNode n2 = m2.get(id);

            // Charakter & Delete-Status identisch
            assertEquals(n1.getCharacter(), n2.getCharacter(),
                    "Character differiert für Node " + id);
            assertEquals(n1.isDeleted(), n2.isDeleted(),
                    "Deleted-Flag differiert für Node " + id);

            // Parent-Beziehung
            CrdtNode p1 = n1.getParent();
            CrdtNode p2 = n2.getParent();

            if (p1 == null) {
                assertNull(p2, "Parent-Nullheit differiert für Node " + id);
            } else {
                assertNotNull(p2, "Parent-Nullheit differiert für Node " + id);
                assertEquals(p1.getNodeId(), p2.getNodeId(),
                        "Parent-NodeId differiert für Node " + id);
            }

            // Kinder-Reihenfolge identisch
            List<CrdtNodeId> children1 = n1.getChildren().stream().map(CrdtNode::getNodeId).toList();
            List<CrdtNodeId> children2 = n2.getChildren().stream().map(CrdtNode::getNodeId).toList();

            assertEquals(children1, children2,
                    "Kinder-Reihenfolge differiert für Node " + id);
        }
    }

    // ---- Tests -------------------------------------------------------------------

    @RepeatedTest(10)
    void subtreeSize_invariant_holds_under_random_ops() {
        List<CrdtOperation> ops = generateRandomInsertDeleteOps(NUM_SITES, OPS_PER_RUN);
        Document doc = Document.fromLog(ops);

        assertTrue(doc instanceof CrdtDocument);
        CrdtDocument crdtDoc = (CrdtDocument) doc;

        assertSubtreeSizeInvariant(crdtDoc);
    }

    @RepeatedTest(10)
    void tree_and_cache_are_consistent_under_random_ops() {
        List<CrdtOperation> ops = generateRandomInsertDeleteOps(NUM_SITES, OPS_PER_RUN);
        Document doc = Document.fromLog(ops);

        assertTrue(doc instanceof CrdtDocument);
        CrdtDocument crdtDoc = (CrdtDocument) doc;

        assertTreeAndCacheConsistent(crdtDoc);
    }

    @RepeatedTest(10)
    void permutations_yield_structurally_identical_documents() {
        List<CrdtOperation> ops = generateRandomInsertDeleteOps(NUM_SITES, OPS_PER_RUN);

        // Baseline
        CrdtDocument base = (CrdtDocument) Document.fromLog(ops);

        for (int i = 0; i < PERMUTATIONS_PER_RUN; i++) {
            List<CrdtOperation> permuted = shuffledCopy(ops, new Random(random.nextLong()));
            CrdtDocument other = (CrdtDocument) Document.fromLog(permuted);

            // textuell
            assertEquals(base.render(), other.render(), "render() differiert");

            // strukturell
            assertStructuralEquality(base, other);

            // Und jeweils: Tree vs Cache
            assertTreeAndCacheConsistent(base);
            assertTreeAndCacheConsistent(other);

            // Und Subtree-Invarianten
            assertSubtreeSizeInvariant(base);
            assertSubtreeSizeInvariant(other);
        }
    }
}
