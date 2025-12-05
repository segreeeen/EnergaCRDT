package at.felixb.energa;

public class CrdtDeleteOp extends CrdtOperation {
    private final CrdtNodeId deleteNodeId;

    public CrdtDeleteOp(CrdtNodeId deleteNodeId) {
        super(OperationType.DELETE);

        this.deleteNodeId = deleteNodeId;
    }

    public CrdtNodeId getDeleteNodeId() {
        return deleteNodeId;
    }
}
