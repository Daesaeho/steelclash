package com.steelclash.core;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/** A map with weak keys compared by object identity and weakly held values. */
public final class WeakIdentityMap<K, V> {
    private final ReferenceQueue<K> collectedKeys = new ReferenceQueue<>();
    private final Map<IdentityReference<K>, WeakReference<V>> entries = new HashMap<>();

    public void put(K key, V value) {
        reapCollectedKeys();
        entries.put(new IdentityReference<>(key, collectedKeys), new WeakReference<>(value));
    }

    public V get(K key) {
        reapCollectedKeys();
        IdentityReference<K> lookup = new IdentityReference<>(key, null);
        WeakReference<V> valueReference = entries.get(lookup);
        if (valueReference == null) return null;
        V value = valueReference.get();
        if (value == null) entries.remove(lookup);
        return value;
    }

    @SuppressWarnings("unchecked")
    private void reapCollectedKeys() {
        IdentityReference<K> key;
        while ((key = (IdentityReference<K>) collectedKeys.poll()) != null) entries.remove(key);
    }

    private static final class IdentityReference<T> extends WeakReference<T> {
        private final int identityHash;

        IdentityReference(T referent, ReferenceQueue<T> queue) {
            super(referent, queue);
            identityHash = System.identityHashCode(referent);
        }

        @Override public int hashCode() { return identityHash; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof IdentityReference<?> reference)) return false;
            T referent = get();
            return referent != null && referent == reference.get();
        }
    }
}
