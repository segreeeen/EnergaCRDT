package at.felixb.energa.presence;

/**
 *
 * @param position index of caret
 * @param gravity -1 = BACKWARD, 1 = FORWARD
 */
public record RawCaret(int position, int gravity) implements Raw {
}
