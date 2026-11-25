package org.bitpunk.m3uedit.plugins.filter;

import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.api.Filter;
import org.bitpunk.m3uedit.api.PluginContext;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The base of the filters removing entries with the same key as an entry before them.
 */
public abstract class AbstractUniqueFilter implements Filter {
    private PluginContext context;

    @Override
    public void init(PluginContext context) {
        this.context = context;
    }

    /**
     * @return the context passed to {@link #init(PluginContext)}
     */
    protected PluginContext getContext() {
        return context;
    }

    /**
     * Describes the first entry with the same key as the removed one, which has been kept.
     *
     * @param reasonKey the key of the text of the reason, with the name ({0}) and the position ({1}) of the entry kept
     */
    protected String describeDuplicate(Entry removed, List<Entry> input, Function<Entry, ?> keyExtractor,
                                       String reasonKey) {
        Object key = keyExtractor.apply(removed);
        for (int i = 0; i < input.size(); i++) {
            Entry entry = input.get(i);
            if (entry == removed) {
                return null;
            }
            if (Objects.equals(keyExtractor.apply(entry), key)) {
                return context.text(reasonKey, entry.getName(), i + 1);
            }
        }
        return null;
    }

    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Map<Object, Boolean> seen = new ConcurrentHashMap<>();
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }

    /**
     * Plugins are compared by identity, only a short description is returned.
     */
    @Override
    public String toString() {
        return "%s[%s]".formatted(getClass().getSimpleName(), getName());
    }
}
