package at.felixb.energa.crdt;

public record DocumentChangeEvent(DocumentChangeEventType eventType) {
    public enum DocumentChangeEventType {
        INSERT, DELETE
    }
}
