package at.felixb.energa.presence;

import at.felixb.energa.crdt.Anchor;
import at.felixb.energa.crdt.Document;
import at.felixb.energa.crdt.Gravity;
import at.felixb.energa.crdt.Range;

import java.io.UnsupportedEncodingException;
import java.util.*;

public class PresenceRegistryImpl implements PresenceRegistry {

    private final Document document;
    private final HashMap<String, Presence> presenceMap = new HashMap<>();

    PresenceRegistryImpl(Document document) {
        this.document = document;
    }

    @Override
    public ResolvedState getResolvedState() {
        Map<String, ResolvedPresence> stateMap = new HashMap<>();

        presenceMap.forEach((key, value) -> {

            ResolvedPresence resolvedPresence = new ResolvedPresence(value.getSessionId(),
                    value.getSeq().get(),
                    value.getMetaState().getMetaBlob(),
                    resolveEphemerals(value.getEphemeralState().getResolvables()));

            stateMap.put(key, resolvedPresence);
        });

        return new ResolvedState(stateMap, document.getRevision());
    }

    private List<Resolved> resolveEphemerals(List<Resolvable> resolvables) {
        return resolvables.stream().map(r -> {
            if (r instanceof Caret) {
                return new ResolvedCaret(document.resolveAnchor(((Caret) r).anchor()));
            }

            if (r instanceof Selection) {
                Range range = document.resolveRange(((Selection) r).start(), ((Selection) r).end());
                if (range.startIndex() == range.endIndex()) {
                    return new ResolvedCaret(range.startIndex());
                }
                return new ResolvedSelection(range.startIndex(), range.endIndex(), ((Selection) r).direction());
            }

            return null;
        }).filter(Objects::nonNull).toList();
    }

    @Override
    public Presence getPresence(String sessionId) {
        return presenceMap.get(sessionId);
    }

    @Override
    public void addPresence(String sessionId) {
        presenceMap.put(sessionId, new Presence(sessionId));
    }

    @Override
    public void updatePresence(String sessionId, long seq, String metaBlob, List<Raw> rawList) {
        if (!presenceMap.containsKey(sessionId)) {
            return;
        }

        Presence presence = presenceMap.get(sessionId);

        // seq gate (no side effects)
        if (!presence.checkSeq(seq)) {
            return;
        }

        // --- 1) validate + prepare (no side effects) ---

        // meta: validate size before committing seq
        String nextMeta = null;
        if (metaBlob != null) {
            validateMetaBlobSize(metaBlob);
            nextMeta = metaBlob;
        }

        // ephemerals: validate all raw values and build resolvables
        List<Resolvable> nextResolvables = null;
        if (rawList != null) {
            nextResolvables = new ArrayList<>(rawList.size());

            for (Raw raw : rawList) {
                if (raw instanceof RawCaret rc) {
                    int g = rc.gravity();
                    if (!isSignedUnit(g)) {
                        return; // reject whole update
                    }
                    Gravity gravity = (g == -1) ? Gravity.LEFT : Gravity.RIGHT;
                    nextResolvables.add(new Caret(document.createAnchor(rc.position(), gravity)));
                    continue;
                }

                if (raw instanceof RawSelection rs) {
                    // reject nulls as well (strict)
                    if (rs.startGravity() == null || rs.endGravity() == null) {
                        return;
                    }

                    int sg = rs.startGravity();
                    int eg = rs.endGravity();
                    int dir = rs.direction();

                    if (!isSignedUnit(sg) || !isSignedUnit(eg) || !isSignedUnit(dir)) {
                        return; // reject whole update
                    }

                    Gravity startG = (sg == -1) ? Gravity.LEFT : Gravity.RIGHT;
                    Gravity endG   = (eg == -1) ? Gravity.LEFT : Gravity.RIGHT;
                    Direction direction = (dir == -1) ? Direction.BACKWARD : Direction.FORWARD;

                    Anchor start = document.createAnchor(rs.startPos(), startG);
                    Anchor end   = document.createAnchor(rs.endPos(), endG);

                    nextResolvables.add(new Selection(start, end, direction));
                    continue;
                }

                // unknown Raw type -> reject (strict)
                return;
            }
        }

        // --- 2) commit once ---
        presence.updateSeq(seq);

        // --- 3) apply prepared values ---
        if (nextResolvables != null) {
            presence.getEphemeralState().setResolvables(nextResolvables);
        }
        if (nextMeta != null) {
            presence.getMetaState().setMetaBlob(nextMeta);
        }
    }

    @Override
    public void deletePresence(String sessionId) {
        presenceMap.remove(sessionId);
    }

    private static boolean isSignedUnit(int v) {
        return v == -1 || v == 1;
    }

    private static void validateMetaBlobSize(String metaBlob) {
        try {
            byte[] utf8Bytes = metaBlob.getBytes("UTF-8");
            if (utf8Bytes.length > 8000) {
                throw new IllegalArgumentException("metaBlob length exceeds 8kb");
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }


    private boolean updateMeta(Presence presence, String metaBlob) {
        final byte[] utf8Bytes;
        try {
            utf8Bytes = metaBlob.getBytes("UTF-8");
            if (utf8Bytes.length > 8000) {
                throw new IllegalArgumentException("metaBlob length exceeds 8kb");
            }

            presence.getMetaState().setMetaBlob(metaBlob);
            return true;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

}
