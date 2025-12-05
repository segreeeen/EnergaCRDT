package at.felixb.energa;

import java.security.Provider;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class LinearOrderCache {

    private final List<CrdtNode> cache = new ArrayList<>();
    private final CrdtDocument document;
    private boolean dirty = true;
    private Map<CrdtNodeId, Integer> nodeIdIndexMap = new HashMap<>();

    public LinearOrderCache(CrdtDocument document) {
        super();
        this.document = document;

        document.registerDocumentChangedListener(() -> {
            this.dirty = true;
        });
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
