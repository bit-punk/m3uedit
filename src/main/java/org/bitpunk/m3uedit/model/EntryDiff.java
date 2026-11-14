package org.bitpunk.m3uedit.model;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A side-by-side diff of two entry-lists, e.g. the entries before and after a filter.
 * <p>
 * Entries are compared by identity. An entry of the right list may be a changed copy of an entry of the left list
 * (e.g. enriched): then it is matched with its origin, and shown as {@link Kind#MODIFIED} if it stays in place. The entries kept in their relative order form the longest common subsequence
 * of both lists; as each entry occurs at most once, it is computed as longest increasing subsequence in
 * O(n log n), so the diff stays fast for large playlists.
 * <p>
 * Each {@link Row} shows an entry of the left list, of the right list, or both (unchanged). Between two unchanged
 * entries, the left-only and right-only entries share their rows, like in a diff-tool.
 */
@Getter
public final class EntryDiff {

    public enum Kind {
        /** in both lists, in the same relative order */
        UNCHANGED,
        /** only in the left list */
        REMOVED,
        /** in both lists, but at another position relative to the unchanged entries */
        MOVED,
        /** only in the right list */
        ADDED,
        /** in both lists, in the same relative order, but changed (a copy of the entry on the left side) */
        MODIFIED
    }

    /**
     * A row of the diff. Positions are 1-based, an empty side has no entry, a position of 0 and kind null.
     */
    public record Row(Entry left, int leftPosition, Kind leftKind, Entry right, int rightPosition, Kind rightKind) {
        public boolean isUnchanged() {
            return leftKind == Kind.UNCHANGED;
        }

        /**
         * @return true if the entry has been kept in its relative order, changed or not
         */
        public boolean isAnchor() {
            return leftKind == Kind.UNCHANGED || leftKind == Kind.MODIFIED;
        }
    }

    private final List<Row> rows;
    private final int removedCount;
    private final int movedCount;
    private final int addedCount;
    private final int unchangedCount;
    private final int modifiedCount;

    private EntryDiff(List<Row> rows) {
        this.rows = Collections.unmodifiableList(rows);
        int removed = 0;
        int moved = 0;
        int added = 0;
        int unchanged = 0;
        int modified = 0;
        for (Row row : rows) {
            if (row.leftKind() == Kind.MODIFIED) {
                modified++;
            }
            if (row.leftKind() == Kind.REMOVED) {
                removed++;
            } else if (row.leftKind() == Kind.MOVED) {
                moved++;
            } else if (row.leftKind() == Kind.UNCHANGED) {
                unchanged++;
            }
            if (row.rightKind() == Kind.ADDED) {
                added++;
            }
        }
        this.removedCount = removed;
        this.movedCount = moved;
        this.addedCount = added;
        this.unchangedCount = unchanged;
        this.modifiedCount = modified;
    }

    public static EntryDiff compute(List<Entry> left, List<Entry> right) {
        return compute(left, right, Function.identity());
    }

    /**
     * @param origin returns the entry an entry of the right list has been derived from (e.g. by an enricher),
     *               the entry itself if it has not been changed
     */
    public static EntryDiff compute(List<Entry> left, List<Entry> right, Function<Entry, Entry> origin) {
        // position of each entry in the left list, an entry occurring twice is matched once only
        Map<Entry, Integer> leftIndex = new IdentityHashMap<>();
        for (int i = 0; i < left.size(); i++) {
            leftIndex.putIfAbsent(left.get(i), i);
        }
        int[] matchOfRight = new int[right.size()];     // index in the left list, -1 if not contained
        boolean[] leftMatched = new boolean[left.size()];
        for (int j = 0; j < right.size(); j++) {
            Entry rightOrigin = origin.apply(right.get(j));
            Integer i = leftIndex.get(rightOrigin != null ? rightOrigin : right.get(j));
            if (i != null && !leftMatched[i]) {
                leftMatched[i] = true;
                matchOfRight[j] = i;
            } else {
                matchOfRight[j] = -1;
            }
        }
        boolean[] rightAnchor = longestIncreasingSubsequence(matchOfRight);
        boolean[] leftAnchor = new boolean[left.size()];
        for (int j = 0; j < right.size(); j++) {
            if (rightAnchor[j]) {
                leftAnchor[matchOfRight[j]] = true;
            }
        }

        List<Row> rows = new ArrayList<>(Math.max(left.size(), right.size()));
        int i = 0;
        int j = 0;
        while (i < left.size() || j < right.size()) {
            // collect the entries up to the next unchanged entry on each side
            int nextLeft = i;
            while (nextLeft < left.size() && !leftAnchor[nextLeft]) {
                nextLeft++;
            }
            int nextRight = j;
            while (nextRight < right.size() && !rightAnchor[nextRight]) {
                nextRight++;
            }
            int changedRows = Math.max(nextLeft - i, nextRight - j);
            for (int k = 0; k < changedRows; k++) {
                int l = i + k;
                int r = j + k;
                Entry leftEntry = l < nextLeft ? left.get(l) : null;
                Entry rightEntry = r < nextRight ? right.get(r) : null;
                Kind leftKind = leftEntry == null ? null : leftMatched[l] ? Kind.MOVED : Kind.REMOVED;
                Kind rightKind = rightEntry == null ? null : matchOfRight[r] >= 0 ? Kind.MOVED : Kind.ADDED;
                rows.add(new Row(leftEntry, leftEntry == null ? 0 : l + 1, leftKind,
                        rightEntry, rightEntry == null ? 0 : r + 1, rightKind));
            }
            if (nextLeft < left.size() && nextRight < right.size()) {
                Kind kind = left.get(nextLeft) == right.get(nextRight) ? Kind.UNCHANGED : Kind.MODIFIED;
                rows.add(new Row(left.get(nextLeft), nextLeft + 1, kind, right.get(nextRight), nextRight + 1, kind));
            }
            i = nextLeft + 1;
            j = nextRight + 1;
        }
        return new EntryDiff(rows);
    }

    /**
     * Marks the longest strictly increasing subsequence of the non-negative values (patience sorting).
     */
    private static boolean[] longestIncreasingSubsequence(int[] values) {
        int n = values.length;
        int[] tailIndex = new int[n];   // index of the smallest tail of an increasing subsequence of length k+1
        int[] predecessor = new int[n];
        int length = 0;
        for (int j = 0; j < n; j++) {
            if (values[j] < 0) {
                continue;
            }
            int low = 0;
            int high = length;
            while (low < high) {
                int mid = (low + high) >>> 1;
                if (values[tailIndex[mid]] < values[j]) {
                    low = mid + 1;
                } else {
                    high = mid;
                }
            }
            predecessor[j] = low > 0 ? tailIndex[low - 1] : -1;
            tailIndex[low] = j;
            if (low == length) {
                length++;
            }
        }
        boolean[] inSubsequence = new boolean[n];
        for (int j = length > 0 ? tailIndex[length - 1] : -1; j >= 0; j = predecessor[j]) {
            inSubsequence[j] = true;
        }
        return inSubsequence;
    }
}
