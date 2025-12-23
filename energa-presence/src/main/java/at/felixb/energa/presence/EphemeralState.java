package at.felixb.energa.presence;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class EphemeralState {
    private final AtomicLong seq;
    private List<Resolvable> resolvables = new ArrayList<>();

    public EphemeralState(long seq) {
        this.seq = new AtomicLong(seq);
    }

    public AtomicLong getSeq() {
        return seq;
    }

    public boolean updateSeq(Long nextSeq) {
        return seq.get() < nextSeq;
    }

    public List<Resolvable> getResolvables() {
        return resolvables;
    }

    public void setResolvables(List<Resolvable> resolvables) {
        this.resolvables = resolvables;
    }
}
