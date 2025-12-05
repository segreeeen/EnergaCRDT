package at.felixb.energa;

import java.util.ArrayList;
import java.util.List;

public class InsertOp implements UserOperation<CrdtInsertOp> {
    private final int position;
    private final String text;

    InsertOp(int position, String text) {
        this.position = position;
        this.text = text;
    }

    @Override
    public List<CrdtInsertOp> transformToInternal(Document document) {
        CrdtDocument crdtDocument = (CrdtDocument) document;
        CrdtNodeId parentNodeId = crdtDocument.findNodeByPosition(position).map(CrdtNode::getNodeId).orElseThrow();

        List<CrdtInsertOp> operations = new ArrayList<>();

        CrdtNodeId previousNodeId = parentNodeId;

        for (char c: text.toCharArray()) {
            CrdtNodeId insertNodeId = new CrdtNodeId(document.getSiteId(), crdtDocument.getNextNodeNr());
            operations.add(new CrdtInsertOp(previousNodeId, insertNodeId, c));
            previousNodeId = insertNodeId;
        }


        return operations;
    }
}
