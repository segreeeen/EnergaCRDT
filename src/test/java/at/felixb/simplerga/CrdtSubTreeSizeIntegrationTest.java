package at.felixb.simplerga;

import at.felixb.energa.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CrdtSubTreeSizeIntegrationTest {

    // Hilfsfunktion: neues CRDT-Dokument
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

    private void applyInsert(CrdtDocument doc, int position, String text) {
        InsertOp op = OperationFactory.createInsertOp(position, text);
        List<CrdtInsertOp> internal = op.transformToInternal(doc);
        internal.forEach(doc::apply);
    }

    private void applyDelete(CrdtDocument doc, int start, int end) {
        DeleteOp op = OperationFactory.createDeleteOp(start, end);
        List<CrdtDeleteOp> internal = op.transformToInternal(doc);
        internal.forEach(doc::apply);
    }

    @Test
    void emptyDocument_rootSubTreeSizeAndRender() {
        CrdtDocument doc = newDocument();

        String rendered = doc.render();
        CrdtNode root = getRoot(doc);
        List<CrdtNode> nodes = doc.traverse();

        // Erwartung: leerer Text
        assertEquals("", rendered);

        // Erwartung: keine Nicht-Root-Knoten
        assertEquals(0, nodes.size());

        // Erwartung (gewünschtes Verhalten): Root-SubTreeSize = 0
        // Wenn dieser Test rot ist und getSubTreeSize() = 1 o.ä., ist deine Root-Logik noch nicht fertig.
        assertEquals(0, root.getSubTreeSize());
    }

    @Test
    void singleInsert_A_rootAndNodeSubTreeSize() {
        CrdtDocument doc = newDocument();

        applyInsert(doc, 0, "A");

        String rendered = doc.render();
        CrdtNode root = getRoot(doc);
        List<CrdtNode> nodes = doc.traverse();

        // Render-Erwartung
        assertEquals("A", rendered);

        // Es sollte genau ein CrdtNode (für 'A') existieren
        assertEquals(1, nodes.size());

        CrdtNode aNode = nodes.get(0);

        // Erwartung: genau ein sichtbares Zeichen im Dokument
        assertEquals(1, root.getSubTreeSize());

        // Erwartung: der 'A'-Node repräsentiert genau sich selbst
        assertTrue(aNode.isActive());
        assertEquals(1, aNode.getSubTreeSize());
    }

    @Test
    void threeInserts_ABC_rootSubTreeSizeAndVisibleNodes() {
        CrdtDocument doc = newDocument();

        applyInsert(doc, 0, "ABC");

        String rendered = doc.render();
        CrdtNode root = getRoot(doc);
        List<CrdtNode> nodes = doc.traverse();

        // Render-Erwartung
        assertEquals("ABC", rendered);

        // Es sollten drei CrdtNodes existieren (A, B, C)
        assertEquals(3, nodes.size());

        // Alle drei sollten aktiv sein
        long activeCount = nodes.stream().filter(CrdtNode::isActive).count();
        assertEquals(3L, activeCount);

        // Erwartung: Root-SubTreeSize zählt alle sichtbaren Zeichen
        assertEquals(3, root.getSubTreeSize());
    }

    @Test
    void deleteFirstCharacter_in_AB_updatesSubTreeSizes() {
        CrdtDocument doc = newDocument();

        applyInsert(doc, 0, "AB");
        assertEquals("AB", doc.render());

        CrdtNode rootBefore = getRoot(doc);
        int rootSizeBefore = rootBefore.getSubTreeSize();

        // Delete 'A' (Position 0 bis 1)
        applyDelete(doc, 0, 1);

        String rendered = doc.render();
        CrdtNode rootAfter = getRoot(doc);
        List<CrdtNode> nodes = doc.traverse();

        // Render-Erwartung
        assertEquals("B", rendered);

        // Erwartung: Root-SubTreeSize wurde um 1 reduziert
        assertEquals(rootSizeBefore - 1, rootAfter.getSubTreeSize());
        assertEquals(1, rootAfter.getSubTreeSize());

        // Es existieren nach wie vor 2 CRDT-Nodes (A ist tombstone, B aktiv)
        assertEquals(2, nodes.size());

        long activeCount = nodes.stream().filter(CrdtNode::isActive).count();
        long deletedCount = nodes.stream().filter(CrdtNode::isDeleted).count();

        assertEquals(1L, activeCount);
        assertEquals(1L, deletedCount);
    }

    @Test
    void deleteMiddleCharacter_in_ABC_updatesSubTreeSizes() {
        CrdtDocument doc = newDocument();

        applyInsert(doc, 0, "ABC");
        assertEquals("ABC", doc.render());

        CrdtNode rootBefore = getRoot(doc);
        int rootSizeBefore = rootBefore.getSubTreeSize();

        // Delete 'B' (Position 1 bis 2)
        applyDelete(doc, 1, 2);

        String rendered = doc.render();
        CrdtNode rootAfter = getRoot(doc);
        List<CrdtNode> nodes = doc.traverse();

        // Render-Erwartung
        assertEquals("AC", rendered);

        // Erwartung: Root-SubTreeSize wurde um 1 reduziert
        assertEquals(rootSizeBefore - 1, rootAfter.getSubTreeSize());
        assertEquals(2, rootAfter.getSubTreeSize());

        // Es existieren weiterhin 3 Nodes (A, B gelöscht, C aktiv)
        assertEquals(3, nodes.size());

        long activeCount = nodes.stream().filter(CrdtNode::isActive).count();
        long deletedCount = nodes.stream().filter(CrdtNode::isDeleted).count();

        assertEquals(2L, activeCount);
        assertEquals(1L, deletedCount);
    }

    @Test
    void deleteAllCharacters_in_ABC_resultsInZeroVisibleSubTree() {
        CrdtDocument doc = newDocument();

        applyInsert(doc, 0, "ABC");
        assertEquals("ABC", doc.render());

        // Alles löschen: Position 0 bis 3
        applyDelete(doc, 0, 3);

        String rendered = doc.render();
        CrdtNode root = getRoot(doc);
        List<CrdtNode> nodes = doc.traverse();

        // Render-Erwartung
        assertEquals("", rendered);

        // Erwartung: Root-SubTreeSize ist 0 (keine sichtbaren Zeichen)
        assertEquals(0, root.getSubTreeSize());

        // Alle Nodes sind tombstones
        assertEquals(3, nodes.size());
        long activeCount = nodes.stream().filter(CrdtNode::isActive).count();
        long deletedCount = nodes.stream().filter(CrdtNode::isDeleted).count();

        assertEquals(0L, activeCount);
        assertEquals(3L, deletedCount);
    }

    @Test
    void mixedInsertsAndDeletes_keepRootSubTreeSizeInSyncWithRenderedLength() {
        CrdtDocument doc = newDocument();

        // Start: HELLO
        applyInsert(doc, 0, "HELLO");
        assertEquals("HELLO", doc.render());
        CrdtNode rootAfterHello = getRoot(doc);
        assertEquals(5, rootAfterHello.getSubTreeSize());

        // Delete "EL" (Position 1 bis 3) -> HLO
        applyDelete(doc, 1, 3);
        assertEquals("HLO", doc.render());
        CrdtNode rootAfterDel = getRoot(doc);
        assertEquals(3, rootAfterDel.getSubTreeSize());

        // Insert "E" an Position 1 -> HELO
        applyInsert(doc, 1, "E");
        assertEquals("HELO", doc.render());
        CrdtNode rootAfterInsertE = getRoot(doc);
        assertEquals(4, rootAfterInsertE.getSubTreeSize());

        // Delete letztes Zeichen "O" (Position 3 bis 4) -> HEL
        applyDelete(doc, 3, 4);
        assertEquals("HEL", doc.render());
        CrdtNode rootAfterFinalDel = getRoot(doc);
        assertEquals(3, rootAfterFinalDel.getSubTreeSize());
    }
}
