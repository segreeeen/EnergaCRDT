package at.felixb.energa;

public interface OperationFactory {
    static InsertOp createInsertOp(int position, String text) {
        return new InsertOp(position, text);
    }

    static DeleteOp createDeleteOp(int startPosition, int endPosition) {
        return new DeleteOp(startPosition, endPosition);
    }
}
