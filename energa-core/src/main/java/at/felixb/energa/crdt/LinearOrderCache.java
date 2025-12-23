package at.felixb.energa.crdt;


import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

class LinearOrderCache {

    private BPlusList<CrdtNode> cache = new BPlusList<>(32);
    private final Map<CrdtNodeId, Integer> nodeIdIndexMap = new HashMap<>();
    private final CrdtDocument document;
    private boolean dirty = false;

    LinearOrderCache(CrdtDocument document) {
        super();
        this.document = document;
    }

    void insertNode(CrdtNode node) {
        int index = getDfsInsertIndex(node);

        cache.add(index, node); // insert new Node
    }

    boolean cacheDirty() {
        return dirty;
    }


    void renew() {
        this.cache = new BPlusList<>(32);
        cache.addAll(document.traverse());
        renewIdIndexMap();
        dirty = false;
    }

    void renewIdIndexMap() {
        this.nodeIdIndexMap.clear();
        for (int i = 0; i < this.cache.size(); i++) {
            nodeIdIndexMap.put(this.cache.get(i).getNodeId(), i);
        }
    }

    int getDfsInsertIndex(CrdtNode insertNode) {

        CrdtNode parent = insertNode.getParent();

        int parentIndex = (parent == document.getRoot())
                ? 0
                : cache.indexOf(parent);

        int prefixSum = 0;

        // children sind reverseOrder() -> Iteration: größte IDs zuerst
        for (CrdtNode child : parent.getChildren()) {
            int cmp = child.getNodeId().compareTo(insertNode.getNodeId());

            // cmp > 0 => childId ist größer => kommt VOR insertNode in reverseOrder
            if (cmp > 0) {
                prefixSum += child.getSubTreeSize();
            } else {
                // jetzt sind wir bei <= insertNodeId, danach kommt nur noch kleiner -> kann abbrechen
                break;
            }
        }

        int parentSelfOffset = (parent == document.getRoot()) ? 0 : 1;

        return parentIndex + parentSelfOffset + prefixSum;
    }


    int size() {
        return cache.size();
    }

    /**
     * Returns Node by Index
     * @param i
     * @return
     */
    CrdtNode getIndexOf(int i) {
        return cache.get(i);
    }

    /**
     * Returns Index by Node
     * @param node
     * @return
     */
    int getIndexOf(CrdtNode node) {
        return cache.indexOf(node);
    }

    Stream<CrdtNode> stream() {
        return cache.toList().stream();
    }

    List<CrdtNode> getCopyWithActiveOnlyNodes() {
        return cache.toVisibleList();
    }

    int visibleSize() {
        return cache.visibleSize();
    }

    boolean isVisible(CrdtNode value) {
        return cache.isVisible(value);
    }

    int indexOfVisible(CrdtNode value) {
        return cache.indexOfVisible(value);
    }

    CrdtNode getVisible(int visibleIndex) {
        return cache.getVisible(visibleIndex);
    }

    List<CrdtNode> getCopyWithDeletedNodes() {
        return cache.toList();
    }

    void setVisible(CrdtNode node, boolean newVisible) {
        cache.setVisible(node, newVisible);
    }

    void forEachVisibleNode(Consumer<CrdtNode> consumer) {
        cache.forEachVisible(consumer);
    }
}
