package at.felixb.energa.crdt;

import java.util.ArrayList;
import java.util.List;

public class DeleteOp implements UserOperation<CrdtDeleteOp> {
    private final int positionStart;
    private final int positionEnd;

    DeleteOp(int  startPosition, int endPosition) {
        this.positionStart = startPosition;
        this.positionEnd = endPosition;
    }

    @Override
    public List<CrdtDeleteOp> transformToInternal(Document document) {
        CrdtDocument crdtDocument = (CrdtDocument) document;
        List<CrdtDeleteOp> rgaDeleteOps = new ArrayList<>();
        List<CrdtNode> nodes = crdtDocument.getLinearOrder().stream().filter(CrdtNode::isActive).toList();

        if (positionStart < 0 || positionStart > nodes.size()) return rgaDeleteOps;
        if (positionEnd < 0 || positionEnd > nodes.size()) return rgaDeleteOps;

        for (int i = positionStart; i < positionEnd; i++) {
            rgaDeleteOps.add(new CrdtDeleteOp(nodes.get(i).getNodeId()));
        }
        return rgaDeleteOps;
    }
}
