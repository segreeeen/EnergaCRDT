package at.felixb.energa.presence;

import at.felixb.energa.crdt.Range;

import java.util.Objects;

public final class ResolvedSelection implements Resolvable {
    private final Range range;
    private final Direction direction;

    public ResolvedSelection(Range range, Direction direction) {
        this.range = range;
        this.direction = direction;
    }

    public int getStartIndex() {
        return range.startIndex();
    }

    public int getEndIndex() {
        return range.endIndex();
    }

    public Direction direction() {
        return direction;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ResolvedSelection) obj;
        return Objects.equals(this.range, that.range) &&
                Objects.equals(this.direction, that.direction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(range, direction);
    }

    @Override
    public String toString() {
        return "ResolvedSelection[" +
                "range=" + range + ", " +
                "direction=" + direction + ']';
    }

}
