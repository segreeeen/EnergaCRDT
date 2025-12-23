package at.felixb.energa.crdt;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CrdtAnchorStabilityTest {

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

    /**
     * Build chain ROOT -A-> a -B-> b -C-> c
     */
    private static List<CrdtOperation> insertABC() {
        CrdtNodeId root = rootId();
        CrdtNodeId a = nodeId(1);
        CrdtNodeId b = nodeId(2);
        CrdtNodeId c = nodeId(3);

        return Arrays.asList(
                new CrdtInsertOp(root, a, 'A'),
                new CrdtInsertOp(a, b, 'B'),
                new CrdtInsertOp(b, c, 'C')
        );
    }

    // -------------------------------------------------------------------------
    // 1) Anchor “stability” across edits
    // -------------------------------------------------------------------------

    @Test
    void anchor_created_between_A_and_B_stays_at_same_gap_when_inserting_on_the_right_of_gap() {
        // Initial: ABC
        // Create caret at gap 1 (A|B)
        // Then insert X UNDER B (so it appears after B/C in DFS order: ABCX)
        // Expect the gap A|B to remain gap index 1.
        //
        // This asserts: anchor (B, RIGHT) continues to resolve "before B",
        // regardless of insertions that happen to the right of B.

        CrdtNodeId root = rootId();
        CrdtNodeId a = nodeId(1);
        CrdtNodeId b = nodeId(2);
        CrdtNodeId c = nodeId(3);
        CrdtNodeId x = nodeId(4);

        List<CrdtOperation> ops = Arrays.asList(
                new CrdtInsertOp(root, a, 'A'),
                new CrdtInsertOp(a, b, 'B'),
                new CrdtInsertOp(b, c, 'C'),

                // create the insert far to the right (child of C or B, depending on your DFS)
                new CrdtInsertOp(c, x, 'X')
        );

        CrdtDocument doc = docFromOps(ops);
        assertEquals("ABCX", doc.render());

        Anchor anchor = doc.createAnchor(1, Gravity.RIGHT); // attaches to B (right neighbor)
        assertEquals(1, doc.resolveAnchor(anchor), "caret A|B should remain at gap 1");
    }

    @Test
    void anchor_created_between_A_and_B_moves_when_inserting_new_node_between_A_and_B() {
        // Build initial ABC
        CrdtNodeId root = rootId();
        CrdtNodeId a = nodeId(1);
        CrdtNodeId b = nodeId(2);
        CrdtNodeId c = nodeId(3);
        CrdtNodeId x = nodeId(4); // IMPORTANT: higher id -> will be ordered BEFORE b under A (reverseOrder)

        CrdtDocument doc = docFromOps(Arrays.asList(
                new CrdtInsertOp(root, a, 'A'),
                new CrdtInsertOp(a, b, 'B'),
                new CrdtInsertOp(b, c, 'C')
        ));
        assertEquals("ABC", doc.render());

        // Create anchor at gap 1 (A|B) BEFORE we mutate
        Anchor anchor = doc.createAnchor(1, Gravity.RIGHT); // attaches to B (before B)
        assertEquals(1, doc.resolveAnchor(anchor));

        // Now insert X under A -> because children are reverseOrder and x>b, X comes before B: A X B C
        doc.apply(new CrdtInsertOp(a, x, 'X'));
        assertEquals("AXBC", doc.render());

        // Anchor is still "before B", which moved to gap 2
        assertEquals(2, doc.resolveAnchor(anchor));
    }


    @Test
    void anchor_created_at_gap1_leftGravity_sticks_after_A_even_if_inserting_between_A_and_B() {
        // caret gap 1 (A|B) with Gravity.LEFT attaches to A (left neighbor)
        // Insert X between A and B => AXBC
        // "after A" becomes gap 1 still? Actually visible order: A | X ... so after A is still gap 1.
        // So resolveAnchor should remain 1.

        CrdtNodeId root = rootId();
        CrdtNodeId a = nodeId(1);
        CrdtNodeId b = nodeId(2);
        CrdtNodeId c = nodeId(3);
        CrdtNodeId x = nodeId(4);

        List<CrdtOperation> ops = Arrays.asList(
                new CrdtInsertOp(root, a, 'A'),
                new CrdtInsertOp(a, b, 'B'),
                new CrdtInsertOp(b, c, 'C'),
                new CrdtInsertOp(a, x, 'X') // between A and B
        );

        CrdtDocument doc = docFromOps(ops);
        assertEquals("AXBC", doc.render());

        Anchor anchor = doc.createAnchor(1, Gravity.LEFT); // anchor to A
        assertEquals(1, doc.resolveAnchor(anchor), "after A should remain gap 1 even if X is inserted between A and B");
    }

    @Test
    void anchor_on_visible_node_updates_when_that_node_is_deleted() {
        // Create anchor that attaches to B (RIGHT => before B).
        // Delete B. Then RIGHT-search should go to nearest visible right (C).
        // With visible [A,C], before C is gap 1.

        CrdtNodeId root = rootId();
        CrdtNodeId a = nodeId(1);
        CrdtNodeId b = nodeId(2);
        CrdtNodeId c = nodeId(3);

        List<CrdtOperation> ops = Arrays.asList(
                new CrdtInsertOp(root, a, 'A'),
                new CrdtInsertOp(a, b, 'B'),
                new CrdtInsertOp(b, c, 'C'),
                new CrdtDeleteOp(b)
        );

        CrdtDocument doc = docFromOps(ops);
        assertEquals("AC", doc.render());

        Anchor anchor = new Anchor(b, Gravity.RIGHT);
        assertEquals(1, doc.resolveAnchor(anchor), "deleted B, RIGHT should resolve before next visible right (C)");
    }

    // -------------------------------------------------------------------------
    // 2) Deleted anchor in “dense deletes”
    // -------------------------------------------------------------------------

    @Test
    void resolveAnchor_deletedNode_right_skips_multiple_deleted_nodes_until_next_visible() {
        // Build A B C D, delete B and C -> visible [A,D]
        // Anchor at C (deleted) with RIGHT should search right:
        // - next nodes: D (visible) -> before D is gap 1
        CrdtNodeId root = rootId();
        CrdtNodeId a = nodeId(1);
        CrdtNodeId b = nodeId(2);
        CrdtNodeId c = nodeId(3);
        CrdtNodeId d = nodeId(4);

        List<CrdtOperation> ops = Arrays.asList(
                new CrdtInsertOp(root, a, 'A'),
                new CrdtInsertOp(a, b, 'B'),
                new CrdtInsertOp(b, c, 'C'),
                new CrdtInsertOp(c, d, 'D'),
                new CrdtDeleteOp(b),
                new CrdtDeleteOp(c)
        );

        CrdtDocument doc = docFromOps(ops);
        assertEquals("AD", doc.render());

        Anchor anchor = new Anchor(c, Gravity.RIGHT);
        assertEquals(1, doc.resolveAnchor(anchor), "RIGHT should skip deleted nodes and resolve before D");
    }

    @Test
    void resolveAnchor_deletedNode_left_skips_multiple_deleted_nodes_until_next_visible() {
        // A B C D, delete B and C -> visible [A,D]
        // Anchor at C (deleted) with LEFT should search left:
        // - nearest visible left is A -> after A is gap 1
        CrdtNodeId root = rootId();
        CrdtNodeId a = nodeId(1);
        CrdtNodeId b = nodeId(2);
        CrdtNodeId c = nodeId(3);
        CrdtNodeId d = nodeId(4);

        List<CrdtOperation> ops = Arrays.asList(
                new CrdtInsertOp(root, a, 'A'),
                new CrdtInsertOp(a, b, 'B'),
                new CrdtInsertOp(b, c, 'C'),
                new CrdtInsertOp(c, d, 'D'),
                new CrdtDeleteOp(b),
                new CrdtDeleteOp(c)
        );

        CrdtDocument doc = docFromOps(ops);
        assertEquals("AD", doc.render());

        Anchor anchor = new Anchor(c, Gravity.LEFT);
        assertEquals(1, doc.resolveAnchor(anchor), "LEFT should skip deleted nodes and resolve after A");
    }

    @Test
    void resolveAnchor_deletedNode_right_with_no_visible_right_goes_to_end() {
        // A B C, delete C -> visible [A,B]
        // Anchor at C (deleted) with RIGHT should search right -> none -> end = N=2
        CrdtNodeId root = rootId();
        CrdtNodeId a = nodeId(1);
        CrdtNodeId b = nodeId(2);
        CrdtNodeId c = nodeId(3);

        List<CrdtOperation> ops = Arrays.asList(
                new CrdtInsertOp(root, a, 'A'),
                new CrdtInsertOp(a, b, 'B'),
                new CrdtInsertOp(b, c, 'C'),
                new CrdtDeleteOp(c)
        );

        CrdtDocument doc = docFromOps(ops);
        assertEquals("AB", doc.render());

        Anchor anchor = new Anchor(c, Gravity.RIGHT);
        assertEquals(2, doc.resolveAnchor(anchor), "no visible right => should resolve to end");
    }

    @Test
    void resolveAnchor_deletedNode_left_with_no_visible_left_goes_to_start() {
        // A B C, delete A -> visible [B,C]
        // Anchor at A (deleted) with LEFT should search left -> none -> start = 0
        CrdtNodeId root = rootId();
        CrdtNodeId a = nodeId(1);
        CrdtNodeId b = nodeId(2);
        CrdtNodeId c = nodeId(3);

        List<CrdtOperation> ops = Arrays.asList(
                new CrdtInsertOp(root, a, 'A'),
                new CrdtInsertOp(a, b, 'B'),
                new CrdtInsertOp(b, c, 'C'),
                new CrdtDeleteOp(a)
        );

        CrdtDocument doc = docFromOps(ops);
        assertEquals("BC", doc.render());

        Anchor anchor = new Anchor(a, Gravity.LEFT);
        assertEquals(0, doc.resolveAnchor(anchor), "no visible left => should resolve to start");
    }

    @Test
    void anchor_before_B_moves_when_inserting_new_sibling_X_with_higher_id_than_B() {
        // reverseOrder children: higher id first
        // If X > B, then A's children order: X, B -> DFS: A X B C

        CrdtNodeId root = rootId();
        CrdtNodeId a = nodeId(1);
        CrdtNodeId b = nodeId(2);
        CrdtNodeId c = nodeId(3);
        CrdtNodeId x = nodeId(4); // X > B => X comes before B

        CrdtDocument doc = docFromOps(Arrays.asList(
                new CrdtInsertOp(root, a, 'A'),
                new CrdtInsertOp(a, b, 'B'),
                new CrdtInsertOp(b, c, 'C')
        ));
        assertEquals("ABC", doc.render());

        Anchor anchor = doc.createAnchor(1, Gravity.RIGHT); // before B
        assertEquals(1, doc.resolveAnchor(anchor));

        doc.apply(new CrdtInsertOp(a, x, 'X'));
        assertEquals("AXBC", doc.render());

        // "before B" moved right by 1 due to X inserted between A and B
        assertEquals(2, doc.resolveAnchor(anchor));
    }

    @Test
    void anchor_before_B_does_not_move_when_inserting_new_sibling_X_with_lower_id_than_B() {
        // reverseOrder children: higher id first
        // If X < B, then A's children order: B, X -> DFS: A B C X  (X is after B's subtree)
        // Therefore "before B" stays at gap 1.

        CrdtNodeId root = rootId();
        CrdtNodeId a = nodeId(10);

        // Make B deliberately high, and X deliberately lower than B.
        CrdtNodeId b = nodeId(100);
        CrdtNodeId c = nodeId(101); // child of B
        CrdtNodeId x = nodeId(50);  // X < B => X comes AFTER B within A's children (reverseOrder)

        CrdtDocument doc = docFromOps(Arrays.asList(
                new CrdtInsertOp(root, a, 'A'),
                new CrdtInsertOp(a, b, 'B'),
                new CrdtInsertOp(b, c, 'C')
        ));
        assertEquals("ABC", doc.render());

        Anchor anchor = doc.createAnchor(1, Gravity.RIGHT); // before B
        assertEquals(1, doc.resolveAnchor(anchor));

        doc.apply(new CrdtInsertOp(a, x, 'X'));

        // Expected order: A B C X (since B > X in reverseOrder, B comes first, so X is after B's subtree)
        assertEquals("ABCX", doc.render());

        // Still before B => still gap 1
        assertEquals(1, doc.resolveAnchor(anchor));
    }

    @Test
    void anchor_after_A_leftGravity_stays_at_gap1_when_inserting_X_before_B() {
        // Initial: ABC
        // Anchor at gap 1 with LEFT => attaches to A ("after A")
        // Insert X under A with higher id than B => order becomes A X B C
        // "after A" is still gap 1.

        CrdtNodeId root = rootId();
        CrdtNodeId a = nodeId(1);
        CrdtNodeId b = nodeId(2);
        CrdtNodeId c = nodeId(3);
        CrdtNodeId x = nodeId(4); // X > B => X before B (reverseOrder)

        CrdtDocument doc = docFromOps(Arrays.asList(
                new CrdtInsertOp(root, a, 'A'),
                new CrdtInsertOp(a, b, 'B'),
                new CrdtInsertOp(b, c, 'C')
        ));
        assertEquals("ABC", doc.render());

        Anchor anchor = doc.createAnchor(1, Gravity.LEFT); // after A
        assertEquals(1, doc.resolveAnchor(anchor));

        doc.apply(new CrdtInsertOp(a, x, 'X'));
        assertEquals("AXBC", doc.render());

        // still after A
        assertEquals(1, doc.resolveAnchor(anchor));
    }

    @Test
    void anchor_after_A_leftGravity_stays_at_gap1_when_inserting_X_after_B() {
        // Initial: ABC
        // Anchor at gap 1 with LEFT => attaches to A ("after A")
        // Insert X under A with lower id than B => order becomes A B C X
        // "after A" is still gap 1.

        CrdtNodeId root = rootId();
        CrdtNodeId a = nodeId(10);

        CrdtNodeId b = nodeId(100);
        CrdtNodeId c = nodeId(101); // child of B
        CrdtNodeId x = nodeId(50);  // X < B => X after B (reverseOrder)

        CrdtDocument doc = docFromOps(Arrays.asList(
                new CrdtInsertOp(root, a, 'A'),
                new CrdtInsertOp(a, b, 'B'),
                new CrdtInsertOp(b, c, 'C')
        ));
        assertEquals("ABC", doc.render());

        Anchor anchor = doc.createAnchor(1, Gravity.LEFT); // after A
        assertEquals(1, doc.resolveAnchor(anchor));

        doc.apply(new CrdtInsertOp(a, x, 'X'));
        assertEquals("ABCX", doc.render());

        // still after A
        assertEquals(1, doc.resolveAnchor(anchor));
    }

    @Test
    void anchor_after_A_leftGravity_resolves_to_start_when_A_is_deleted() {
        // Initial: ABC
        // caret gap 1 with LEFT => anchor attaches to A ("after A")
        // Delete A => anchor node is deleted
        // LEFT resolution searches left for visible -> none -> resolves to 0

        CrdtNodeId root = rootId();
        CrdtNodeId a = nodeId(1);
        CrdtNodeId b = nodeId(2);
        CrdtNodeId c = nodeId(3);

        CrdtDocument doc = docFromOps(Arrays.asList(
                new CrdtInsertOp(root, a, 'A'),
                new CrdtInsertOp(a, b, 'B'),
                new CrdtInsertOp(b, c, 'C')
        ));
        assertEquals("ABC", doc.render());

        Anchor anchor = doc.createAnchor(1, Gravity.LEFT); // after A
        assertEquals(1, doc.resolveAnchor(anchor));

        // delete A
        doc.apply(new CrdtDeleteOp(a));
        assertEquals("BC", doc.render());

        // Anchor points to deleted A with LEFT:
        // search left -> none -> 0
        assertEquals(0, doc.resolveAnchor(anchor));
    }

    @Test
    void anchor_before_B_rightGravity_resolves_before_next_visible_when_B_is_deleted() {
        // Initial: ABC
        // caret gap 1 with RIGHT => anchor attaches to B ("before B")
        // Delete B => anchor node deleted
        // RIGHT resolution searches right for visible -> C -> resolves to indexOfVisible(C)

        CrdtNodeId root = rootId();
        CrdtNodeId a = nodeId(1);
        CrdtNodeId b = nodeId(2);
        CrdtNodeId c = nodeId(3);

        CrdtDocument doc = docFromOps(Arrays.asList(
                new CrdtInsertOp(root, a, 'A'),
                new CrdtInsertOp(a, b, 'B'),
                new CrdtInsertOp(b, c, 'C')
        ));
        assertEquals("ABC", doc.render());

        Anchor anchor = doc.createAnchor(1, Gravity.RIGHT); // before B
        assertEquals(1, doc.resolveAnchor(anchor));

        doc.apply(new CrdtDeleteOp(b));
        assertEquals("AC", doc.render());

        // next visible to the right is C, and "before C" is gap 1
        assertEquals(1, doc.resolveAnchor(anchor));
    }

    @Test
    void anchor_before_B_rightGravity_resolves_to_end_when_no_visible_right_exists() {
        // Initial: ABC
        // anchor "before B"
        // Delete B and C => visible [A]
        // RIGHT resolution: search right -> none visible -> end => visibleSize() == 1

        CrdtNodeId root = rootId();
        CrdtNodeId a = nodeId(1);
        CrdtNodeId b = nodeId(2);
        CrdtNodeId c = nodeId(3);

        CrdtDocument doc = docFromOps(Arrays.asList(
                new CrdtInsertOp(root, a, 'A'),
                new CrdtInsertOp(a, b, 'B'),
                new CrdtInsertOp(b, c, 'C')
        ));
        assertEquals("ABC", doc.render());

        Anchor anchor = doc.createAnchor(1, Gravity.RIGHT); // before B
        assertEquals(1, doc.resolveAnchor(anchor));

        doc.apply(new CrdtDeleteOp(b));
        doc.apply(new CrdtDeleteOp(c));
        assertEquals("A", doc.render());

        assertEquals(1, doc.resolveAnchor(anchor), "should resolve to end of visible order");
    }


}
