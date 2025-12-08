package at.felixb.energa.btree;

import java.util.ArrayList;
import java.util.List;

public class BTree<K extends Comparable<K>, V> {

    private final int minKeys;
    private final int maxKeys;
    private final int maxChildren;
    private BTreeNode<K, V> root;

    public BTree(int t) {
        if (t < 2) {
            throw new IllegalArgumentException("t must be >= 2");
        }
        this.maxKeys = 2 * t - 1;
        this.minKeys = t - 1;
        this.maxChildren = 2 * t;
        this.root = null;
    }

    // -------------------------------------------------
    //   B+ Tree: put / get (Key-basiert)
    // -------------------------------------------------

    public void put(K key, V value) {
        if (root == null) {
            BTreeNode<K, V> node = new BTreeNode<>(true);
            node.keys.add(key);
            node.values.add(value);
            root = node;
            return;
        }

        if (isFull(root)) {
            BTreeNode<K, V> newRoot = new BTreeNode<>(false);
            newRoot.children.add(root);
            splitChild(newRoot, 0);
            root = newRoot;
        }

        insertNonFull(root, key, value);
    }

    public V get(K key) {
        if (root == null) return null;

        BTreeNode<K, V> node = root;

        // bis zum Blatt herunterlaufen
        while (!node.leaf) {
            int i = 0;
            while (i < node.keys.size() && key.compareTo(node.keys.get(i)) >= 0) {
                i++;
            }
            node = node.children.get(i);
        }

        // im Blatt linear suchen
        for (int i = 0; i < node.keys.size(); i++) {
            int cmp = key.compareTo(node.keys.get(i));
            if (cmp == 0) {
                return node.values.get(i);
            }
            if (cmp < 0) {
                break;
            }
        }

        return null;
    }

    private boolean isFull(BTreeNode<K, V> node) {
        return node.keys.size() == maxKeys;
    }

    private void insertNonFull(BTreeNode<K, V> node, K key, V value) {
        if (node.leaf) {
            insertInLeaf(node, key, value);
        } else {
            insertInInternal(node, key, value);
        }
    }

    // Werte werden NUR in Blättern gespeichert
    private void insertInLeaf(BTreeNode<K, V> node, K key, V value) {
        for (int i = 0; i < node.keys.size(); i++) {
            K existingKey = node.keys.get(i);
            int compareValue = key.compareTo(existingKey);
            if (compareValue == 0) {
                // Key existiert bereits im Blatt: Value überschreiben
                node.values.set(i, value);
                return;
            }

            if (compareValue < 0) {
                node.keys.add(i, key);
                node.values.add(i, value);
                return;
            }
        }

        node.keys.add(key);
        node.values.add(value);
    }

    // Innere Knoten haben KEINE Values
    private void insertInInternal(BTreeNode<K, V> node, K key, V value) {
        int childIndex = 0;

        // passendes Kind finden: bei == gehen wir nach rechts
        while (childIndex < node.keys.size()
                && key.compareTo(node.keys.get(childIndex)) >= 0) {
            childIndex++;
        }

        BTreeNode<K, V> child = node.children.get(childIndex);

        if (isFull(child)) {
            splitChild(node, childIndex);
            // nach Split entscheiden, ob wir rechts weitergehen
            if (key.compareTo(node.keys.get(childIndex)) >= 0) {
                childIndex++;
            }
            child = node.children.get(childIndex);
        }

        insertNonFull(child, key, value);
    }

    private void splitChild(BTreeNode<K, V> parent, int index) {
        BTreeNode<K, V> child = parent.children.get(index);

        if (child.leaf) {
            splitLeafChild(parent, index, child);
        } else {
            splitInternalChild(parent, index, child);
        }
    }

