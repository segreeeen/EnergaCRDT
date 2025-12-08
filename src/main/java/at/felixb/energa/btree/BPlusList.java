package at.felixb.energa.btree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BPlusList<V> {

    private final int t;
    private final int maxValues;    // max. Werte pro Blatt
    private final int maxChildren;  // max. Kinder pro innerem Knoten

    private Node<V> root;
    private int size;

    /**
     * Map von Value -> NodeLocation (Leaf + Offset im Leaf).
     * Damit können wir indexOf(value) in O(log n) berechnen,
     * ohne globale Index-Shifts.
     */
    private final Map<V, NodeLocation<V>> locationMap = new HashMap<>();

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
    //  Öffentliche API – List-ähnlich
    // -------------------------------------------------

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

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
     * Liefert den globalen Index eines Wertes anhand der NodeLocation.
     * O(log n) über die Baumhöhe.
     */
    public int indexOf(V value) {
        NodeLocation<V> loc = locationMap.get(value);
        if (loc == null) {
            throw new IllegalArgumentException("Value not found in BPlusList");
        }

        Node<V> node = loc.leaf;
        int index = loc.offsetInLeaf;

        // Von Leaf nach oben laufen und alle linken Subtrees aufsummieren.
        while (node.parent != null) {
            Node<V> parent = node.parent;
            int prefix = 0;

            // Kindindex + Summe der subtreeSize aller Kinder links von uns
            for (Node<V> child : parent.children) {
                if (child == node) {
                    break;
                }
                prefix += child.subtreeSize;
            }

            index += prefix;
            node = parent;
        }

        return index;
    }

    /**
     * Hängt am Ende (Index = size()) an.
     */
    public void add(V value) {
        add(size, value);
    }

    /**
     * Fügt an Position index ein (0..size).
     * Alle Elemente ab index werden nach rechts verschoben.
     * O(log n) pro Insert.
     */
    public void add(int index, V value) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException("index: " + index + ", size: " + size);
        }

        if (root == null) {
            root = newLeaf();
            root.values.add(value);
            root.subtreeSize = 1;
            size = 1;
            // Location für erstes Element
            locationMap.put(value, new NodeLocation<>(root, 0));
            return;
        }

        if (isFull(root)) {
            Node<V> newRoot = newInternal();
            newRoot.children.add(root);
            root.parent = newRoot;
            newRoot.subtreeSize = root.subtreeSize;
            splitChild(newRoot, 0);
            root = newRoot;
        }

        insertNonFull(root, index, value);
        size++;
    }

    public void addAll(List<V> values) {
        for (V v : values) {
            add(v);
        }
    }

    /**
     * ersetzt den Wert an Position index, gibt alten Wert zurück
     */
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

        // LocationMap aktualisieren: alten Eintrag raus, neuen rein
        if (old != null) {
            locationMap.remove(old);
        }
        locationMap.put(newValue, new NodeLocation<>(node, pos));

        return old;
    }

    /**
     * Alle Werte in Indexreihenfolge.
     */
    public List<V> toList() {
        List<V> result = new ArrayList<>();
        if (root == null) return result;

        Node<V> node = root;
        // zum ersten Blatt laufen
        while (!node.leaf) {
            node = node.children.get(0);
        }

        // über Blattkette iterieren
        while (node != null) {
            result.addAll(node.values);
            node = node.next;
        }

        return result;
    }

    // -------------------------------------------------
    //  Interne Insert-Logik (B+-Baum über Positionen)
    // -------------------------------------------------

    private void insertNonFull(Node<V> node, int index, V value) {
        if (node.leaf) {
            // Lokales Insert im Blatt
            node.values.add(index, value);
            node.subtreeSize++;

            // NodeLocation für neuen Wert
            locationMap.put(value, new NodeLocation<>(node, index));

            // Alle Werte rechts davon im selben Blatt: Offset ++
            for (int i = index + 1; i < node.values.size(); i++) {
                V v = node.values.get(i);
                NodeLocation<V> loc = locationMap.get(v);
                if (loc != null) {
                    loc.offsetInLeaf = i;
                }
            }
            return;
        }

        // passenden Kindknoten nach Index suchen
        int prefix = 0;
        int childIndex = 0;

        for (; childIndex < node.children.size(); childIndex++) {
            Node<V> child = node.children.get(childIndex);
            int childSize = child.subtreeSize;

            // Für Inserts: index darf == prefix + childSize sein
            // => Insert am Ende dieses Teilbaums
            if (index <= prefix + childSize) {
                index = index - prefix;
                break;
            }

            prefix += childSize;
        }

        if (childIndex == node.children.size()) {
            // Insert ganz am Ende -> letztes Kind
            childIndex = node.children.size() - 1;
            index = node.children.get(childIndex).subtreeSize;
        }

        Node<V> child = node.children.get(childIndex);

        if (isFull(child)) {
            splitChild(node, childIndex);
            // nach dem Split entscheiden, ob links oder rechts
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

        insertNonFull(child, index, value);
        node.subtreeSize++; // ein Element mehr im Teilbaum dieses Knotens
    }

    private boolean isFull(Node<V> node) {
        if (node.leaf) {
            return node.values.size() >= maxValues;
        } else {
            return node.children.size() >= maxChildren;
        }
    }

    /**
     * Split eines Kindes:
     *  - bei Blättern: Werte halbieren, Blattkette aktualisieren
     *  - bei inneren Knoten: Kinder halbieren
     *  In beiden Fällen bleibt parent.subtreeSize gleich.
     */
    private void splitChild(Node<V> parent, int childIndex) {
        Node<V> child = parent.children.get(childIndex);

        if (child.leaf) {
            Node<V> right = newLeaf();
            right.parent = parent;

            int total = child.values.size();
            int mid = total / 2; // ungefähr halb/halb

            // rechte Hälfte der Werte nach rechts
            for (int i = mid; i < total; i++) {
                right.values.add(child.values.get(i));
            }
            for (int i = total - 1; i >= mid; i--) {
                child.values.remove(i);
            }

            child.subtreeSize = child.values.size();
            right.subtreeSize = right.values.size();

            // NodeLocations für beide Blätter neu setzen
            for (int i = 0; i < child.values.size(); i++) {
                V v = child.values.get(i);
                NodeLocation<V> loc = locationMap.get(v);
                if (loc != null) {
                    loc.leaf = child;
                    loc.offsetInLeaf = i;
                }
            }
            for (int i = 0; i < right.values.size(); i++) {
                V v = right.values.get(i);
                NodeLocation<V> loc = locationMap.get(v);
                if (loc != null) {
                    loc.leaf = right;
                    loc.offsetInLeaf = i;
                }
            }

            // Blatt-Verkettung
            right.next = child.next;
            child.next = right;

            parent.children.add(childIndex + 1, right);
            // parent.subtreeSize bleibt unverändert
        } else {
            Node<V> right = newInternal();
            right.parent = parent;

            int totalChildren = child.children.size();
            int mid = totalChildren / 2;

            // rechte Hälfte der Kinder verschieben
            for (int i = mid; i < totalChildren; i++) {
                Node<V> movedChild = child.children.get(i);
                right.children.add(movedChild);
                movedChild.parent = right;
            }
            for (int i = totalChildren - 1; i >= mid; i--) {
                child.children.remove(i);
            }

            // subtreeSize der beiden Teilbäume neu berechnen
            child.subtreeSize = 0;
            for (Node<V> c : child.children) {
                child.subtreeSize += c.subtreeSize;
            }

            right.subtreeSize = 0;
            for (Node<V> c : right.children) {
                right.subtreeSize += c.subtreeSize;
            }

            parent.children.add(childIndex + 1, right);
            // parent.subtreeSize bleibt unverändert
        }
    }

    // -------------------------------------------------
    //  Hilfszeug
    // -------------------------------------------------

    private Node<V> newLeaf() {
        Node<V> n = new Node<>();
        n.leaf = true;
        n.values = new ArrayList<>();
        n.children = new ArrayList<>();
        n.subtreeSize = 0;
        n.next = null;
        n.parent = null;
        return n;
    }

    private Node<V> newInternal() {
        Node<V> n = new Node<>();
        n.leaf = false;
        n.values = null; // wird nicht benutzt
        n.children = new ArrayList<>();
        n.subtreeSize = 0;
        n.next = null;
        n.parent = null;
        return n;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index: " + index + ", size: " + size);
        }
    }

    // -------------------------------------------------
    //  VALIDIERUNG
    // -------------------------------------------------

    /**
     * Prüft die internen Invarianten des Baums.
     * Wirft IllegalStateException, wenn etwas nicht stimmt.
     */
    public void validate() {
        List<String> errors = new ArrayList<>();

        if (root == null) {
            if (size != 0) {
                errors.add("root is null but size = " + size);
            }
        } else {
            // 1) subtreeSize vs. size prüfen + Struktur rekursiv
            int[] leafDepthHolder = new int[]{-1};
            validateNode(root, true, 0, leafDepthHolder, errors);

            if (root.subtreeSize != size) {
                errors.add("root.subtreeSize (" + root.subtreeSize + ") != size (" + size + ")");
            }

            // 2) Blattkette: erste Blatt suchen, dann next-Kette durchlaufen
            Node<V> firstLeaf = root;
            while (!firstLeaf.leaf) {
                if (firstLeaf.children.isEmpty()) {
                    errors.add("internal node with no children while searching first leaf");
                    break;
                }
                firstLeaf = firstLeaf.children.get(0);
            }

            int leafCountSum = 0;
            Node<V> cur = firstLeaf;
            while (cur != null) {
                if (!cur.leaf) {
                    errors.add("non-leaf node found in leaf chain");
                    break;
                }
                leafCountSum += cur.values.size();
                cur = cur.next;
            }

            if (leafCountSum != size) {
                errors.add("leaf chain value count (" + leafCountSum + ") != size (" + size + ")");
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

    /**
     * Gibt true zurück, wenn validate() keine Ausnahme wirft.
     */
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
            // Blätter: keine Kinder, values != null, subtreeSize == values.size()
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
                // minimale Belegung: root darf weniger haben, andere Blätter in Inserts-only sollten >= 1 haben
                if (!isRoot && node.values.isEmpty()) {
                    errors.add("non-root leaf at depth " + depth + " has 0 values");
                }
                if (node.values.size() > maxValues) {
                    errors.add("leaf at depth " + depth + " has " + node.values.size()
                            + " values > maxValues (" + maxValues + ")");
                }
            }

            // alle Blätter sollten auf derselben Tiefe liegen
            if (leafDepthHolder[0] == -1) {
                leafDepthHolder[0] = depth;
            } else if (leafDepthHolder[0] != depth) {
                errors.add("leaf at depth " + depth + " but previous leaf at depth " + leafDepthHolder[0]);
            }

        } else {
            // Innere Knoten: values sollte null sein, children nicht leer
            if (node.values != null && !node.values.isEmpty()) {
                errors.add("internal node at depth " + depth + " has non-empty values");
            }
            if (node.children == null || node.children.isEmpty()) {
                errors.add("internal node at depth " + depth + " has no children");
            } else {
                int c = node.children.size();

                if (!isRoot) {
                    // Inserts-only: ein innerer Knoten sollte mindestens 2 Kinder haben
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
                for (Node<V> child : node.children) {
                    sum += child.subtreeSize;
                }
                if (sum != node.subtreeSize) {
                    errors.add("internal node at depth " + depth + " has subtreeSize = "
                            + node.subtreeSize + " but sum(children.subtreeSize) = " + sum);
                }

                for (Node<V> child : node.children) {
                    validateNode(child, false, depth + 1, leafDepthHolder, errors);
                }
            }
        }
    }

    // -------------------------------------------------
    //  Node-Typ & NodeLocation
    // -------------------------------------------------

    private static class Node<V> {
        boolean leaf;
        int subtreeSize;              // Anzahl Werte in diesem Teilbaum
        List<V> values;               // nur bei leaf == true
        List<Node<V>> children;       // nur bei leaf == false
        Node<V> next;                 // Blattverkettung
        Node<V> parent;               // für indexOf über NodeLocation
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
