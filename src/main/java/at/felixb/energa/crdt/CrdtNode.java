package at.felixb.energa.crdt;

import java.util.*;

public class CrdtNode {

    private final CrdtNodeId nodeId;
    private final char character;
    private boolean deleted = false;
    private TreeMap<CrdtNodeId, CrdtNode> children = new TreeMap<>(Collections.reverseOrder()); //automatically sorts by ID
    private int subTreeSize = 1;
    private CrdtNode parent;

    //RootNode Constructor
    CrdtNode(UUID siteId, int nodeId) {
        this.nodeId = new CrdtNodeId(siteId, nodeId);
        this.character = '\0';
        this.deleted = true;
    }

    CrdtNode(CrdtNodeId id, char c) {
        this.nodeId = id;
        this.character = c;
    }

    public List<CrdtNode> getChildren() {
        return children.values().stream().toList();
    }

    public void addChild(CrdtNode child) {
        children.put(child.getNodeId(), child);
        child.setParent(this);
        incrementSubTreeSize();
    }

    public CrdtNodeId getNodeId() {
        return nodeId;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public boolean isActive() {
        return !deleted;
    }

    public void delete() {
        this.deleted = true;
    }

    public char getCharacter() {
        return character;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CrdtNode node = (CrdtNode) o;
        return Objects.equals(nodeId, node.nodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(nodeId);
    }

    public void setParent(CrdtNode parent) {
        this.parent = parent;
    }

    void incrementSubTreeSize() {
        this.subTreeSize++;
        if (parent != null) {
            parent.incrementSubTreeSize();
        }
    }

    public int getSubTreeSize() {
        return subTreeSize;
    }

    public CrdtNode getParent() {
        return this.parent;
    }
}
