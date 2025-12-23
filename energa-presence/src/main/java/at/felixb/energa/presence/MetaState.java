package at.felixb.energa.presence;

import java.util.concurrent.atomic.AtomicLong;

public class MetaState {
    private final AtomicLong seq;
    private String metaBlob;

    public MetaState(long seq) {
        this.seq = new AtomicLong(seq);
    }

    public AtomicLong getSeq() {
        return seq;
    }

    public boolean updateSeq(Long nextSeq) {
        return seq.get() < nextSeq;
    }

    public String getMetaBlob() {
        return metaBlob;

    }

    public void setMetaBlob(String metaBlob) {
        this.metaBlob = metaBlob;
    }
}
