package at.felixb.energa.bpluslist;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class BPlusList<V> {

    private final int t;
    private final int maxValues;    // max. Werte pro Blatt
    private final int maxChildren;  // max. Kinder pro innerem Knoten

    private Node<V> root;
    private int size;

    /**
     * Value -> NodeLocation (Leaf + Offset).
     * IdentityHashMap ist ideal für CRDT Nodes (Instance Identity).
     */
    private final Map<V, NodeLocation<V>> locationMap = new IdentityHashMap<>();

    /**
     * Value -> visible flag (truth source).
     * Leaves rebuild their BitSet from this map.
     */
    private final Map<V, Boolean> visibilityMap = new IdentityHashMap<>();

    public BPlusList(int t) {
        if (t < 2) {
            throw new IllegalArgumentException("t must be >= 2");
        }
        this.t = t;
        this.maxValues = 2 * t - 1;
        this.maxChildren = 2 * t;
        this.root = null;
        this.size = 0;
    }

    // -------------------------------------------------
    //  Sizes
    // -------------------------------------------------

    public int size() {
        return size;
    }

    public int visibleSize() {
        if (root == null) return 0;
        return root.visibleSubtreeSize;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    // -------------------------------------------------
    //  Plain (all-nodes) access
    // -------------------------------------------------

    public V get(int index) {
        checkIndex(index);
        Node<V> node = root;
        int pos = index;

        while (!node.leaf) {
            int prefix = 0;
            int childIndex = 0;

            for (; childIndex < node.children.size(); childIndex++) {
                Node<V> child = node.children.get(childIndex);
                int childSize = child.subtreeSize;
                if (pos < prefix + childSize) {
                    pos = pos - prefix;
                    break;
                }
                prefix += childSize;
            }

            node = node.children.get(childIndex);
        }

        return node.values.get(pos);
    }

    /**
     * Global index among ALL elements (visible + invisible).
     * O(height * degree)
     */
    public int indexOf(V value) {
        NodeLocation<V> loc = locationMap.get(value);
        if (loc == null) {
            return -1;
        }

        Node<V> node = loc.leaf;
        int index = loc.offsetInLeaf;

        while (node.parent != null) {
            Node<V> parent = node.parent;

            int prefix = 0;
            int k = node.indexInParent;
            for (int i = 0; i < k; i++) {
                prefix += parent.children.get(i).subtreeSize;
            }

            index += prefix;
            node = parent;
        }

        return index;
    }

    // -------------------------------------------------
    //  Visible-only access
    // -------------------------------------------------

    public boolean isVisible(V value) {
        Boolean v = visibilityMap.get(value);
        return v != null && v;
    }

    /**
     * Sets visibility (tombstone toggle).
     * Returns true if the value existed and visibility actually changed.
     */
    public boolean setVisible(V value, boolean visible) {
        NodeLocation<V> loc = locationMap.get(value);
        if (loc == null) return false;

        boolean oldVisible = isVisible(value);
        if (oldVisible == visible) return false;

        visibilityMap.put(value, visible);

        // Update leaf bitset and leaf.visibleSubtreeSize
        Node<V> leaf = loc.leaf;
        rebuildLeafVisibility(leaf);

        // Propagate delta up the tree
        int delta = visible ? 1 : -1;
        Node<V> n = leaf;
        while (n != null) {
            n.visibleSubtreeSize += delta;
            n = n.parent;
        }

        return true;
    }

    /**
     * Index among VISIBLE elements only.
     * Returns -1 if value not found or not visible.
     */
    public int indexOfVisible(V value) {
        NodeLocation<V> loc = locationMap.get(value);
        if (loc == null) return -1;
        if (!isVisible(value)) return -1;

        Node<V> node = loc.leaf;

        // visible count within the leaf before this offset
        int index = countVisibleBeforeInLeaf(node, loc.offsetInLeaf);

        // add visible sizes of left siblings up the tree
        while (node.parent != null) {
            Node<V> parent = node.parent;

            int prefix = 0;
            int k = node.indexInParent;
            for (int i = 0; i < k; i++) {
                prefix += parent.children.get(i).visibleSubtreeSize;
            }

            index += prefix;
            node = parent;
        }

        return index;
    }

    /**
     * Returns the visible element at visibleIndex (0..visibleSize-1).
     */
    public V getVisible(int visibleIndex) {
        if (visibleIndex < 0 || visibleIndex >= visibleSize()) {
            throw new IndexOutOfBoundsException("visibleIndex: " + visibleIndex + ", visibleSize: " + visibleSize());
        }

        Node<V> node = root;
        int pos = visibleIndex;

        while (!node.leaf) {
            int prefix = 0;
            int childIndex = 0;

            for (; childIndex < node.children.size(); childIndex++) {
                Node<V> child = node.children.get(childIndex);
                int childVisible = child.visibleSubtreeSize;
                if (pos < prefix + childVisible) {
                    pos = pos - prefix;
                    break;
                }
                prefix += childVisible;
            }

            node = node.children.get(childIndex);
        }

        int offset = findNthVisibleOffset(node, pos);
        return node.values.get(offset);
    }

    // -------------------------------------------------
    //  Mutations
    // -------------------------------------------------

    public void add(V value) {
        add(size, value);
    }

    /**
     * Inserts value at index among ALL elements (including invisible).
     * New values are visible by default.
     */
    public void add(int index, V value) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException("index: " + index + ", size: " + size);
        }

        if (root == null) {
            root = newLeaf(null, 0);
            root.values.add(value);
            root.subtreeSize = 1;

            visibilityMap.put(value, true);
            rebuildLeafVisibility(root);
            root.visibleSubtreeSize = 1;

            size = 1;
            locationMap.put(value, new NodeLocation<>(root, 0));
            return;
        }

        if (isFull(root)) {
            Node<V> newRoot = newInternal(null, 0);
            newRoot.children.add(root);
            root.parent = newRoot;
            root.indexInParent = 0;
            newRoot.subtreeSize = root.subtreeSize;
            newRoot.visibleSubtreeSize = root.visibleSubtreeSize;
            splitChild(newRoot, 0);
            root = newRoot;
        }

        visibilityMap.put(value, true);
        insertNonFull(root, index, value);
        size++;
    }

    public void addAll(List<V> values) {
        for (V v : values) {
            add(v);
        }
    }

    public V set(int index, V newValue) {
        checkIndex(index);

        Node<V> node = root;
        int pos = index;

        while (!node.leaf) {
            int prefix = 0;
            int childIndex = 0;

            for (; childIndex < node.children.size(); childIndex++) {
                Node<V> child = node.children.get(childIndex);
                int childSize = child.subtreeSize;
                if (pos < prefix + childSize) {
                    pos = pos - prefix;
                    break;
                }
                prefix += childSize;
            }

            node = node.children.get(childIndex);
        }

        V old = node.values.set(pos, newValue);

        // preserve visibility of old element (default true if old missing)
        boolean oldVisible = old == null || isVisible(old);

        if (old != null) {
            locationMap.remove(old);
            visibilityMap.remove(old);
        }

        locationMap.put(newValue, new NodeLocation<>(node, pos));
        visibilityMap.put(newValue, oldVisible);

        // leaf visibility might have changed if old/new visibility differs (rare),
        // rebuild to keep bitset consistent
        rebuildLeafVisibility(node);

        return old;
    }

    public List<V> toList() {
        List<V> result = new ArrayList<>();
        if (root == null) return result;

        Node<V> node = root;
        while (!node.leaf) {
            node = node.children.get(0);
        }

        while (node != null) {
            result.addAll(node.values);
            node = node.next;
        }

        return result;
    }

    public List<V> toVisibleList() {
        List<V> result = new ArrayList<>(visibleSize());
        if (root == null) return result;

        Node<V> node = root;
        while (!node.leaf) {
            node = node.children.get(0);
        }

        while (node != null) {
            // add only visible values in this leaf
            int bit = node.visibleBits.nextSetBit(0);
            while (bit >= 0) {
                result.add(node.values.get(bit));
                bit = node.visibleBits.nextSetBit(bit + 1);
            }
            node = node.next;
        }

        return result;
    }

    // -------------------------------------------------
    //  Insert internals
    // -------------------------------------------------

    private void insertNonFull(Node<V> node, int index, V value) {
        if (node.leaf) {
            node.values.add(index, value);
            node.subtreeSize++;

            // location
            locationMap.put(value, new NodeLocation<>(node, index));

            // update offsets for values to the right in same leaf
            for (int i = index + 1; i < node.values.size(); i++) {
                V v = node.values.get(i);
                NodeLocation<V> loc = locationMap.get(v);
                if (loc != null) {
                    loc.offsetInLeaf = i;
                }
            }

            // visibility bookkeeping: new element visible by default
            rebuildLeafVisibility(node);
            node.visibleSubtreeSize = countVisibleInLeaf(node);

            return;
        }

        int prefix = 0;
        int childIndex = 0;

        for (; childIndex < node.children.size(); childIndex++) {
            Node<V> child = node.children.get(childIndex);
            int childSize = child.subtreeSize;

            if (index <= prefix + childSize) {
                index = index - prefix;
                break;
            }
            prefix += childSize;
        }

        if (childIndex == node.children.size()) {
            childIndex = node.children.size() - 1;
            index = node.children.get(childIndex).subtreeSize;
        }

        Node<V> child = node.children.get(childIndex);

        if (isFull(child)) {
            splitChild(node, childIndex);

            Node<V> left = node.children.get(childIndex);
            Node<V> right = node.children.get(childIndex + 1);

            if (index > left.subtreeSize) {
                index -= left.subtreeSize;
                childIndex++;
                child = right;
            } else {
                child = left;
            }
        }

        int beforeVisible = child.visibleSubtreeSize;
        int beforeTotal = child.subtreeSize;

        insertNonFull(child, index, value);

        // update counts in this internal node based on child deltas
        node.subtreeSize += (child.subtreeSize - beforeTotal);
        node.visibleSubtreeSize += (child.visibleSubtreeSize - beforeVisible);
    }

    private boolean isFull(Node<V> node) {
        if (node.leaf) {
            return node.values.size() >= maxValues;
        } else {
            return node.children.size() >= maxChildren;
        }
    }

    private void splitChild(Node<V> parent, int childIndex) {
        Node<V> child = parent.children.get(childIndex);

        if (child.leaf) {
            Node<V> right = newLeaf(parent, childIndex + 1);

            int total = child.values.size();
            int mid = total / 2;

            for (int i = mid; i < total; i++) {
                right.values.add(child.values.get(i));
            }
            for (int i = total - 1; i >= mid; i--) {
                child.values.remove(i);
            }

            // update NodeLocations for left leaf
            for (int i = 0; i < child.values.size(); i++) {
                V v = child.values.get(i);
                NodeLocation<V> loc = locationMap.get(v);
                if (loc != null) {
                    loc.leaf = child;
                    loc.offsetInLeaf = i;
                }
            }

            // update NodeLocations for right leaf
            for (int i = 0; i < right.values.size(); i++) {
                V v = right.values.get(i);
                NodeLocation<V> loc = locationMap.get(v);
                if (loc != null) {
                    loc.leaf = right;
                    loc.offsetInLeaf = i;
                }
            }

            // rebuild visibility in both leaves
            rebuildLeafVisibility(child);
            rebuildLeafVisibility(right);

            child.subtreeSize = child.values.size();
            right.subtreeSize = right.values.size();

            child.visibleSubtreeSize = countVisibleInLeaf(child);
            right.visibleSubtreeSize = countVisibleInLeaf(right);

            // leaf chain
            right.next = child.next;
            child.next = right;

            parent.children.add(childIndex + 1, right);
            fixChildIndicesFrom(parent, childIndex + 1);

            // parent counts (subtreeSize/visibleSubtreeSize) do NOT change here
            // because we only redistributed values between leaves.

        } else {
            Node<V> right = newInternal(parent, childIndex + 1);

            int totalChildren = child.children.size();
            int mid = totalChildren / 2;

            for (int i = mid; i < totalChildren; i++) {
                Node<V> movedChild = child.children.get(i);
                right.children.add(movedChild);
                movedChild.parent = right;
                movedChild.indexInParent = right.children.size() - 1;
            }
            for (int i = totalChildren - 1; i >= mid; i--) {
                child.children.remove(i);
            }

            // recompute sizes for left/right internal nodes
            child.subtreeSize = 0;
            child.visibleSubtreeSize = 0;
            for (int i = 0; i < child.children.size(); i++) {
                Node<V> c = child.children.get(i);
                c.parent = child;
                c.indexInParent = i;
                child.subtreeSize += c.subtreeSize;
                child.visibleSubtreeSize += c.visibleSubtreeSize;
            }

            right.subtreeSize = 0;
            right.visibleSubtreeSize = 0;
            for (int i = 0; i < right.children.size(); i++) {
                Node<V> c = right.children.get(i);
                c.parent = right;
                c.indexInParent = i;
                right.subtreeSize += c.subtreeSize;
                right.visibleSubtreeSize += c.visibleSubtreeSize;
            }

            parent.children.add(childIndex + 1, right);
            fixChildIndicesFrom(parent, childIndex + 1);

            // parent counts do not change here either (redistribution).
        }
    }

    private void fixChildIndicesFrom(Node<V> parent, int startIndex) {
        for (int i = startIndex; i < parent.children.size(); i++) {
            Node<V> c = parent.children.get(i);
            c.parent = parent;
            c.indexInParent = i;
        }
    }

    // -------------------------------------------------
    //  Leaf visibility helpers
    // -------------------------------------------------

    private void rebuildLeafVisibility(Node<V> leaf) {
        if (!leaf.leaf) return;

        leaf.visibleBits.clear();
        for (int i = 0; i < leaf.values.size(); i++) {
            V v = leaf.values.get(i);
            if (isVisible(v)) {
                leaf.visibleBits.set(i);
            }
        }
    }

    private int countVisibleInLeaf(Node<V> leaf) {
        return leaf.visibleBits.cardinality();
    }

    private int countVisibleBeforeInLeaf(Node<V> leaf, int offsetExclusive) {
        int count = 0;
        int bit = leaf.visibleBits.nextSetBit(0);
        while (bit >= 0 && bit < offsetExclusive) {
            count++;
            bit = leaf.visibleBits.nextSetBit(bit + 1);
        }
        return count;
    }

    private int findNthVisibleOffset(Node<V> leaf, int n) {
        int count = 0;
        int bit = leaf.visibleBits.nextSetBit(0);
        while (bit >= 0) {
            if (count == n) return bit;
            count++;
            bit = leaf.visibleBits.nextSetBit(bit + 1);
        }
        throw new IllegalStateException("Visible index out of range in leaf (n=" + n + ")");
    }

    // -------------------------------------------------
    //  Node creation / bounds
    // -------------------------------------------------

    private Node<V> newLeaf(Node<V> parent, int indexInParent) {
        Node<V> n = new Node<>();
        n.leaf = true;
        n.values = new ArrayList<>();
        n.children = new ArrayList<>(); // stays empty for leaves
        n.subtreeSize = 0;
        n.visibleSubtreeSize = 0;
        n.visibleBits = new BitSet();
        n.next = null;
        n.parent = parent;
        n.indexInParent = indexInParent;
        return n;
    }

    private Node<V> newInternal(Node<V> parent, int indexInParent) {
        Node<V> n = new Node<>();
        n.leaf = false;
        n.values = null;
        n.children = new ArrayList<>();
        n.subtreeSize = 0;
        n.visibleSubtreeSize = 0;
        n.visibleBits = null; // not used
        n.next = null;
        n.parent = parent;
        n.indexInParent = indexInParent;
        return n;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index: " + index + ", size: " + size);
        }
    }

    // -------------------------------------------------
    //  VALIDATION
    // -------------------------------------------------

    public void validate() {
        List<String> errors = new ArrayList<>();

        if (root == null) {
            if (size != 0) {
                errors.add("root is null but size = " + size);
            }
        } else {
            int[] leafDepthHolder = new int[]{-1};
            validateNode(root, true, 0, leafDepthHolder, errors);

            if (root.subtreeSize != size) {
                errors.add("root.subtreeSize (" + root.subtreeSize + ") != size (" + size + ")");
            }
            if (root.visibleSubtreeSize < 0 || root.visibleSubtreeSize > root.subtreeSize) {
                errors.add("root.visibleSubtreeSize out of bounds: " + root.visibleSubtreeSize);
            }

            Node<V> firstLeaf = root;
            while (!firstLeaf.leaf) {
                if (firstLeaf.children.isEmpty()) {
                    errors.add("internal node with no children while searching first leaf");
                    break;
                }
                firstLeaf = firstLeaf.children.get(0);
            }

            int leafCountSum = 0;
            int leafVisibleSum = 0;

            Node<V> cur = firstLeaf;
            while (cur != null) {
                if (!cur.leaf) {
                    errors.add("non-leaf node found in leaf chain");
                    break;
                }
                leafCountSum += cur.values.size();
                leafVisibleSum += cur.visibleSubtreeSize;
                cur = cur.next;
            }

            if (leafCountSum != size) {
                errors.add("leaf chain value count (" + leafCountSum + ") != size (" + size + ")");
            }
            if (leafVisibleSum != visibleSize()) {
                errors.add("leaf chain visible count (" + leafVisibleSum + ") != visibleSize (" + visibleSize() + ")");
            }
        }

        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder("BPlusList validation failed:\n");
            for (String e : errors) {
                sb.append(" - ").append(e).append('\n');
            }
            throw new IllegalStateException(sb.toString());
        }
    }

    public boolean isValid() {
        try {
            validate();
            return true;
        } catch (IllegalStateException ex) {
            return false;
        }
    }

    private void validateNode(Node<V> node,
                              boolean isRoot,
                              int depth,
                              int[] leafDepthHolder,
                              List<String> errors) {

        if (node.leaf) {
            if (node.children != null && !node.children.isEmpty()) {
                errors.add("leaf node at depth " + depth + " has children");
            }
            if (node.values == null) {
                errors.add("leaf node at depth " + depth + " has null values list");
            } else {
                if (node.subtreeSize != node.values.size()) {
                    errors.add("leaf node at depth " + depth + " has subtreeSize = "
                            + node.subtreeSize + " but values.size() = " + node.values.size());
                }

                int vis = countVisibleInLeaf(node);
                if (node.visibleSubtreeSize != vis) {
                    errors.add("leaf node at depth " + depth + " has visibleSubtreeSize = "
                            + node.visibleSubtreeSize + " but visibleBits.cardinality = " + vis);
                }

                if (!isRoot && node.values.isEmpty()) {
                    errors.add("non-root leaf at depth " + depth + " has 0 values");
                }
                if (node.values.size() > maxValues) {
                    errors.add("leaf at depth " + depth + " has " + node.values.size()
                            + " values > maxValues (" + maxValues + ")");
                }
            }

            if (leafDepthHolder[0] == -1) {
                leafDepthHolder[0] = depth;
            } else if (leafDepthHolder[0] != depth) {
                errors.add("leaf at depth " + depth + " but previous leaf at depth " + leafDepthHolder[0]);
            }

        } else {
            if (node.values != null && !node.values.isEmpty()) {
                errors.add("internal node at depth " + depth + " has non-empty values");
            }
            if (node.children == null || node.children.isEmpty()) {
                errors.add("internal node at depth " + depth + " has no children");
            } else {
                int c = node.children.size();

                if (!isRoot) {
                    if (c < 2) {
                        errors.add("non-root internal node at depth " + depth + " has only "
                                + c + " children");
                    }
                }
                if (c > maxChildren) {
                    errors.add("internal node at depth " + depth + " has " + c
                            + " children > maxChildren (" + maxChildren + ")");
                }

                int sum = 0;
                int sumVis = 0;

                for (int i = 0; i < node.children.size(); i++) {
                    Node<V> child = node.children.get(i);
                    if (child.parent != node) {
                        errors.add("child.parent mismatch at depth " + depth + " index " + i);
                    }
                    if (child.indexInParent != i) {
                        errors.add("child.indexInParent mismatch at depth " + depth + " index " + i
                                + " (was " + child.indexInParent + ")");
                    }
                    sum += child.subtreeSize;
                    sumVis += child.visibleSubtreeSize;
                }

                if (sum != node.subtreeSize) {
                    errors.add("internal node at depth " + depth + " has subtreeSize = "
                            + node.subtreeSize + " but sum(children.subtreeSize) = " + sum);
                }
                if (sumVis != node.visibleSubtreeSize) {
                    errors.add("internal node at depth " + depth + " has visibleSubtreeSize = "
                            + node.visibleSubtreeSize + " but sum(children.visibleSubtreeSize) = " + sumVis);
                }
                if (node.visibleSubtreeSize < 0 || node.visibleSubtreeSize > node.subtreeSize) {
                    errors.add("internal node at depth " + depth + " visibleSubtreeSize out of bounds: "
                            + node.visibleSubtreeSize + " vs subtreeSize " + node.subtreeSize);
                }

                for (Node<V> child : node.children) {
                    validateNode(child, false, depth + 1, leafDepthHolder, errors);
                }
            }
        }
    }

    // -------------------------------------------------
    //  Node / Location
    // -------------------------------------------------

    private static class Node<V> {
        boolean leaf;

        int subtreeSize;              // total elements in subtree
        int visibleSubtreeSize;       // visible elements in subtree

        List<V> values;               // only if leaf
        List<Node<V>> children;       // only if internal

        Node<V> next;                 // leaf chain
        Node<V> parent;
        int indexInParent;

        BitSet visibleBits;           // only if leaf
    }

    private static class NodeLocation<V> {
        Node<V> leaf;
        int offsetInLeaf;

        NodeLocation(Node<V> leaf, int offsetInLeaf) {
            this.leaf = leaf;
            this.offsetInLeaf = offsetInLeaf;
        }
    }
}
