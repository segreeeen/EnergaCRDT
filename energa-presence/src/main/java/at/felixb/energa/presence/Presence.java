package at.felixb.energa.presence;

public class Presence {

    private String sessionId;
    private final EphemeralState ephemeralState;
    private final MetaState metaState;

    public Presence(String sessionId) {
        this.sessionId = sessionId;
        this.ephemeralState = new EphemeralState(0L);
        this.metaState = new MetaState(0L);
    }

    public String getSessionId() {
        return sessionId;
    }

    public EphemeralState getEphemeralState() {
        return ephemeralState;
    }

    public MetaState getMetaState() {
        return metaState;
    }
}