    /**
     * B+-Tree-Blattsplit:
     *  - Keys und Values werden halbiert
     *  - Blätter werden über next verkettet
     *  - Separator-Key ist der erste Key des rechten Blattes und wird in den Parent kopiert
     */
    private void splitLeafChild(BTreeNode<K, V> parent, int index, BTreeNode<K, V> child) {
        BTreeNode<K, V> rightChild = new BTreeNode<>(true);

        int totalKeys = child.keys.size();
        int from = (totalKeys + 1) / 2; // rechte Hälfte beginnt hier

        // rechte Hälfte der Keys/Values in das neue Blatt verschieben
        for (int i = from; i < totalKeys; i++) {
            rightChild.keys.add(child.keys.get(i));
            rightChild.values.add(child.values.get(i));
        }

        // linkes Blatt einkürzen
        for (int i = totalKeys - 1; i >= from; i--) {
            child.keys.remove(i);
            child.values.remove(i);
        }

        // Blatt-Verkettung
        rightChild.next = child.next;
        child.next = rightChild;

        // Separator-Key (erster Key im rechten Blatt)
        K splitKey = rightChild.keys.get(0);

        // in den Parent einfügen: nur Key + Child, keine Values im inneren Knoten
        parent.keys.add(index, splitKey);
        parent.children.add(index + 1, rightChild);
    }

    /**
     * Split eines inneren Knotens:
     *  - klassischer B-Tree-Split, aber ohne Values in Parent/inneren Knoten
     */
    private void splitInternalChild(BTreeNode<K, V> parent, int index, BTreeNode<K, V> child) {
        BTreeNode<K, V> rightChild = new BTreeNode<>(false);

        int totalKeys = child.keys.size();
        int mid = totalKeys / 2;

        // Separator-Key
        K midKey = child.keys.get(mid);

        // Keys rechts vom Median in den rechten Knoten verschieben
        for (int i = mid + 1; i < totalKeys; i++) {
            rightChild.keys.add(child.keys.get(i));
        }

        // linke Seite einkürzen (Median inklusive entfernen)
        for (int i = child.keys.size() - 1; i >= mid; i--) {
            child.keys.remove(i);
        }

        // Children entsprechend splitten:
        int totalChildren = child.children.size();
        for (int i = mid + 1; i < totalChildren; i++) {
            rightChild.children.add(child.children.get(i));
        }
        for (int i = child.children.size() - 1; i >= mid + 1; i--) {
            child.children.remove(i);
        }

        // Separator-Key in den Parent einfügen
        parent.keys.add(index, midKey);
        parent.children.add(index + 1, rightChild);
    }

    // -------------------------------------------------
    //   Werte in Key-Reihenfolge als Liste
    // -------------------------------------------------

    public List<V> toList() {
        List<V> result = new ArrayList<>();
        if (root == null) return result;

        BTreeNode<K, V> node = root;
        // zum ersten Blatt
        while (!node.leaf) {
            node = node.children.get(0);
        }

        // über Blattkette laufen
        while (node != null) {
            result.addAll(node.values);
            node = node.next;
        }

        return result;
    }

    // -------------------------------------------------
    //   Helfer für BPlusList: Keys >= startIndex um +1
    //   (nur sinnvoll, wenn K = Integer)
    // -------------------------------------------------

    public void shiftKeysFrom(int startIndex) {
        if (root == null) return;
        shiftKeysFromNode(root, startIndex);
    }

    private void shiftKeysFromNode(BTreeNode<K, V> node, int startIndex) {
        // Keys in diesem Knoten anpassen
        for (int i = 0; i < node.keys.size(); i++) {
            int intKey = toIntKey(node.keys.get(i));
            if (intKey >= startIndex) {
                node.keys.set(i, fromIntKey(intKey + 1));
            }
        }

        // Kinder rekursiv bearbeiten
        if (!node.leaf) {
            for (BTreeNode<K, V> child : node.children) {
                shiftKeysFromNode(child, startIndex);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private int toIntKey(K key) {
        if (!(key instanceof Integer)) {
            throw new IllegalStateException("shiftKeysFrom() erfordert K = Integer");
        }
        return (Integer) key;
    }

    @SuppressWarnings("unchecked")
    private K fromIntKey(int index) {
        return (K) Integer.valueOf(index);
    }

    // -------------------------------------------------
    //   Node-Klasse
    // -------------------------------------------------

    public static class BTreeNode<K, V> {
        private final List<BTreeNode<K, V>> children;
        private final List<K> keys;
        private final List<V> values;      // nur in Blättern verwendet
        private boolean leaf;
        private BTreeNode<K, V> next;      // Verkettung der Blätter

        public BTreeNode(boolean leaf) {
            this.keys = new ArrayList<>();
            this.values = new ArrayList<>();
            this.children = new ArrayList<>();
            this.leaf = leaf;
            this.next = null;
        }
    }
}
