package com.health.utils;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class CacheUtils {

    private static final ConcurrentHashMap<String, MemoryEntry> MEMORY = new ConcurrentHashMap<>();

    public CacheUtils() {
    }

    public void set(String key, String value, long timeout, TimeUnit unit) {
        long expireAt = System.currentTimeMillis() + unit.toMillis(timeout);
        MEMORY.put(key, new MemoryEntry(value, expireAt));
    }

    public String get(String key) {
        MemoryEntry entry = MEMORY.get(key);
        if (entry == null) return null;
        if (entry.isExpired()) {
            MEMORY.remove(key);
            return null;
        }
        return entry.value;
    }

    public Boolean delete(String key) {
        return MEMORY.remove(key) != null;
    }

    public Long increment(String key) {
        MemoryEntry entry = MEMORY.get(key);
        long next = 1;
        long now = System.currentTimeMillis();
        long expireAt = 0;
        if (entry != null && !entry.isExpired()) {
            try {
                next = Long.parseLong(entry.value) + 1;
            } catch (NumberFormatException ignored) {
                next = 1;
            }
            expireAt = entry.expireAt;
        }
        MEMORY.put(key, new MemoryEntry(String.valueOf(next), expireAt == 0 ? 0 : expireAt));
        return next;
    }

    public void expire(String key, long timeout, TimeUnit unit) {
        MemoryEntry entry = MEMORY.get(key);
        if (entry == null || entry.isExpired()) {
            MEMORY.remove(key);
            return;
        }
        entry.expireAt = System.currentTimeMillis() + unit.toMillis(timeout);
    }

    private static class MemoryEntry {
        private final String value;
        private volatile long expireAt;

        private MemoryEntry(String value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }

        private boolean isExpired() {
            return expireAt > 0 && System.currentTimeMillis() > expireAt;
        }
    }
}