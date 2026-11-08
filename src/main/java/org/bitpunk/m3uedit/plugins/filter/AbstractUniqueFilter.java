package org.bitpunk.m3uedit.plugins.filter;

import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.AbstractPlugin;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class AbstractUniqueFilter extends AbstractPlugin {

    /**
     * Describes the first entry with the same key as the removed one, which has been kept.
     */
    protected static String describeDuplicate(Entry removed, List<Entry> input, Function<Entry, ?> keyExtractor,
                                              String keyDescription) {
        Object key = keyExtractor.apply(removed);
        for (int i = 0; i < input.size(); i++) {
            Entry entry = input.get(i);
            if (entry == removed) {
                return null;
            }
            if (Objects.equals(keyExtractor.apply(entry), key)) {
                return "same %s as \"%s\" (#%d)".formatted(keyDescription, entry.getName(), i + 1);
            }
        }
        return null;
    }

    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Map<Object, Boolean> seen = new ConcurrentHashMap<>();
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }

}
