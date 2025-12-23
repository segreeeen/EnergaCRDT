package at.felixb.energa.crdt;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrdtDocumentCrdtTest {

    // Wir nehmen eine eigene Site für die "normalen" Nodes
    private static final UUID SITE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static CrdtNodeId nodeId(int counter) {
        return new CrdtNodeId(SITE, counter);
    }

    private static CrdtNodeId rootId() {
        // Root-Id ist jetzt global definiert in Document
        return new CrdtNodeId(Document.ROOT_SITE_ID, 0);
    }

    /**
     * Szenario 1:
     * A -> B -> C Insert-Kette, aber in "falscher" Reihenfolge im Log.
     *
     * Erwartung:
     * - Render-Ergebnis ist identisch, egal in welcher Reihenfolge die Ops im Log stehen.
     * - Hier: "ABC"
     */
    @Test
    void insertChain_outOfOrder_yieldsSameRender() {
        CrdtNodeId root = rootId();
        CrdtNodeId a = nodeId(1);
        CrdtNodeId b = nodeId(2);
        CrdtNodeId c = nodeId(3);

        CrdtInsertOp insertA = new CrdtInsertOp(root, a, 'A');
        CrdtInsertOp insertB = new CrdtInsertOp(a, b, 'B');
        CrdtInsertOp insertC = new CrdtInsertOp(b, c, 'C');

        List<CrdtOperation> logInOrder = Arrays.asList(insertA, insertB, insertC);
        List<CrdtOperation> logShuffled = Arrays.asList(insertB, insertC, insertA);

        Document docInOrder = Document.fromLog(logInOrder);
        Document docShuffled = Document.fromLog(logShuffled);

        assertEquals("ABC", docInOrder.render());
        assertEquals(docInOrder.render(), docShuffled.render());
    }

    /**
     * Szenario 2:
     * Delete kommt VOR Insert für denselben Node.
     *
     * Erwartung:
     * - Node existiert strukturell, ist aber deleted.
     * - Im gerenderten Text taucht das Zeichen nicht auf.
     */
    @Test
    void deleteBeforeInsert_yieldsDeletedNode() {
        CrdtNodeId root = rootId();
        CrdtNodeId x = nodeId(1);

        CrdtInsertOp insertX = new CrdtInsertOp(root, x, 'X');
        CrdtDeleteOp deleteX = new CrdtDeleteOp(x);

        // Reihenfolge: Delete zuerst, dann Insert
        List<CrdtOperation> log = Arrays.asList(deleteX, insertX);

        Document doc = Document.fromLog(log);

        assertEquals("", doc.render());
    }

    /**
     * Szenario 3:
     * Parent A, Child B (Parent=A), Delete B in "schlechter" Reihenfolge:
     *   Delete B, Insert B (Parent=A), Insert A
     *
     * Erwartung:
     * - B hängt unter A, ist aber gelöscht.
     * - Im gerenderten Text taucht B nicht auf.
     */
    @Test
    void deleteBeforeInsertWithParentChain_childIsDeleted() {
        CrdtNodeId root = rootId();
        CrdtNodeId a = nodeId(1);
        CrdtNodeId b = nodeId(2);

        CrdtInsertOp insertA = new CrdtInsertOp(root, a, 'A');
        CrdtInsertOp insertB = new CrdtInsertOp(a, b, 'B');
        CrdtDeleteOp deleteB = new CrdtDeleteOp(b);

        // Reihenfolge absichtlich "verrückt"
        List<CrdtOperation> log = Arrays.asList(deleteB, insertB, insertA);

        Document doc = Document.fromLog(log);

        // A sollte sichtbar sein, B nicht
        assertEquals("A", doc.render());
    }

    /**
     * Szenario 4:
     * Doppelte Operationen im Log (Insert & Delete mehrfach).
     *
     * Erwartung:
     * - Mehrfache Inserts mit derselben NodeId erzeugen keinen doppelten Node.
     * - Mehrfache Deletes sind idempotent.
     * - Render-Ergebnis ist identisch zu einer "bereinigten" History.
     */
    @Test
    void duplicateOperations_areIdempotent() {
        CrdtNodeId root = rootId();
        CrdtNodeId x = nodeId(1);

        CrdtInsertOp insertX = new CrdtInsertOp(root, x, 'X');
        CrdtDeleteOp deleteX = new CrdtDeleteOp(x);

        // "saubere" History: einmal Insert, einmal Delete
        List<CrdtOperation> cleanLog = Arrays.asList(insertX, deleteX);

        // "schmutzige" History: Duplikate
        List<CrdtOperation> noisyLog = Arrays.asList(
                insertX, insertX,          // Insert doppelt
                deleteX, deleteX, deleteX  // Delete mehrfach
        );

        Document cleanDoc = Document.fromLog(cleanLog);
        Document noisyDoc = Document.fromLog(noisyLog);

        assertEquals(cleanDoc.render(), noisyDoc.render());
        assertEquals("", cleanDoc.render());
    }

    /**
     * Szenario 5:
     * Gleiche Log-Operationen in verschiedenen Permutationen
     * führen zu identischen Renders.
     *
     * Beispiel:
     *  ROOT -A-> a -B-> b
     *  ROOT -C-> c
     */
    @Test
    void differentPermutations_sameOperations_sameRender() {
        CrdtNodeId root = rootId();
        CrdtNodeId a = nodeId(1);
        CrdtNodeId b = nodeId(2);
        CrdtNodeId c = nodeId(3);

        CrdtInsertOp insertA = new CrdtInsertOp(root, a, 'A');
        CrdtInsertOp insertB = new CrdtInsertOp(a, b, 'B');
        CrdtInsertOp insertC = new CrdtInsertOp(root, c, 'C'); // z.B. zweites Kind unter Root

        List<CrdtOperation> perm1 = Arrays.asList(insertA, insertB, insertC);
        List<CrdtOperation> perm2 = Arrays.asList(insertC, insertA, insertB);

        Document doc1 = Document.fromLog(perm1);
        Document doc2 = Document.fromLog(perm2);

        assertEquals(doc1.render(), doc2.render());
    }
}
