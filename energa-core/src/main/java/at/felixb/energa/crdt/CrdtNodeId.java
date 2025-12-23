package at.felixb.energa.crdt;

import java.util.UUID;

public record CrdtNodeId(UUID siteId, int counter) implements Comparable<CrdtNodeId> {

    @Override
    public int compareTo(CrdtNodeId o) {
        int counterCmp =  Integer.compare(this.counter, o.counter);
        if (counterCmp != 0) return counterCmp;
        return this.siteId.compareTo(o.siteId);
    }
}
