package at.felixb.simplerga;

import at.felixb.energa.crdt.*;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CrdtAnchorAdditionalGuardsTest {

    private static final int RANDOM_SEED = 424242;
    private final Random rnd = new Random(RANDOM_SEED);

    private static final UUID SITE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static CrdtNodeId nodeId(int counter) {
        return new CrdtNodeId(SITE, counter);
    }

    private static CrdtNodeId rootId() {
        return new CrdtNodeId(Document.ROOT_SITE_ID, 0);
    }

    private static CrdtDocument docFromOps(List<CrdtOperation> ops) {
        Document doc = Document.fromLog(ops);
        assertTrue(doc instanceof CrdtDocument);
        return (CrdtDocument) doc;
    }

    // -------------------------------------------------------------------------
    // Helper: build a deterministic “chain” A->B->C->... (so linear order is stable)
    // -------------------------------------------------------------------------

    private static List<CrdtOperation> insertChain(String s) {
        CrdtNodeId root = rootId();
        List<CrdtOperation> ops = new ArrayList<>();

        CrdtNodeId parent = root;
        for (int i = 0; i < s.length(); i++) {
            CrdtNodeId id = nodeId(i + 1);
            ops.add(new CrdtInsertOp(parent, id, s.charAt(i)));
            parent = id;
        }
        return ops;
    }

    private static CrdtNodeId idForIndexInChain(int oneBased) {
        return nodeId(oneBased);
    }

    // -------------------------------------------------------------------------
    // 1) Property-style: resolveAnchor always in [0..visibleSize]
    // -------------------------------------------------------------------------

    @RepeatedTest(20)
    void resolveAnchor_always_clamped_to_visible_range_under_random_ops_and_random_anchors() {
        // Generate random log of inserts/deletes (single-site so ordering is deterministic)
        CrdtNodeId root = rootId();

        int opsCount = 120;
        int counter = 1;

        List<CrdtOperation> ops = new ArrayList<>();
        List<CrdtNodeId> existing = new ArrayList<>();
        existing.add(root);

        List<CrdtNodeId> deletable = new ArrayList<>();

        for (int i = 0; i < opsCount; i++) {
            boolean doInsert = deletable.isEmpty() || rnd.nextBoolean();
            if (doInsert) {
                CrdtNodeId parent = existing.get(rnd.nextInt(existing.size()));
                CrdtNodeId id = nodeId(counter++);
                char ch = (char) ('a' + rnd.nextInt(26));
                ops.add(new CrdtInsertOp(parent, id, ch));
                existing.add(id);
                deletable.add(id);
            } else {
                CrdtNodeId target = deletable.get(rnd.nextInt(deletable.size()));
                ops.add(new CrdtDeleteOp(target));
            }
        }

        CrdtDocument doc = docFromOps(ops);

        // Build some anchors from:
        // - createAnchor on random caret indices
        // - direct anchors to random node IDs (including deleted)
        int trials = 200;
        for (int t = 0; t < trials; t++) {
            int visibleN = doc.getActiveOnlyLinearOrder().size();

            Anchor a;
            if (rnd.nextBoolean()) {
                int caret = rnd.nextInt(visibleN + 3) - 1; // includes out-of-range
                Gravity g = rnd.nextBoolean() ? Gravity.LEFT : Gravity.RIGHT;
                a = doc.createAnchor(caret, g);
            } else {
                // random existing node id (may be deleted)
                // pick from linear order with deleted, or root
                List<CrdtNode> full = doc.getLinearOrder();
                CrdtNodeId id = full.isEmpty()
                        ? root
                        : full.get(rnd.nextInt(full.size())).getNodeId();
                Gravity g = rnd.nextBoolean() ? Gravity.LEFT : Gravity.RIGHT;
                a = new Anchor(id, g);
            }

            int resolved = doc.resolveAnchor(a);
            int N = doc.getActiveOnlyLinearOrder().size();

            assertTrue(resolved >= 0 && resolved <= N,
                    "resolved=" + resolved + " not in [0.." + N + "] for anchor=" + a);
        }
    }

    // -------------------------------------------------------------------------
    // 2) Range consistency guard: resolveRange matches per-anchor resolution
    // -------------------------------------------------------------------------

    @Test
    void resolveRange_matches_individual_resolveAnchor_calls() {
        CrdtDocument doc = docFromOps(insertChain("ABCDE")); // visible N=5

        Anchor a = doc.createAnchor(2, Gravity.LEFT);
        Anchor b = doc.createAnchor(4, Gravity.RIGHT);

        Range r = doc.resolveRange(a, b);

        assertEquals(doc.resolveAnchor(a), r.startIndex());
        assertEquals(doc.resolveAnchor(b), r.endIndex());
    }

    // -------------------------------------------------------------------------
    // 3) Deleted anchor with both sides visible: direction correctness (LEFT vs RIGHT)
    // -------------------------------------------------------------------------

    @Test
    void resolveAnchor_deletedNode_left_uses_left_visible_neighbor_right_uses_right_visible_neighbor() {
        // Build chain: A B C D E
        // Delete C, leaving [A,B,D,E]
        // Anchor at C (deleted):
        // - LEFT => nearest visible left is B => after B => gap 2
        // - RIGHT => nearest visible right is D => before D => gap 2
        // Both give same number here but via different neighbor. We assert by comparing to explicit neighbors.

        List<CrdtOperation> ops = new ArrayList<>(insertChain("ABCDE"));
        ops.add(new CrdtDeleteOp(idForIndexInChain(3))); // delete C

        CrdtDocument doc = docFromOps(ops);
        assertEquals("ABDE", doc.render());

        Anchor left = new Anchor(idForIndexInChain(3), Gravity.LEFT);
        Anchor right = new Anchor(idForIndexInChain(3), Gravity.RIGHT);

        // Explicit expectations via visible neighbors:
        // after B => resolveAnchor(Anchor(B,LEFT)) == indexOfVisible(B)+1 == 2
        // before D => resolveAnchor(Anchor(D,RIGHT)) == indexOfVisible(D) == 2
        int expectLeft = doc.resolveAnchor(new Anchor(idForIndexInChain(2), Gravity.LEFT));
        int expectRight = doc.resolveAnchor(new Anchor(idForIndexInChain(4), Gravity.RIGHT));

        assertEquals(expectLeft, doc.resolveAnchor(left));
        assertEquals(expectRight, doc.resolveAnchor(right));
    }

    // -------------------------------------------------------------------------
    // 4) Stress: many deletes on both sides, anchor still resolves valid + correct direction
    // -------------------------------------------------------------------------

    @Test
    void resolveAnchor_deletedNode_skips_long_runs_of_deleted_nodes() {
        // Chain: A B C D E F G H I
        // Delete B..H except A and I remain visible => "AI"
        // Anchor at E (deleted):
        // LEFT -> after A => gap 1
        // RIGHT -> before I => gap 1

        List<CrdtOperation> ops = new ArrayList<>(insertChain("ABCDEFGHI"));

        // delete B..H (2..8)
        for (int i = 2; i <= 8; i++) {
            ops.add(new CrdtDeleteOp(idForIndexInChain(i)));
        }

        CrdtDocument doc = docFromOps(ops);
        assertEquals("AI", doc.render());

        Anchor eLeft = new Anchor(idForIndexInChain(5), Gravity.LEFT);  // E
        Anchor eRight = new Anchor(idForIndexInChain(5), Gravity.RIGHT);

        assertEquals(1, doc.resolveAnchor(eLeft));
        assertEquals(1, doc.resolveAnchor(eRight));
    }

    // -------------------------------------------------------------------------
    // 5) Root anchor: always resolves to 0 (defensive behavior)
    // -------------------------------------------------------------------------

    @Test
    void resolveAnchor_on_root_is_zero() {
        CrdtDocument doc = docFromOps(insertChain("ABC"));
        Anchor rootLeft = new Anchor(rootId(), Gravity.LEFT);
        Anchor rootRight = new Anchor(rootId(), Gravity.RIGHT);

        // root is deleted/invisible per implementation, and left-search yields none => 0
        assertEquals(0, doc.resolveAnchor(rootLeft));
        assertEquals(0, doc.resolveAnchor(rootRight));
    }

    // -------------------------------------------------------------------------
    // 6) Multi-step mutations: anchor stays consistent through insert/delete churn
    // -------------------------------------------------------------------------

    @Test
    void anchor_survives_multiple_mutations_and_stays_with_its_neighbor_semantics() {
        // Start: ABCD
        // caret at gap 2 (B|C)
        // Make two anchors:
        // - RIGHT => before C
        // - LEFT  => after B
        //
        // Then:
        // 1) insert X under B (goes somewhere within B subtree ordering)
        // 2) delete C
        // 3) insert Y under C (even though deleted, node exists structurally)
        //
        // We only assert: resolves stay in range AND keep consistent relation:
        // - LEFT anchor should still resolve to "after B" (which is before all stuff to the right of B)
        // - RIGHT anchor should now resolve before next visible right (D) after C deleted.

        CrdtNodeId root = rootId();
        CrdtNodeId a = nodeId(1);
        CrdtNodeId b = nodeId(2);
        CrdtNodeId c = nodeId(3);
        CrdtNodeId d = nodeId(4);

        CrdtDocument doc = docFromOps(Arrays.asList(
                new CrdtInsertOp(root, a, 'A'),
                new CrdtInsertOp(a, b, 'B'),
                new CrdtInsertOp(b, c, 'C'),
                new CrdtInsertOp(c, d, 'D')
        ));
        assertEquals("ABCD", doc.render());

        Anchor left = doc.createAnchor(2, Gravity.LEFT);   // after B
        Anchor right = doc.createAnchor(2, Gravity.RIGHT); // before C

        assertEquals(2, doc.resolveAnchor(left));
        assertEquals(2, doc.resolveAnchor(right));

        // mutate
        CrdtNodeId x = nodeId(100); // arbitrary id
        doc.apply(new CrdtInsertOp(b, x, 'X'));

        doc.apply(new CrdtDeleteOp(c));

        CrdtNodeId y = nodeId(50);
        doc.apply(new CrdtInsertOp(c, y, 'Y')); // exists structurally; visibility depends on delete semantics

        int N = doc.getActiveOnlyLinearOrder().size();
        int rl = doc.resolveAnchor(left);
        int rr = doc.resolveAnchor(right);

        assertTrue(rl >= 0 && rl <= N);
        assertTrue(rr >= 0 && rr <= N);

        // LEFT anchor: after B should equal resolve of Anchor(B,LEFT)
        assertEquals(doc.resolveAnchor(new Anchor(b, Gravity.LEFT)), rl);

        // RIGHT anchor: before C, but C deleted => should equal resolve of Anchor(D,RIGHT) (next visible right)
        // If D got pushed around by inserts, this still holds semantically: "before next visible right of deleted C".
        int expectedRight = expectedResolveRightForDeletedNode(doc, c);
        assertEquals(expectedRight, rr);
    }

    private static int expectedResolveRightForDeletedNode(CrdtDocument doc, CrdtNodeId deletedId) {
        // Find deleted node position in full order (with deleted nodes)
        List<CrdtNode> full = doc.getLinearOrder();
        int idx = -1;
        for (int i = 0; i < full.size(); i++) {
            if (full.get(i).getNodeId().equals(deletedId)) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return 0; // matches your resolveAnchor null-ish fallback behavior

        // Find first visible to the right
        for (int i = idx + 1; i < full.size(); i++) {
            CrdtNode n = full.get(i);
            if (n.isVisible()) {
                // caret "before n" => visible index of n
                return doc.resolveAnchor(new Anchor(n.getNodeId(), Gravity.RIGHT));
            }
        }

        // None visible to the right => end
        return doc.getActiveOnlyLinearOrder().size();
    }

    // -------------------------------------------------------------------------
    // 7) Ordering guard: reverseOrder sibling insertion affects anchor as expected
    // -------------------------------------------------------------------------

    @Test
    void ordering_reverseOrder_guard_before_B_moves_only_if_new_sibling_is_sorted_before_B() {
        // Build A->B->C chain.
        // Anchor before B (gap 1, RIGHT).
        // Insert X under A:
        // - if X id > B => X comes before B => anchor resolves to 2
        // - if X id < B => X comes after B subtree => anchor remains 1

        CrdtNodeId root = rootId();
        CrdtNodeId a = nodeId(10);
        CrdtNodeId b = nodeId(100);
        CrdtNodeId c = nodeId(101);

        // base doc
        CrdtDocument doc = docFromOps(Arrays.asList(
                new CrdtInsertOp(root, a, 'A'),
                new CrdtInsertOp(a, b, 'B'),
                new CrdtInsertOp(b, c, 'C')
        ));
        assertEquals("ABC", doc.render());

        Anchor beforeB = doc.createAnchor(1, Gravity.RIGHT);
        assertEquals(1, doc.resolveAnchor(beforeB));

        // Case 1: X bigger than B => before B
        CrdtNodeId x1 = nodeId(200);
        doc.apply(new CrdtInsertOp(a, x1, 'X'));

        assertEquals("AXBC", doc.render());
        assertEquals(2, doc.resolveAnchor(beforeB));

        // reset by building a fresh doc for the other ordering case
        CrdtDocument doc2 = docFromOps(Arrays.asList(
                new CrdtInsertOp(root, a, 'A'),
                new CrdtInsertOp(a, b, 'B'),
                new CrdtInsertOp(b, c, 'C')
        ));
        Anchor beforeB2 = doc2.createAnchor(1, Gravity.RIGHT);
        assertEquals(1, doc2.resolveAnchor(beforeB2));

        // Case 2: X smaller than B => after B subtree
        CrdtNodeId x2 = nodeId(50);
        doc2.apply(new CrdtInsertOp(a, x2, 'X'));

        assertEquals("ABCX", doc2.render());
        assertEquals(1, doc2.resolveAnchor(beforeB2));
    }
}
