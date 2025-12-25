package at.felixb.energa.presence;

import at.felixb.energa.crdt.Anchor;
import at.felixb.energa.crdt.Document;
import at.felixb.energa.crdt.Gravity;
import at.felixb.energa.crdt.Range;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PresenceRegistryImplTest {

    private Document document;
    private PresenceRegistryImpl registry;

    @BeforeEach
    void setUp() {
        document = Mockito.mock(Document.class);
        when(document.getRevision()).thenReturn(42L);
        registry = new PresenceRegistryImpl(document);

        registry.addPresence("s1");
        registry.addPresence("s2");
    }

    @Test
    void updatePresence_seqGate_ignoresEqualOrLowerSeq() {
        registry.updatePresence("s1", 5L, "A", null);
        assertEquals("A", registry.getPresence("s1").getMetaState().getMetaBlob());

        // equal seq -> ignored
        registry.updatePresence("s1", 5L, "B", null);
        assertEquals("A", registry.getPresence("s1").getMetaState().getMetaBlob());

        // lower seq -> ignored
        registry.updatePresence("s1", 4L, "C", null);
        assertEquals("A", registry.getPresence("s1").getMetaState().getMetaBlob());
    }

    @Test
    void updatePresence_partialUpdate_metaOnlyOrEphemeralsOnly() {
        // meta only
        registry.updatePresence("s1", 1L, "META-1", null);
        assertEquals("META-1", registry.getPresence("s1").getMetaState().getMetaBlob());
        assertTrue(registry.getPresence("s1").getEphemeralState().getResolvables().isEmpty());

        // ephemerals only with higher seq -> should NOT wipe meta
        // RawCaret needs: position + gravity (-1 or +1). Pick -1 for LEFT.
        registry.updatePresence("s1", 2L, null, List.of(new RawCaret(123, -1)));

        assertEquals("META-1", registry.getPresence("s1").getMetaState().getMetaBlob());
        assertEquals(1, registry.getPresence("s1").getEphemeralState().getResolvables().size());
    }


    @Test
    void updatePresence_unknownSession_isDropped() {
        registry.updatePresence("does-not-exist", 1L, "X", List.of());
        assertNull(registry.getPresence("does-not-exist"));
    }

    @Test
    void updatePresence_metaBlobTooLarge_throwsAndDoesNotChangeState() {
        registry.updatePresence("s1", 1L, "ok", null);
        assertEquals("ok", registry.getPresence("s1").getMetaState().getMetaBlob());

        // 8001 bytes in UTF-8 (ASCII)
        String tooLarge = "a".repeat(8001);

        assertThrows(IllegalArgumentException.class,
                () -> registry.updatePresence("s1", 2L, tooLarge, null));

        // state should remain unchanged because update failed
        assertEquals("ok", registry.getPresence("s1").getMetaState().getMetaBlob());
        // and seq should not have advanced (important!)
        assertEquals(1L, registry.getPresence("s1").getSeq().get());
    }

    @Test
    void getResolvedState_containsAllSessions_andIncludesDocumentRevision_andResolvesEphemerals() {
        Anchor caretAnchor = mock(Anchor.class);
        Anchor selStart = mock(Anchor.class);
        Anchor selEnd = mock(Anchor.class);

        // Registry creates anchors from raw input => mock createAnchor(...)
        when(document.createAnchor(eq(7), any(Gravity.class))).thenReturn(caretAnchor);
        when(document.createAnchor(eq(10), any(Gravity.class))).thenReturn(selStart);
        when(document.createAnchor(eq(20), any(Gravity.class))).thenReturn(selEnd);

        // Resolve results
        when(document.resolveAnchor(caretAnchor)).thenReturn(7);

        Range range = mock(Range.class);
        when(range.startIndex()).thenReturn(10);
        when(range.endIndex()).thenReturn(20);
        when(document.resolveRange(selStart, selEnd)).thenReturn(range);

        // send raw presence (direction/gravity must be -1 or +1 with your "reject" policy)
        registry.updatePresence("s1", 1L, "m1",
                List.of(
                        new RawCaret(7, -1),
                        new RawSelection(10, -1, 20, 1, 1)
                )
        );

        ResolvedState resolvedState = registry.getResolvedState();

        // document revision forwarded
        assertEquals(42L, resolvedState.getDocumentRevision());

        // all sessions included (s1 + s2)
        assertTrue(resolvedState.getStateMap().containsKey("s1"));
        assertTrue(resolvedState.getStateMap().containsKey("s2"));

        ResolvedPresence s1 = resolvedState.getStateMap().get("s1");
        assertEquals("s1", s1.sessionId());
        assertEquals(1L, s1.seq());
        assertEquals("m1", s1.metaBlob());

        // resolutions produced
        List<Resolved> items = s1.resolutions();
        assertEquals(2, items.size());

        assertTrue(items.stream().anyMatch(r -> r instanceof ResolvedCaret));
        assertTrue(items.stream().anyMatch(r -> r instanceof ResolvedSelection));

        ResolvedCaret caret = (ResolvedCaret) items.stream().filter(r -> r instanceof ResolvedCaret).findFirst().orElseThrow();
        assertEquals(7, caret.position());

        ResolvedSelection sel = (ResolvedSelection) items.stream().filter(r -> r instanceof ResolvedSelection).findFirst().orElseThrow();
        assertEquals(10, sel.getStartIndex());
        assertEquals(20, sel.getEndIndex());
        assertEquals(Direction.FORWARD, sel.getDirection());
    }

}
