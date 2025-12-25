package at.felixb.energa.crdt;

import java.util.*;

import static at.felixb.energa.crdt.DocumentChangeEvent.*;

public class CrdtDocument implements Document {

    private final UUID siteId;
    private final CrdtNode root;
    private final Map<CrdtNodeId, CrdtNode> indexedNodeAccessMap = new HashMap<>();
    private final List<CrdtOperation> operations = new ArrayList<>();
    private final Map<CrdtNodeId, List<CrdtInsertOp>> pendingInsertOps = new HashMap<>();
    private final Map<CrdtNodeId, CrdtDeleteOp> pendingDeleteOps = new HashMap<>();
    private final List<DocumentChangedListener> changeListeners = new ArrayList<>();
    private final LinearOrderCache linearOrderCache;

    private int nodeCounter = 0;
    private long revision = 0;

    CrdtDocument() {
        this.root = new CrdtNode(Document.ROOT_SITE_ID, getNextNodeNr());

        this.siteId = UUID.randomUUID();
        this.indexedNodeAccessMap.put(root.getNodeId(), this.root);
        linearOrderCache = new LinearOrderCache(this);
    }


    // #### Public
    public List<CrdtNode> getLinearOrder() {
        return linearOrderCache.getCopyWithDeletedNodes();
    }

    public List<CrdtNode> getActiveOnlyLinearOrder() {
        return linearOrderCache.getCopyWithActiveOnlyNodes();
    }

    @Override
    public String render() {
        int n = linearOrderCache.visibleSize();
        StringBuilder sb = new StringBuilder(n);

        // direkt über sichtbare iterieren
        linearOrderCache.forEachVisibleNode(node -> sb.append(node.getCharacter()));

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

        this.revision++;
    }

    @Override
    public UUID getSiteId() {
        return siteId;
    }

    /**
     * Returns an Anchor representing the caret at the given gap index.
     * <p>
     * The caret index is interpreted as a gap index in the visible linear order
     * (0 <= caretIndex <= N), where N is the number of visible nodes.
     * <br/><br/>
     * If gravity == Left:
     * <br/>- the anchor attaches to the left neighbor of the caret
     * <br/>- node = visible node at caretIndex - 1
     * <br/><br/>
     * If gravity == Right:
     * <br/>- the anchor attaches to the right neighbor of the caret
     * <br/>- node = visible node at caretIndex
     * <br/><br/>
     * Edge cases:
     * <br/>- If caretIndex <= 0, the anchor is clamped to the start:
     * Anchor(HEAD, Left)
     * <br/>- If caretIndex >= N, the anchor is clamped to the end:
     * Anchor(lastVisibleNode, Left)
     * <br/><br/>
     * At the document boundaries, gravity is ignored since only one neighbor exists.
     *
     * @param caretIndex caret gap index in visible order
     * @param gravity    requested gravity (left/right)
     */
    @Override
    public Anchor createAnchor(int caretIndex, Gravity gravity) {
        var active = getActiveOnlyLinearOrder(); // visible nodes only
        int N = active.size();

        if (N == 0) return new Anchor(root.getNodeId(), Gravity.LEFT);

        // edge cases: first or last possible caret position
        if (caretIndex <= 0) return new Anchor(root.getNodeId(), Gravity.LEFT);
        if (caretIndex >= N) return new Anchor(active.get(N - 1).getNodeId(), Gravity.LEFT);

        // normal case: position somewhere in the middle
        CrdtNodeId id = switch (gravity) {
            case LEFT -> active.get(caretIndex - 1).getNodeId();
            case RIGHT -> active.get(caretIndex).getNodeId();
        };

        return new Anchor(id, gravity);
    }

