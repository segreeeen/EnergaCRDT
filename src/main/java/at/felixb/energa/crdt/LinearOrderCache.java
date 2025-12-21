package at.felixb.energa.crdt;


import at.felixb.energa.bpluslist.BPlusList;

import java.util.*;
import java.util.stream.Stream;

public class LinearOrderCache {

    private BPlusList<CrdtNode> cache = new BPlusList<>(32);
    private final Map<CrdtNodeId, Integer> nodeIdIndexMap = new HashMap<>();
    private final CrdtDocument document;
    private boolean dirty = false;

    public LinearOrderCache(CrdtDocument document) {
        super();
        this.document = document;
    }

    public void insertNode(CrdtNode node) {
        int index = getDfsInsertIndex(node);

        cache.add(index, node); // insert new Node
    }

    public boolean cacheDirty() {
        return dirty;
    }


    public void renew() {
        this.cache = new BPlusList<>(32);
        cache.addAll(document.traverse());
        renewIdIndexMap();
        dirty = false;
    }

    private void renewIdIndexMap() {
        this.nodeIdIndexMap.clear();
        for (int i = 0; i < this.cache.size(); i++) {
            nodeIdIndexMap.put(this.cache.get(i).getNodeId(), i);
        }
    }

    private int getDfsInsertIndex(CrdtNode insertNode) {

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


    public int size() {
        return cache.size();
    }

    /**
     * Returns Node by Index
     * @param i
     * @return
     */
    public CrdtNode getIndexOf(int i) {
        return cache.get(i);
    }

    /**
     * Returns Index by Node
     * @param node
     * @return
     */
    public int getIndexOf(CrdtNode node) {
        return cache.indexOf(node);
    }

    public Stream<CrdtNode> stream() {
        return cache.toList().stream();
    }

    public List<CrdtNode> getCopyWithActiveOnlyNodes() {
        return cache.toVisibleList();
    }

    public int visibleSize() {
        return cache.visibleSize();
    }

    public boolean isVisible(CrdtNode value) {
        return cache.isVisible(value);
    }

    public int indexOfVisible(CrdtNode value) {
        return cache.indexOfVisible(value);
    }

    public CrdtNode getVisible(int visibleIndex) {
        return cache.getVisible(visibleIndex);
    }

    public List<CrdtNode> getCopyWithDeletedNodes() {
        return cache.toList();
    }

    public void setVisible(CrdtNode node, boolean newVisible) {
        cache.setVisible(node, newVisible);
    }
}
