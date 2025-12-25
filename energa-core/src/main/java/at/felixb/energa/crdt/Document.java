package at.felixb.energa.crdt;

import java.util.List;
import java.util.UUID;

public interface Document {
    UUID ROOT_SITE_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    static Document create() {
        return new CrdtDocument();
    }

    static Document fromLog(List<CrdtOperation> operations) {
        CrdtDocument document = new CrdtDocument();
        for (CrdtOperation operation : operations) {
            document.apply(operation);
        }

        return document;
    }

    String render();

    void apply(CrdtOperation operation);

    UUID getSiteId();

    Anchor createAnchor(int index, Gravity gravity);

    int resolveAnchor(Anchor anchor);

    Range resolveRange(Anchor a, Anchor b);

    void registerDocumentChangedListener(DocumentChangedListener listener);

    long getRevision();
}
