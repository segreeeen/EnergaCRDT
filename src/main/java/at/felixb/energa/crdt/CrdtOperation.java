package at.felixb.energa.crdt;

public abstract class CrdtOperation {
    enum OperationType {
        INSERT, DELETE
    }

    final private OperationType operationType;

    CrdtOperation(OperationType operationType) {
        this.operationType = operationType;
    }

    OperationType getOperationType() {
        return operationType;
    }
}
