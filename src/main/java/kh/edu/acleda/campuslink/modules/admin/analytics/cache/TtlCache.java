package kh.edu.acleda.campuslink.modules.admin.analytics.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.cache.Cache;

/** Small dependency-free in-process cache with expire-after-write semantics. */
@SuppressWarnings("unchecked")
final class TtlCache implements Cache {

    private final String name;
    private final Duration ttl;
    private final Clock clock;
    private final ConcurrentMap<Object, Entry> values = new ConcurrentHashMap<>();
    private final ConcurrentMap<Object, Object> loadLocks = new ConcurrentHashMap<>();

    TtlCache(String name, Duration ttl, Clock clock) {
        this.name = Objects.requireNonNull(name, "name");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return values;
    }

    @Override
    public ValueWrapper get(Object key) {
        Entry entry = values.get(requireKey(key));
        if (entry == null) {
            return null;
        }
        if (isExpired(entry)) {
            values.remove(key, entry);
            return null;
        }
        return () -> entry.value();
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        ValueWrapper wrapper = get(key);
        if (wrapper == null) {
            return null;
        }
        Object value = wrapper.get();
        if (type != null && !type.isInstance(value)) {
            throw new IllegalStateException("Cached value is not of required type " + type.getName());
        }
        return type == null ? (T) value : type.cast(value);
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        requireKey(key);
        Objects.requireNonNull(valueLoader, "valueLoader");

        T cached = get(key, (Class<T>) null);
        if (cached != null) {
            return cached;
        }

        Object lock = loadLocks.computeIfAbsent(key, ignored -> new Object());
        synchronized (lock) {
            cached = get(key, (Class<T>) null);
            if (cached != null) {
                return cached;
            }
            try {
                T loaded = valueLoader.call();
                if (loaded != null) {
                    put(key, loaded);
                }
                return loaded;
            } catch (Exception exception) {
                throw new ValueRetrievalException(key, valueLoader, exception);
            } finally {
                loadLocks.remove(key, lock);
            }
        }
    }

    @Override
    public void put(Object key, Object value) {
        requireKey(key);
        if (value == null) {
            evict(key);
            return;
        }
        values.put(key, new Entry(value, clock.instant().plus(ttl)));
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        requireKey(key);
        if (value == null) {
            return get(key);
        }
        while (true) {
            ValueWrapper current = get(key);
            if (current != null) {
                return current;
            }
            Entry replacement = new Entry(value, clock.instant().plus(ttl));
            Entry previous = values.putIfAbsent(key, replacement);
            if (previous == null) {
                return null;
            }
            if (!isExpired(previous)) {
                return () -> previous.value();
            }
            values.remove(key, previous);
        }
    }

    @Override
    public void evict(Object key) {
        values.remove(requireKey(key));
    }

    @Override
    public void clear() {
        values.clear();
        loadLocks.clear();
    }

    private boolean isExpired(Entry entry) {
        return !clock.instant().isBefore(entry.expiresAt());
    }

    private static Object requireKey(Object key) {
        return Objects.requireNonNull(key, "key");
    }

    private record Entry(Object value, Instant expiresAt) {
    }
}
