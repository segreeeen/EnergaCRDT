package at.felixb.energa;

public class CrdtInsertOp extends CrdtOperation {

    private final CrdtNodeId parentNodeId;
    private final CrdtNodeId insertNodeId;
    private final char character;

    public CrdtInsertOp(CrdtNodeId parentNodeId, CrdtNodeId insertNodeId, char character) {
        super(OperationType.INSERT);
        this.parentNodeId = parentNodeId;
        this.insertNodeId = insertNodeId;
        this.character = character;
    }

    public CrdtNodeId getParentNodeId() {
        return parentNodeId;
    }

    public CrdtNodeId getInsertNodeId() {
        return insertNodeId;
    }

    public char getCharacter() {
        return character;
    }
}
