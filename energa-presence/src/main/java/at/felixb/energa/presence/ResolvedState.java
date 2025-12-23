package at.felixb.energa.presence;

import java.util.Map;

public class ResolvedState {
    private final Map<String, ResolvedPresence> stateMap;

    public ResolvedState(Map<String, ResolvedPresence> stateMap) {
        this.stateMap = stateMap;
    }

    public Map<String, ResolvedPresence> getStateMap() {
        return stateMap;
    }
}
