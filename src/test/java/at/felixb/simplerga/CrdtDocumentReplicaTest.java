package at.felixb.simplerga;

import at.felixb.energa.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrdtDocumentReplicaTest {

    // Zwei unterschiedliche Sites für "User A" und "User B"
    private static final UUID SITE_A = UUID.fromString("00000000-0000-0000-0000-0000000000A1");
    private static final UUID SITE_B = UUID.fromString("00000000-0000-0000-0000-0000000000B1");

    private static CrdtNodeId rootId() {
        // Root-Id ist jetzt global stabil über Document.ROOT_SITE_ID + counter 0
        return new CrdtNodeId(Document.ROOT_SITE_ID, 0);
    }

    private static CrdtNodeId nodeIdA(int counter) {
        return new CrdtNodeId(SITE_A, counter);
    }

    private static CrdtNodeId nodeIdB(int counter) {
        return new CrdtNodeId(SITE_B, counter);
    }

    /**
     * Zwei Repliken bekommen dieselben Ops, aber in unterschiedlicher Reihenfolge.
     * Die eine Site ist "Autor" aller Nodes.
     *
     * Erwartung:
     * - Beide Documents konvergieren auf denselben gerenderten Text.
     */
    @Test
    void twoReplicas_sameOperationsDifferentOrder_converge() {
        Document docA = Document.create();
        Document docB = Document.create();

        CrdtNodeId root = rootId();
        CrdtNodeId a = nodeIdA(1);
        CrdtNodeId b = nodeIdA(2);
        CrdtNodeId c = nodeIdA(3);

        CrdtInsertOp insertA = new CrdtInsertOp(root, a, 'A');
        CrdtInsertOp insertB = new CrdtInsertOp(a, b, 'B');
        CrdtInsertOp insertC = new CrdtInsertOp(b, c, 'C');

        // Replica A: Ops kommen in "schöner" Reihenfolge
        List<CrdtOperation> orderA = Arrays.asList(insertA, insertB, insertC);
        orderA.forEach(docA::apply);

        // Replica B: Ops kommen in "kaputter" Reihenfolge
        List<CrdtOperation> orderB = Arrays.asList(insertC, insertA, insertB);
        orderB.forEach(docB::apply);

        assertEquals(docA.render(), docB.render());
        // zur Sicherheit: es sollte "ABC" sein (wenn Root -> A -> B -> C)
        assertEquals("ABC", docA.render());
    }

    /**
     * Zwei Sites fügen gleichzeitig (konkurrierend) unter Root ein:
     * - Site A: 'A'
     * - Site B: 'B'
     *
     * Beide Repliken sehen die Ops in unterschiedlicher Reihenfolge.
     *
     * Erwartung:
     * - Beide Documents konvergieren auf dasselbe Ergebnis (z.B. "AB" oder "BA",
     *   je nach NodeId-Order), aber identisch zueinander.
     */
    @Test
    void twoReplicas_concurrentInserts_underRoot_converge() {
        Document docA = Document.create();
        Document docB = Document.create();

        CrdtNodeId root = rootId();
        CrdtNodeId aNode = nodeIdA(1);
        CrdtNodeId bNode = nodeIdB(1);

        CrdtInsertOp insertA = new CrdtInsertOp(root, aNode, 'A');
        CrdtInsertOp insertB = new CrdtInsertOp(root, bNode, 'B');

        // Replica A: erst A, dann B
        docA.apply(insertA);
        docA.apply(insertB);

        // Replica B: erst B, dann A
        docB.apply(insertB);
        docB.apply(insertA);

        String renderA = docA.render();
        String renderB = docB.render();

        // Beide müssen konvergieren
        assertEquals(renderA, renderB);

        // Optional: wir können auch prüfen, dass genau zwei Zeichen da sind
        // und dass es eine der beiden möglichen Reihenfolgen ist.
        // (Die konkrete Reihenfolge hängt von der UUID-Order SITE_A vs. SITE_B ab.)
        // assertTrue(renderA.equals("AB") || renderA.equals("BA"));
    }

    /**
     * Beide Repliken haben eigene lokale Inserts und sehen dann die Remote-Ops
     * in unterschiedlicher Reihenfolge.
     *
     * Beispiel:
     * - A fügt 'A' ein
     * - B fügt 'X' ein
     * - Dann tauschen sie ihre Ops aus (in verschiedener Reihenfolge)
     *
     * Erwartung:
     * - Beide Documents haben am Ende denselben Text (z.B. "AX" oder "XA").
     */
    @Test
    void twoReplicas_localAndRemoteOps_interleaveAndConverge() {
        Document docA = Document.create();
        Document docB = Document.create();

        CrdtNodeId root = rootId();

        // Lokale Ops: A erzeugt 'A', B erzeugt 'X'
        CrdtNodeId aNode = nodeIdA(1);
        CrdtNodeId xNode = nodeIdB(1);

        CrdtInsertOp opA_local = new CrdtInsertOp(root, aNode, 'A');
        CrdtInsertOp opX_local = new CrdtInsertOp(root, xNode, 'X');

        // A wendet nur seine lokale Op an
        docA.apply(opA_local);

        // B wendet nur seine lokale Op an
        docB.apply(opX_local);

        // Jetzt "Sync": A bekommt X, B bekommt A – aber in unterschiedlicher Reihenfolge
        // A: sieht zuerst seine eigene, dann X
        docA.apply(opX_local);

        // B: sieht zuerst seine eigene, dann A
        docB.apply(opA_local);

        String renderA = docA.render();
        String renderB = docB.render();

        assertEquals(renderA, renderB);
        // wieder optional: prüfen, dass genau zwei Zeichen da sind
        // assertEquals(2, renderA.length());
    }

    /**
     * Kombiniertes Szenario mit Inserts und Delete über zwei Sites.
     *
     * - Site A: fügt 'A' und 'B' ein
     * - Site B: löscht 'B'
     * - Ops kommen in unterschiedlicher Reihenfolge bei beiden Repliken an.
     *
     * Erwartung:
     * - Beide Repliken konvergieren auf denselben Text.
     */
    @Test
    void twoReplicas_insertsAndDeletes_converge() {
        Document docA = Document.create();
        Document docB = Document.create();

        CrdtNodeId root = rootId();
        CrdtNodeId aNode = nodeIdA(1);
        CrdtNodeId bNode = nodeIdA(2); // B wird von Site A erzeugt

        CrdtInsertOp insertA = new CrdtInsertOp(root, aNode, 'A');
        CrdtInsertOp insertB = new CrdtInsertOp(aNode, bNode, 'B');
        CrdtDeleteOp deleteB = new CrdtDeleteOp(bNode); // z.B. von Site B initiiert, aber Id von A

        // Replica A: sieht Inserts in Ordnung, Delete später
        List<CrdtOperation> opsForA = Arrays.asList(insertA, insertB, deleteB);
        opsForA.forEach(docA::apply);

        // Replica B: sieht Delete früher, Inserts in anderer Reihenfolge
        List<CrdtOperation> opsForB = Arrays.asList(deleteB, insertB, insertA);
        opsForB.forEach(docB::apply);

        // Am Ende soll B gelöscht sein, A bleiben
        assertEquals("A", docA.render());
        assertEquals(docA.render(), docB.render());
    }
}
