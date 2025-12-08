package at.felixb.energa.crdt;

import at.felixb.energa.btree.BPlusList;

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

        int subTreeSizeSum = parent.getChildren().stream()
                .filter(child -> child.getNodeId().compareTo(insertNode.getNodeId()) > 0)
                .mapToInt(CrdtNode::getSubTreeSize)
                .sum();

        return parentIndex + (parent == document.getRoot() ? 0 : 1) + subTreeSizeSum;
    }

    public int size() {
        return cache.size();
    }

    public CrdtNode get(int i) {
        return cache.get(i);
    }

    public Stream<CrdtNode> stream() {
        return cache.toList().stream();
    }

    public List<CrdtNode> getCacheCopy() {
        return cache.toList();
    }
}
