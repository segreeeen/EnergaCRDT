package at.felixb.energa.presence;

import java.util.Map;

public class ResolvedState {
    private final Map<String, ResolvedPresence> stateMap;
    private final long documentRevision;

    public ResolvedState(Map<String, ResolvedPresence> stateMap, long documentRevision) {
        this.stateMap = stateMap;
        this.documentRevision = documentRevision;
    }

    public Map<String, ResolvedPresence> getStateMap() {
        return stateMap;
    }

    public long getDocumentRevision() {
        return documentRevision;
    }
}
