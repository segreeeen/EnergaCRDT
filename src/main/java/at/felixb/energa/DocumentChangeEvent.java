package at.felixb.energa;

public record DocumentChangeEvent(DocumentChangeEventType eventType, CrdtNode node) {
    public enum DocumentChangeEventType {
        INSERT, DELETE
    }
}
