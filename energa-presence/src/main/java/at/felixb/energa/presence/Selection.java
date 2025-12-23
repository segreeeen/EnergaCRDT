package at.felixb.energa.presence;

import at.felixb.energa.crdt.Anchor;

public record Selection(Anchor start, Anchor end, Direction direction) implements Resolvable {

}
