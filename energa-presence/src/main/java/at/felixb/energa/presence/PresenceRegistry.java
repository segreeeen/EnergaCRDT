package at.felixb.energa.presence;

import at.felixb.energa.crdt.Document;

import java.util.List;

public interface PresenceRegistry {

    ResolvedState getResolvedState();

    Presence getPresence(String sessionId);

    void addPresence(String sessionId);

    void updatePresence(String sessionId, long seq, String metaBlob, List<Raw> resolvables);

    void deletePresence(String sessionId);

    static PresenceRegistry create(Document document) {
        return new PresenceRegistryImpl(document);
    }
}
