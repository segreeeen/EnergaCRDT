package at.felixb.simplerga;

import at.felixb.energa.crdt.CrdtOperation;
import at.felixb.energa.crdt.Document;
import at.felixb.energa.crdt.OperationFactory;
import at.felixb.energa.crdt.UserOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class CrdtDocumentTest {

    private Document document;

    @BeforeEach
    void setUp() {
        document = Document.create();
    }

    /**
     * Helper: wendet eine UserOperation (InsertOp / DeleteOp) auf das Dokument an,
     * indem sie zuerst in interne RGA-Operationen transformiert wird
     * und dann jede einzelne Operation via document.apply(...) ausgeführt wird.
     */
    private <T extends CrdtOperation> void applyUserOp(UserOperation<T> op) {
        List<T> internalOps = op.transformToInternal(document);
        for (T internalOp : internalOps) {
            document.apply(internalOp);
        }
    }

    @Test
    void render_emptyDocument_returnsEmptyString() {
        assertEquals("", document.render());
    }

    @Test
    void insert_zeroIntoEmpty_insertsText() {
        applyUserOp(OperationFactory.createInsertOp(0, "abc"));
        assertEquals("abc", document.render());
    }

    @Test
    void insert_insertAtEnd_appendsText() {
        applyUserOp(OperationFactory.createInsertOp(0, "abc"));
        assertEquals("abc", document.render());

        int endPos = document.render().length(); // 3
        applyUserOp(OperationFactory.createInsertOp(endPos, "def"));

        assertEquals("abcdef", document.render());
    }

    @Test
    void insert_insertAtBeginning_prependsText() {
        applyUserOp(OperationFactory.createInsertOp(0, "world"));
        assertEquals("world", document.render());

        applyUserOp(OperationFactory.createInsertOp(0, "hello "));

        assertEquals("hello world", document.render());
    }

    @Test
    void insert_insertInMiddle_insertsBetweenCharacters() {
        applyUserOp(OperationFactory.createInsertOp(0, "ac"));
        assertEquals("ac", document.render());

        // Position 1 ist zwischen 'a' und 'c'
        applyUserOp(OperationFactory.createInsertOp(1, "b"));

        assertEquals("abc", document.render());
    }

    @Test
    void insert_negativeIndex_throwsException() {
        applyUserOp(OperationFactory.createInsertOp(0, "abc"));
        String before = document.render();

        assertThrows(NoSuchElementException.class,
                () -> applyUserOp(OperationFactory.createInsertOp(-1, "X")));

        // Dokument bleibt unverändert
        assertEquals(before, document.render());
    }

    @Test
    void insert_indexGreaterThanLength_throwsException() {
        applyUserOp(OperationFactory.createInsertOp(0, "abc"));
        String before = document.render();

        int tooLarge = document.render().length() + 1; // 4 bei "abc"

        assertThrows(NoSuchElementException.class,
                () -> applyUserOp(OperationFactory.createInsertOp(tooLarge, "X")));

        // Dokument bleibt unverändert
        assertEquals(before, document.render());
    }

    @Test
    void delete_deleteMiddleRange_removesCharacters() {
        applyUserOp(OperationFactory.createInsertOp(0, "abcdef"));
        assertEquals("abcdef", document.render());

        // Löscht Zeichen an Position 2 und 3: 'c' und 'd'
        applyUserOp(OperationFactory.createDeleteOp(2, 4));

        assertEquals("abef", document.render());
    }

    @Test
    void delete_deleteFirstCharacter() {
        applyUserOp(OperationFactory.createInsertOp(0, "abc"));
        assertEquals("abc", document.render());

        applyUserOp(OperationFactory.createDeleteOp(0, 1));

        assertEquals("bc", document.render());
    }

    @Test
    void delete_deleteLastCharacter() {
        applyUserOp(OperationFactory.createInsertOp(0, "abc"));
        assertEquals("abc", document.render());

        int len = document.render().length(); // 3
        applyUserOp(OperationFactory.createDeleteOp(len - 1, len));

        assertEquals("ab", document.render());
    }

    @Test
    void delete_deleteEntireString_resultsInEmpty() {
        applyUserOp(OperationFactory.createInsertOp(0, "abc"));
        assertEquals("abc", document.render());

        int len = document.render().length();
        applyUserOp(OperationFactory.createDeleteOp(0, len));

        assertEquals("", document.render());
    }

    @Test
    void delete_startEqualsEnd_doesNothing() {
        applyUserOp(OperationFactory.createInsertOp(0, "abc"));
        String before = document.render();

        applyUserOp(OperationFactory.createDeleteOp(1, 1));

        assertEquals(before, document.render());
    }

    @Test
    void delete_negativeStart_doesNothing() {
        applyUserOp(OperationFactory.createInsertOp(0, "abc"));
        String before = document.render();

        applyUserOp(OperationFactory.createDeleteOp(-1, 1));

        assertEquals(before, document.render());
    }

    @Test
    void delete_endTooLarge_doesNothing() {
        applyUserOp(OperationFactory.createInsertOp(0, "abc"));
        String before = document.render();

        int tooLarge = document.render().length() + 1; // 4 bei "abc"
        applyUserOp(OperationFactory.createDeleteOp(0, tooLarge));

        assertEquals(before, document.render());
    }

    @Test
    void combinedOperations_insertAndDelete_behaveLikeTextEditing() {
        // Start: ""
        applyUserOp(OperationFactory.createInsertOp(0, "Hello World"));
        assertEquals("Hello World", document.render());

        // "Hello World" -> lösche " World"
        applyUserOp(OperationFactory.createDeleteOp(5, 11));
        assertEquals("Hello", document.render());

        // Füge ", CRDT" hinten an
        applyUserOp(OperationFactory.createInsertOp(document.render().length(), ", CRDT"));
        assertEquals("Hello, CRDT", document.render());

        // Füge am Anfang "Say: " ein
        applyUserOp(OperationFactory.createInsertOp(0, "Say: "));
        assertEquals("Say: Hello, CRDT", document.render());
    }
}
