package at.felixb.simplerga;

import at.felixb.energa.crdt.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CrdtSubTreeSizeStructureTest {

    // Neues CRDT-Dokument
    private CrdtDocument newDocument() {
        return (CrdtDocument) Document.create();
    }

    // Root via Reflection holen (weil private)
    private CrdtNode getRoot(CrdtDocument doc) {
        try {
            Field f = CrdtDocument.class.getDeclaredField("root");
            f.setAccessible(true);
            return (CrdtNode) f.get(doc);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to access root field via reflection", e);
        }
    }

    // Insert über CRDT-API
    private void applyInsert(CrdtDocument doc, int position, String text) {
        InsertOp op = OperationFactory.createInsertOp(position, text);
        List<CrdtInsertOp> internal = op.transformToInternal(doc);
        internal.forEach(doc::apply);
    }

    // Delete über CRDT-API
    private void applyDelete(CrdtDocument doc, int start, int end) {
        DeleteOp op = OperationFactory.createDeleteOp(start, end);
        List<CrdtDeleteOp> internal = op.transformToInternal(doc);
        internal.forEach(doc::apply);
    }

    /**
     * Naive strukturelle Größe:
     *  1 (dieser Node) + Summe(Teilbäume der Kinder)
     * Ignoriert deleted-Flag bewusst – entspricht deiner aktuellen Semantik.
     */
    private int computeStructuralSize(CrdtNode node) {
        int size = 1; // node selbst
        for (CrdtNode child : node.getChildren()) {
            size += computeStructuralSize(child);
        }
        return size;
    }

    /**
     * Invariante: für jeden Node gilt
     *   node.getSubTreeSize() == 1 + Sum(child.getSubTreeSize())
     * strukturell gesehen (unabhängig von deleted).
     */
    private void assertStructuralInvariant(CrdtDocument doc) {
        CrdtNode root = getRoot(doc);

        assertNodeStructuralInvariant(root);
        for (CrdtNode node : doc.traverse()) {
            assertNodeStructuralInvariant(node);
        }
    }

    private void assertNodeStructuralInvariant(CrdtNode node) {
        int expected = computeStructuralSize(node);
        int actual = node.getSubTreeSize();

        assertEquals(expected, actual,
                () -> "Structural subtree invariant violated for node "
                        + node.getNodeId() + " expected=" + expected + " actual=" + actual);
    }

    // --- Tests ---

    @Test
    void emptyDocument_rootSubTreeSizeAndStructure() {
        CrdtDocument doc = newDocument();

        String rendered = doc.render();
        CrdtNode root = getRoot(doc);
        List<CrdtNode> nodes = doc.traverse();

        // Render: leer
        assertEquals("", rendered);

        // Keine Nicht-Root-Knoten
        assertEquals(0, nodes.size());

        // Strukturell: nur Root existiert -> Größe = 1
        assertEquals(1, root.getSubTreeSize());

        // Invariante
        assertStructuralInvariant(doc);
    }

    @Test
    void singleInsert_A_rootAndLeafStructuralSizes() {
        CrdtDocument doc = newDocument();

        applyInsert(doc, 0, "A");

        String rendered = doc.render();
        CrdtNode root = getRoot(doc);
        List<CrdtNode> nodes = doc.traverse();

        // Sichtbarer Text
        assertEquals("A", rendered);

        // Genau ein Nicht-Root-Node
        assertEquals(1, nodes.size());
        CrdtNode aNode = nodes.get(0);

        // Leaf: strukturell nur sich selbst -> 1
        assertEquals(1, aNode.getSubTreeSize());

        // Root: Root + A -> 2
        assertEquals(2, root.getSubTreeSize());

        assertStructuralInvariant(doc);
    }

    @Test
    void threeInserts_ABC_rootAndNodesStructuralSizes() {
        CrdtDocument doc = newDocument();

        applyInsert(doc, 0, "ABC");
        String rendered = doc.render();
        CrdtNode root = getRoot(doc);
        List<CrdtNode> nodes = doc.traverse();

        // Text
        assertEquals("ABC", rendered);

        // 3 Nicht-Root-Nodes (A,B,C) in Kette: A -> B -> C
        assertEquals(3, nodes.size());

        CrdtNode aNode = nodes.get(0);
        CrdtNode bNode = nodes.get(1);
        CrdtNode cNode = nodes.get(2);

        // C ist Leaf -> strukturelle Größe 1
        assertEquals(1, cNode.getSubTreeSize(), "Leaf C should have structural subtree size 1");

        // B hat B + C -> 2
        assertEquals(2, bNode.getSubTreeSize(),
                "Node B should have structural subtree size 2 (B + C)");

        // A hat A + B + C -> 3
        assertEquals(3, aNode.getSubTreeSize(),
                "Node A should have structural subtree size 3 (A + B + C)");

        // Root: Root + A + B + C -> 4
        assertEquals(4, root.getSubTreeSize(),
                "Root structural subtree size should be 4 (root + A + B + C)");

        assertStructuralInvariant(doc);
    }


    @Test
    void deleteDoesNotChangeStructuralSubTreeSizes() {
        CrdtDocument doc = newDocument();

        // "AB"
        applyInsert(doc, 0, "AB");
        assertEquals("AB", doc.render());

        CrdtNode rootBefore = getRoot(doc);
        int rootSizeBefore = rootBefore.getSubTreeSize();
        List<CrdtNode> nodesBefore = doc.traverse();
        assertEquals(2, nodesBefore.size());

        // Delete 'A'
        applyDelete(doc, 0, 1);
        String rendered = doc.render();
        CrdtNode rootAfter = getRoot(doc);
        List<CrdtNode> nodesAfter = doc.traverse();

        // Text: nur "B" übrig
        assertEquals("B", rendered);

        // Strukturelle Anzahl Nodes bleibt gleich (Root, A, B)
        assertEquals(nodesBefore.size(), nodesAfter.size());
        assertEquals(rootSizeBefore, rootAfter.getSubTreeSize(),
                "Structural subtree size of root must not change on logical delete");

        // Eine Node sollte deleted sein, eine aktiv
        long deletedCount = nodesAfter.stream().filter(CrdtNode::isDeleted).count();
        long activeCount = nodesAfter.stream().filter(CrdtNode::isActive).count();

        assertEquals(1L, deletedCount);
        assertEquals(1L, activeCount);

        assertStructuralInvariant(doc);
    }

    @Test
    void deleteAllCharacters_keepsStructuralSizesButChangesVisibility() {
        CrdtDocument doc = newDocument();

        applyInsert(doc, 0, "ABC");
        assertEquals("ABC", doc.render());

        CrdtNode rootBefore = getRoot(doc);
        int rootSizeBefore = rootBefore.getSubTreeSize();
        List<CrdtNode> nodesBefore = doc.traverse();
        assertEquals(3, nodesBefore.size());

        // Alles löschen
        applyDelete(doc, 0, 3);
        String rendered = doc.render();
        CrdtNode rootAfter = getRoot(doc);
        List<CrdtNode> nodesAfter = doc.traverse();

        // Text: leer
        assertEquals("", rendered);

        // Root-Strukturgröße unverändert
        assertEquals(rootSizeBefore, rootAfter.getSubTreeSize());
        assertEquals(4, rootAfter.getSubTreeSize(), "Root should still count Root + 3 nodes");

        // Alle 3 Nicht-Root-Nodes existieren noch, aber sind deleted
        assertEquals(3, nodesAfter.size());
        long deletedCount = nodesAfter.stream().filter(CrdtNode::isDeleted).count();
        long activeCount = nodesAfter.stream().filter(CrdtNode::isActive).count();

        assertEquals(3L, deletedCount);
        assertEquals(0L, activeCount);

        assertStructuralInvariant(doc);
    }

    @Test
    void mixedInsertsAndDeletes_structuralSizesOnlyGrow() {
        CrdtDocument doc = newDocument();

        // HELLO
        applyInsert(doc, 0, "HELLO");
        assertEquals("HELLO", doc.render());
        CrdtNode rootAfterInsert = getRoot(doc);
        List<CrdtNode> nodesAfterInsert = doc.traverse();

        assertEquals(5, nodesAfterInsert.size());
        assertEquals(6, rootAfterInsert.getSubTreeSize(),  // Root + 5 letters
                "Root structural size should be 6 after inserting 5 chars");

        int structuralBeforeDeletes = rootAfterInsert.getSubTreeSize();

        // Delete "EL" -> HLO
        applyDelete(doc, 1, 3);
        assertEquals("HLO", doc.render());
        CrdtNode rootAfterFirstDelete = getRoot(doc);
        assertEquals(structuralBeforeDeletes, rootAfterFirstDelete.getSubTreeSize(),
                "Structural subtree size should remain constant after deletes");

        // Insert "E" again -> HELO
        applyInsert(doc, 1, "E");
        assertEquals("HELO", doc.render());
        CrdtNode rootAfterReinsert = getRoot(doc);
        List<CrdtNode> nodesAfterReinsert = doc.traverse();

        // Es kam ein neuer Node hinzu
        assertEquals(nodesAfterInsert.size() + 1, nodesAfterReinsert.size());
        assertEquals(structuralBeforeDeletes + 1, rootAfterReinsert.getSubTreeSize(),
                "Structural subtree size should grow by 1 after new insert");

        assertStructuralInvariant(doc);
    }
}
