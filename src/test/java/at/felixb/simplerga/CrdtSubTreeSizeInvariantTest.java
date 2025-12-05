package at.felixb.simplerga;

import at.felixb.energa.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CrdtSubTreeSizeInvariantTest {

    // Hilfsfunktion: neues CRDT-Dokument
    private CrdtDocument newDocument() {
        return (CrdtDocument) Document.create();
    }

    // Root via Reflection holen (weil private in CrdtDocument)
    private CrdtNode getRoot(CrdtDocument doc) {
        try {
            Field f = CrdtDocument.class.getDeclaredField("root");
            f.setAccessible(true);
            return (CrdtNode) f.get(doc);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to access root field via reflection", e);
        }
    }

    // Insert über die „öffentliche“ CRDT-Schiene
    private void applyInsert(CrdtDocument doc, int position, String text) {
        InsertOp op = OperationFactory.createInsertOp(position, text);
        List<CrdtInsertOp> internal = op.transformToInternal(doc);
        internal.forEach(doc::apply);
    }

    // Delete über die „öffentliche“ CRDT-Schiene
    private void applyDelete(CrdtDocument doc, int start, int end) {
        DeleteOp op = OperationFactory.createDeleteOp(start, end);
        List<CrdtDeleteOp> internal = op.transformToInternal(doc);
        internal.forEach(doc::apply);
    }

    /**
     * Naive Berechnung: traversiert den Teilbaum und zählt alle sichtbaren Nodes.
     * Diese Zahl ist die „wahre“ SubTreeSize dieses Nodes.
     */
    private int computeSubTreeSizeNaive(CrdtNode node) {
        int count = node.isActive() ? 1 : 0;
        for (CrdtNode child : node.getChildren()) {
            count += computeSubTreeSizeNaive(child);
        }
        return count;
    }

    /**
     * Prüft für root und alle Nodes aus traverse(), dass:
     *
     *   node.getSubTreeSize() == Anzahl sichtbarer Nodes im Teilbaum (naiv gezählt)
     */
    private void assertSubTreeInvariant(CrdtDocument doc) {
        CrdtNode root = getRoot(doc);

        assertNodeSubTreeInvariant(root);
        for (CrdtNode node : doc.traverse()) {
            assertNodeSubTreeInvariant(node);
        }
    }

    private void assertNodeSubTreeInvariant(CrdtNode node) {
        int expected = computeSubTreeSizeNaive(node);
        int actual = node.getSubTreeSize();

        assertEquals(expected, actual,
                () -> "Subtree invariant violated for node " + node.getNodeId()
                        + " expected=" + expected + " actual=" + actual);
    }

    @Test
    void emptyDocument_invariantHolds() {
        CrdtDocument doc = newDocument();

        // Sanity: leerer Text, keine Nicht-Root-Nodes
        assertEquals("", doc.render());
        assertTrue(doc.traverse().isEmpty());

        // Invariante checken
        assertSubTreeInvariant(doc);
    }

    @Test
    void singleInsert_invariantHolds() {
        CrdtDocument doc = newDocument();

        applyInsert(doc, 0, "A");
        assertEquals("A", doc.render());

        // Invariante muss jetzt gelten
        assertSubTreeInvariant(doc);
    }

    @Test
    void threeInserts_invariantHolds() {
        CrdtDocument doc = newDocument();

        applyInsert(doc, 0, "ABC");
        assertEquals("ABC", doc.render());

        assertSubTreeInvariant(doc);
    }

    @Test
    void deleteFirst_in_AB_invariantHolds() {
        CrdtDocument doc = newDocument();

        applyInsert(doc, 0, "AB");
        assertEquals("AB", doc.render());

        applyDelete(doc, 0, 1); // löscht 'A'
        assertEquals("B", doc.render());

        assertSubTreeInvariant(doc);
    }

    @Test
    void deleteMiddle_in_ABC_invariantHolds() {
        CrdtDocument doc = newDocument();

        applyInsert(doc, 0, "ABC");
        assertEquals("ABC", doc.render());

        applyDelete(doc, 1, 2); // löscht 'B'
        assertEquals("AC", doc.render());

        assertSubTreeInvariant(doc);
    }

    @Test
    void deleteAll_in_ABC_invariantHolds() {
        CrdtDocument doc = newDocument();

        applyInsert(doc, 0, "ABC");
        assertEquals("ABC", doc.render());

        applyDelete(doc, 0, 3); // löscht 'ABC'
        assertEquals("", doc.render());

        assertSubTreeInvariant(doc);
    }

    @Test
    void mixedInsertsAndDeletes_invariantHolds() {
        CrdtDocument doc = newDocument();

        // HELLO
        applyInsert(doc, 0, "HELLO");
        assertEquals("HELLO", doc.render());
        assertSubTreeInvariant(doc);

        // Delete "EL" -> HLO
        applyDelete(doc, 1, 3);
        assertEquals("HLO", doc.render());
        assertSubTreeInvariant(doc);

        // Insert "E" at pos 1 -> HELO
        applyInsert(doc, 1, "E");
        assertEquals("HELO", doc.render());
        assertSubTreeInvariant(doc);

        // Delete last char "O" -> HEL
        applyDelete(doc, 3, 4);
        assertEquals("HEL", doc.render());
        assertSubTreeInvariant(doc);
    }
}
