package at.felixb.energa.presence;

import at.felixb.energa.crdt.Document;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PresenceRegistry {

    private final Document document;
    private final HashMap<String, Presence> presenceMap = new HashMap<>();

    public PresenceRegistry(Document document) {
        this.document = document;
        document.registerDocumentChangedListener((event) -> {
            resolve();
        });
    }

    private ResolvedState resolve() {
        Map<String, ResolvedPresence> stateMap = new HashMap<>();



        return null;
    }

    public Presence getPresence(String sessionId) {
        return presenceMap.get(sessionId);
    }

    public void addPresence(String sessionId) {
        presenceMap.put(sessionId, new Presence(sessionId));
    }

    public void updateMeta(String sessionId, long seq, String metaBlob) {
        if (!presenceMap.containsKey(sessionId)) {
            return;
        }

        final byte[] utf8Bytes;
        try {
            utf8Bytes = metaBlob.getBytes("UTF-8");
            if (utf8Bytes.length > 8000) {
                throw new IllegalArgumentException("metaBlob length exceeds 8kb");
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        Presence presence = presenceMap.get(sessionId);
        MetaState metaState = presence.getMetaState();

        if (metaState.updateSeq(seq)) {
            metaState.setMetaBlob(metaBlob);
        }
    }

    public void updateEphemeral(String sessionId, long seq, List<Resolvable> resolvables) {
        if (!presenceMap.containsKey(sessionId)) {
            return;
        }

        Presence presence = presenceMap.get(sessionId);
        EphemeralState ephemeralState = presence.getEphemeralState();

        if (ephemeralState.updateSeq(seq)) {
            ephemeralState.setResolvables(resolvables);
        }
    }
}