    /**
     * Resolves an Anchor to a caret gap index in the current visible linear order.
     * <p>
     * An Anchor represents a caret position by storing a neighboring node and a
     * gravity indicating which side of that node the caret is attached to.
     * <p>
     * Resolution rules:
     * <br/>
     * - If the anchor's node is visible:
     * <br/>- gravity == LEFT  → caret index = indexOf(node) + 1
     * <br/>- gravity == RIGHT → caret index = indexOf(node)
     * <p>
     * - If the anchor's node is not visible (e.g. tombstoned):
     * <br/>- gravity == LEFT  → search left for the nearest visible node
     * <br/>- gravity == RIGHT → search right for the nearest visible node
     * <br/>- apply the same index rules to the found node
     * <p>
     * - If no visible node exists in the search direction:
     * <br/>- resolve to the start (caret index 0) or end (caret index N)
     * <p>
     * The returned caret index is always clamped to the valid range [0, N],
     * where N is the number of visible nodes.
     *
     * @param anchor the anchor to resolve
     * @return the resolved caret gap index
     */
    @Override
    public int resolveAnchor(Anchor anchor) {
        var node = indexedNodeAccessMap.get(anchor.anchorId());
        if (node == null) return 0;


        //node is visible
        if (node.isVisible()) {
            return switch (anchor.gravity()) {
                case LEFT -> linearOrderCache.indexOfVisible(node) + 1;
                case RIGHT -> linearOrderCache.indexOfVisible(node);
            };
        }

        // node is invisible (i.e. deleted) and we have to resolve the position
        // first: find the next left/right (depends on gravity) visible node
        // then find the position of that node in the visible-only linear order
        int indexNotVisible = linearOrderCache.getIndexOf(node);
        var linearOrder = linearOrderCache.getCopyWithDeletedNodes();
        if (indexNotVisible < 0) return 0;

        return switch (anchor.gravity()) {
            case LEFT -> {
                // we don't check the node itself twice. start with the node to the left
                for (int i = indexNotVisible - 1; i >= 0; i--) {
                    CrdtNode nextNode = linearOrder.get(i);
                    if (nextNode.isVisible()) {
                        yield linearOrderCache.indexOfVisible(nextNode) + 1;
                    }
                }

                yield 0;
            }
            case RIGHT -> {
                // we don't check the node itself twice. start with the node to the right
                for (int i = indexNotVisible + 1; i < linearOrder.size(); i++) {
                    CrdtNode nextNode = linearOrder.get(i);
                    if (nextNode.isVisible()) {
                        yield linearOrderCache.indexOfVisible(nextNode);
                    }
                }

                yield linearOrderCache.visibleSize();
            }
        };
    }




    @Override
    public Range resolveRange(Anchor a, Anchor b) {
        return new Range(resolveAnchor(a), resolveAnchor(b));
    }

    @Override
    public void registerDocumentChangedListener(DocumentChangedListener listener) {
        this.changeListeners.add(listener);
    }

    public List<CrdtNode> traverse() {
        List<CrdtNode> nodes = new ArrayList<>();
        for (CrdtNode n : root.getChildren()) {
            visit(n, nodes);
        }
        return nodes;
    }

    public CrdtNode getRoot() {
        return root;
    }

    @Override
    public long getRevision() {
        return revision;
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

    int getNextNodeNr() {
        return nodeCounter++;
    }

    // #### Private

    private void handlePendingOps(CrdtNode insertedNode) {
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
                    linearOrderCache.insertNode(insertNode);

                    handlePendingOps(insertNode);

                    fireDocumentChanged(new DocumentChangeEvent(DocumentChangeEventType.INSERT));
                },
                () -> addPendingInsertOp(op));
    }

    private void applyDelete(CrdtDeleteOp op) {
        Optional.ofNullable(this.indexedNodeAccessMap.get(op.getDeleteNodeId())).ifPresentOrElse(node -> {
            node.delete();

            linearOrderCache.setVisible(node, false);

            fireDocumentChanged(new DocumentChangeEvent(DocumentChangeEventType.DELETE));
        }, () -> addPendingDeleteOp(op));


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

    private void fireDocumentChanged(DocumentChangeEvent documentChangedEvent) {
        for (DocumentChangedListener listener : changeListeners) {
            listener.onDocumentChanged(documentChangedEvent);
        }
    }

    public int indexOfVisible(CrdtNode node) {
        return linearOrderCache.indexOfVisible(node);
    }
}
