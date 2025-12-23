package at.felixb.energa.crdt;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CrdtAnchorApiTest {

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
    // createAnchor() basics
    // -------------------------------------------------------------------------

    @Test
    void createAnchor_emptyDocument_returnsHeadLeft() {
        CrdtDocument doc = docFromOps(List.of());

        Anchor a0L = doc.createAnchor(0, Gravity.LEFT);
        Anchor a0R = doc.createAnchor(0, Gravity.RIGHT);
        Anchor a5L = doc.createAnchor(5, Gravity.LEFT);

        assertEquals(rootId(), a0L.anchorId());
        assertEquals(Gravity.LEFT, a0L.gravity());

        assertEquals(rootId(), a0R.anchorId());
        assertEquals(Gravity.LEFT, a0R.gravity(), "boundary should clamp and ignore requested gravity");

        assertEquals(rootId(), a5L.anchorId());
        assertEquals(Gravity.LEFT, a5L.gravity());
    }

    @Test
    void createAnchor_clampsToStartAndEnd() {
        CrdtDocument doc = docFromOps(insertABC());
        // visible order = A B C, N=3

        // start clamp
        Anchor aNeg = doc.createAnchor(-10, Gravity.RIGHT);
        assertEquals(rootId(), aNeg.anchorId());
        assertEquals(Gravity.LEFT, aNeg.gravity());

        Anchor a0 = doc.createAnchor(0, Gravity.LEFT);
        assertEquals(rootId(), a0.anchorId());
        assertEquals(Gravity.LEFT, a0.gravity());

        // end clamp
        Anchor a3R = doc.createAnchor(3, Gravity.RIGHT);
        // should clamp to lastVisible, LEFT
        assertEquals(nodeId(3), a3R.anchorId()); // 'C'
        assertEquals(Gravity.LEFT, a3R.gravity());

        Anchor a999 = doc.createAnchor(999, Gravity.LEFT);
        assertEquals(nodeId(3), a999.anchorId());
        assertEquals(Gravity.LEFT, a999.gravity());
    }

    @Test
    void createAnchor_middlePositions_chooseNeighborByGravity() {
        CrdtDocument doc = docFromOps(insertABC());
        // visible order = [A,B,C]

        // caretIndex=1 is between A|B
        Anchor left = doc.createAnchor(1, Gravity.LEFT);
        assertEquals(nodeId(1), left.anchorId());     // A
        assertEquals(Gravity.LEFT, left.gravity());

        Anchor right = doc.createAnchor(1, Gravity.RIGHT);
        assertEquals(nodeId(2), right.anchorId());    // B
        assertEquals(Gravity.RIGHT, right.gravity());

        // caretIndex=2 is between B|C
        Anchor left2 = doc.createAnchor(2, Gravity.LEFT);
        assertEquals(nodeId(2), left2.anchorId());    // B
        assertEquals(Gravity.LEFT, left2.gravity());

        Anchor right2 = doc.createAnchor(2, Gravity.RIGHT);
        assertEquals(nodeId(3), right2.anchorId());   // C
        assertEquals(Gravity.RIGHT, right2.gravity());
    }

    // -------------------------------------------------------------------------
    // resolveAnchor() basics + roundtrip
    // -------------------------------------------------------------------------

    @Test
    void resolveAnchor_unknownNode_returnsZero() {
        CrdtDocument doc = docFromOps(insertABC());

        Anchor bogus = new Anchor(new CrdtNodeId(UUID.randomUUID(), 123), Gravity.LEFT);
        assertEquals(0, doc.resolveAnchor(bogus));
    }

    @Test
    void resolveAnchor_visibleNode_matchesSpec() {
        CrdtDocument doc = docFromOps(insertABC());
        // visible order = [A,B,C]
        // indices (gap): 0 |A| 1 |B| 2 |C| 3

        Anchor aLeft = new Anchor(nodeId(1), Gravity.LEFT);  // A
        Anchor aRight = new Anchor(nodeId(1), Gravity.RIGHT);
        assertEquals(1, doc.resolveAnchor(aLeft));  // indexOfVisible(A)=0 => +1 => 1
        assertEquals(0, doc.resolveAnchor(aRight)); // indexOfVisible(A)=0

        Anchor bLeft = new Anchor(nodeId(2), Gravity.LEFT);  // B
        Anchor bRight = new Anchor(nodeId(2), Gravity.RIGHT);
        assertEquals(2, doc.resolveAnchor(bLeft));  // indexOfVisible(B)=1 => +1 => 2
        assertEquals(1, doc.resolveAnchor(bRight)); // indexOfVisible(B)=1
    }

    @Test
    void createAnchor_then_resolveAnchor_roundtrip_forAllGapPositions() {
        CrdtDocument doc = docFromOps(insertABC());
        int N = doc.getActiveOnlyLinearOrder().size();
        assertEquals(3, N);

        for (int caret = 0; caret <= N; caret++) {
            Anchor left = doc.createAnchor(caret, Gravity.LEFT);
            Anchor right = doc.createAnchor(caret, Gravity.RIGHT);

            assertEquals(caret, doc.resolveAnchor(left), "roundtrip failed for caret=" + caret + " gravity=LEFT");
            assertEquals(caret, doc.resolveAnchor(right), "roundtrip failed for caret=" + caret + " gravity=RIGHT");
        }
    }

    // -------------------------------------------------------------------------
    // resolveAnchor() when anchor node is deleted
    // -------------------------------------------------------------------------

    @Test
    void resolveAnchor_deletedNode_leftGravity_searchesLeftNeighbor() {
        // Start with ABC, then delete B
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
        assertEquals("AC", doc.render()); // visible order [A,C], gaps: 0|A|1|C|2

        Anchor deletedBLeft = new Anchor(b, Gravity.LEFT);

        // LEFT: search left -> nearest visible is A -> indexOfVisible(A)=0 -> +1 => 1
        assertEquals(1, doc.resolveAnchor(deletedBLeft));
    }

    @Test
    void resolveAnchor_deletedNode_rightGravity_searchesRightNeighbor() {
        // ABC then delete B -> visible [A,C]
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
        assertEquals("AC", doc.render()); // gaps: 0|A|1|C|2

        Anchor deletedBRight = new Anchor(b, Gravity.RIGHT);

        // RIGHT: search right -> nearest visible is C -> indexOfVisible(C)=1 -> caret = 1
        assertEquals(1, doc.resolveAnchor(deletedBRight));
    }

    @Test
    void resolveAnchor_deletedNode_noVisibleInDirection_returnsStartOrEnd() {
        // A only, then delete A (so visible empty), plus root exists but not visible char
        // However your render() is based on active nodes only: should be ""
        CrdtNodeId root = rootId();
        CrdtNodeId a = nodeId(1);

        List<CrdtOperation> ops = Arrays.asList(
                new CrdtInsertOp(root, a, 'A'),
                new CrdtDeleteOp(a)
        );

        CrdtDocument doc = docFromOps(ops);
        assertEquals("", doc.render());
        assertEquals(0, doc.getActiveOnlyLinearOrder().size());

        // anchor points to deleted A
        Anchor left = new Anchor(a, Gravity.LEFT);
        Anchor right = new Anchor(a, Gravity.RIGHT);

        // LEFT with no visible left => 0
        assertEquals(0, doc.resolveAnchor(left));
        // RIGHT with no visible right => visibleSize() == 0
        assertEquals(0, doc.resolveAnchor(right));
    }

    // -------------------------------------------------------------------------
    // resolveRange() (not normalized)
    // -------------------------------------------------------------------------

    @Test
    void resolveRange_doesNotNormalize() {
        CrdtDocument doc = docFromOps(insertABC());
        // visible [A,B,C], gaps 0..3

        Anchor a = doc.createAnchor(3, Gravity.LEFT); // end (clamps to C, LEFT) -> resolves to 3
        Anchor b = doc.createAnchor(0, Gravity.LEFT); // start -> resolves to 0

        Range r = doc.resolveRange(a, b);

        assertEquals(3, r.startIndex());
        assertEquals(0, r.endIndex());
    }
}
