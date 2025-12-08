package at.felixb.energa.crdt;

public record DocumentChangeEvent(DocumentChangeEventType eventType, CrdtNode node) {
    public enum DocumentChangeEventType {
        INSERT, DELETE
    }
}
