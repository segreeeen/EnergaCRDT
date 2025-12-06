package at.felixb.energa;

import java.util.*;
import java.util.stream.Stream;

public class LinearOrderCache {

    private final List<CrdtNode> cache = new ArrayList<>();
    private final CrdtDocument document;
    private boolean dirty = false;
    private Map<CrdtNodeId, Integer> nodeIdIndexMap = new HashMap<>();

    public LinearOrderCache(CrdtDocument document) {
        super();
        this.document = document;
    }

    public void insertNode(CrdtNode node) {
        int index = getDfsInsertIndex(node);

        cache.add(index, node); // insert new Node

        nodeIdIndexMap.entrySet().stream().filter(e -> e.getValue() >= index).forEach(e -> {
            e.setValue(e.getValue() + 1);
        });

        nodeIdIndexMap.put(node.getNodeId(), index);
    }

    public boolean cacheDirty() {
        return dirty;
    }


    public void renew() {
        cache.clear();
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

        int parentIndex;
        if (parent == document.getRoot()) {
            parentIndex = 0;
        } else {
            parentIndex = nodeIdIndexMap.get(parent.getNodeId());
        }

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
        return cache.stream();
    }

    public List<CrdtNode> getCacheCopy() {
        return new ArrayList<>(cache);
    }
}
