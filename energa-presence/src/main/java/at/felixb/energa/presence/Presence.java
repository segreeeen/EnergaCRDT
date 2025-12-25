package at.felixb.energa.presence;

import java.util.concurrent.atomic.AtomicLong;

public class Presence {
    private final AtomicLong seq = new AtomicLong(0);
    private String sessionId;
    private final EphemeralState ephemeralState;
    private final MetaState metaState;

    public Presence(String sessionId) {
        this.sessionId = sessionId;
        this.ephemeralState = new EphemeralState();
        this.metaState = new MetaState();
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

    public AtomicLong getSeq() {
        return seq;
    }

    public boolean updateSeq(Long nextSeq) {
        if (checkSeq(nextSeq)) {
            seq.set(nextSeq);
            return true;
        }

        return false;
    }

    public boolean checkSeq(Long nextSeq) {
        return seq.get() < nextSeq;
    }
}
