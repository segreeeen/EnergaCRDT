package at.felixb.energa.crdt;

public interface DocumentChangedListener {
    void onDocumentChanged(DocumentChangeEvent event);
}
