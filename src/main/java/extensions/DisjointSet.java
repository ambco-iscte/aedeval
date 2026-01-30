package extensions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DisjointSet<T> {

    private final Map<T, T> parent = new HashMap<>();

    private final Map<T, Integer> size = new HashMap<>();

    public void put(T item) { // Average O(1), worst O(N)
        if (!parent.containsKey(item)) {
            parent.put(item, item);
            size.put(item, 1);
        }
    }

    public Iterable<Set<T>> components() { // Average O(Nα(N)), worst O(N²α(N))
        Map<T, Set<T>> map = new HashMap<>();
        for (T item : parent.keySet()) {
            T r = root(item); // Average O(α(N)), worst O(Nα(N))
            if (!map.containsKey(r))
                map.put(r, new HashSet<>());
            map.get(r).add(r);
            map.get(r).add(item);
        }
        return map.values();
    }

    public void union(T x, T y) { // Average O(α(N)), worst O(N)
        T rp = root(x);
        T rq = root(y);
        if (rp.equals(rq)) return;
        if (size.get(rp) > size.get(rq)) {
            parent.put(rp, rq);
            size.put(rq, size.get(rq) + size.get(rp));
        } else {
            parent.put(rq, rp);
            size.put(rp, size.get(rp) + size.get(rq));
        }
    }

    public boolean connected(T x, T y) { // Average O(α(N))
        return parent.containsKey(x) && parent.containsKey(y) && root(x).equals(root(y));
    }

    private T root(T item) { // Average O(α(N)), worst O(Nα(N))
        T x = item;
        while (!x.equals(parent.get(x))) {
            parent.put(x, parent.get(parent.get(x))); // Path halving
            x = parent.get(x);
        }
        return x;
    }
}
