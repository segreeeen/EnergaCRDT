package at.felixb.energa;

import java.util.*;

public class CrdtDocument implements Document{

    private final UUID siteId;
    private final CrdtNode root;
    private final Map<CrdtNodeId, CrdtNode> indexedNodeAccessMap = new HashMap<>();
    private final List<CrdtOperation> operations = new ArrayList<>();
    private final Map<CrdtNodeId, List<CrdtInsertOp>> pendingInsertOps = new HashMap<>();
    private final Map<CrdtNodeId, CrdtDeleteOp> pendingDeleteOps = new HashMap<>();
    private final List<DocumentChangedListener> changeListeners = new ArrayList<>();
    private final LinearOrderCache linearOrderCache;

    private int nodeCounter = 0;

    CrdtDocument() {
        this.root = new CrdtNode(Document.ROOT_SITE_ID, getNextNodeNr());

        this.siteId = UUID.randomUUID();
        this.indexedNodeAccessMap.put(root.getNodeId(), this.root);
        linearOrderCache = new LinearOrderCache(this);
    }


    // #### Public
    public List<CrdtNode> getLinearOrder() {
        if (linearOrderCache.cacheDirty()) {
            linearOrderCache.renew();
        }
        return linearOrderCache.getCacheCopy();
    }

    @Override
    public String render() {

        StringBuilder sb = new StringBuilder();

        getLinearOrder().stream().filter(CrdtNode::isActive).map(CrdtNode::getCharacter).forEach(sb::append);

        return sb.toString();
    }

    @Override
    public void apply(CrdtOperation operation) {
        if (operation instanceof CrdtInsertOp insertOp) {
            applyInsert(insertOp);
        } else if (operation instanceof CrdtDeleteOp deleteOp) {
            applyDelete(deleteOp);
        } else {
            throw new IllegalArgumentException("Unsupported op type: " + operation.getClass());
        }

        operations.add(operation);
    }

    @Override
    public UUID getSiteId() {
        return siteId;
    }

    @Override
    public void registerDocumentChangedListener(DocumentChangedListener listener) {
        this.changeListeners.add(listener);
    }

    // #### Package-Private

    Optional<CrdtNode> findNodeByPosition(int position) {

        if (position < 0) return Optional.empty();

        if (position == 0) return Optional.of(root);

        List<CrdtNode> linearOrder = getLinearOrder();

        if (position - 1 >= linearOrder.size()) return Optional.empty();


        return Optional.of(linearOrder.get(position - 1));
    }

    CrdtNode createNewNode(CrdtNodeId id, char c) {
        CrdtNode node = new CrdtNode(id, c);
        indexedNodeAccessMap.put(node.getNodeId(), node);
        return node;
    }

    public List<CrdtNode> traverse() {
        List<CrdtNode> nodes = new ArrayList<>();
        for (CrdtNode n : root.getChildren()) {
            visit(n, nodes);
        }
        return nodes;
    }

    int getNextNodeNr() {
        return nodeCounter++;
    }

    // #### Private

    private void onNodeCreated(CrdtNode insertedNode) {
        // handle pending insert ops
        handlePendingInsertsFor(insertedNode);
        pendingInsertOps.remove(insertedNode.getNodeId());

        // handle pending delete ops
        Optional.ofNullable(pendingDeleteOps.get(insertedNode.getNodeId())).ifPresent(this::applyDelete);
        pendingDeleteOps.remove(insertedNode.getNodeId());
    }

    private void handlePendingInsertsFor(CrdtNode insertedNode) {
        Optional.ofNullable(pendingInsertOps.get(insertedNode.getNodeId())).ifPresent(insertOps -> {
            insertOps.forEach(this::applyInsert);
        });


    }

    private void applyInsert(CrdtInsertOp op) {
        Optional.ofNullable(this.indexedNodeAccessMap.get(op.getParentNodeId())).ifPresentOrElse(
                parent -> {
                    if (Optional.ofNullable(this.indexedNodeAccessMap.get(op.getInsertNodeId())).isPresent()) return;

                    CrdtNode insertNode = createNewNode(op.getInsertNodeId(), op.getCharacter());
                    parent.addChild(insertNode);
                    onNodeCreated(insertNode);
                    fireDocumentChanged();
                },
                () -> addPendingInsertOp(op));
    }

    private void applyDelete(CrdtDeleteOp op) {
        Optional.ofNullable(this.indexedNodeAccessMap.get(op.getDeleteNodeId())).ifPresentOrElse(CrdtNode::delete, () -> addPendingDeleteOp(op));

        fireDocumentChanged();
    }

    private void addPendingInsertOp(CrdtInsertOp op) {
        pendingInsertOps.computeIfAbsent(op.getParentNodeId(), k -> new ArrayList<>()).add(op);
    }

    private void addPendingDeleteOp(CrdtDeleteOp op) {
        pendingDeleteOps.put(op.getDeleteNodeId(), op);
    }

    private void visit(CrdtNode node, List<CrdtNode> acc) {
        acc.add(node);
        for (CrdtNode child : node.getChildren()) {
            visit(child, acc);
        }
    }

    private void fireDocumentChanged() {
        for (DocumentChangedListener listener : changeListeners) {
            listener.onDocumentChanged();
        }
    }
}
