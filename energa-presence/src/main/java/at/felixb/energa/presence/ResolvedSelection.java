package at.felixb.energa.presence;

import at.felixb.energa.crdt.Range;

import java.util.Objects;

public final class ResolvedSelection implements Resolved {

    private final int startIndex;
    private final int endIndex;
    private final Direction direction;

    public ResolvedSelection(int startIndex, int endIndex, Direction direction) {
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.direction = direction;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public int getEndIndex() {
        return endIndex;
    }

    public Direction getDirection() {
        return direction;
    }


}
