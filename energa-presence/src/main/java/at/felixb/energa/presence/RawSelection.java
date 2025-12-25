package at.felixb.energa.presence;

/**
 *
 * @param startPos index
 * @param startGravity -1 = left, 1 = right
 * @param endPos index end of selection
 * @param endGravity -1 = left, 1 = right
 * @param direction -1 = BACKWARD, 1 = FORWARD
 */
public record RawSelection(int startPos, Integer startGravity, int endPos, Integer endGravity, int direction) implements Raw {
}
