package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertSame;
import org.junit.jupiter.api.Test;

class WeakIdentityMapTest {
    @Test void lookupSurvivesMutableHashAndEqualityState() {
        WeakIdentityMap<MutableKey, Object> map = new WeakIdentityMap<>();
        MutableKey key = new MutableKey(1);
        Object value = new Object();
        map.put(key, value);

        key.id = 2;

        assertSame(value, map.get(key));
    }

    @Test void distinctEqualKeysKeepSeparateValues() {
        WeakIdentityMap<MutableKey, Object> map = new WeakIdentityMap<>();
        MutableKey first = new MutableKey(7);
        MutableKey second = new MutableKey(7);
        Object firstValue = new Object();
        Object secondValue = new Object();
        map.put(first, firstValue);
        map.put(second, secondValue);

        assertSame(firstValue, map.get(first));
        assertSame(secondValue, map.get(second));
    }

    private static final class MutableKey {
        private int id;

        private MutableKey(int id) { this.id = id; }

        @Override public int hashCode() { return id; }
        @Override public boolean equals(Object other) {
            return other instanceof MutableKey key && id == key.id;
        }
    }
}
