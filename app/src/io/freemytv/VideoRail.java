package io.freemytv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Stable groups of three: metadata-only history, unique videos, diverse channels. */
final class VideoRail<T extends VideoRail.Item> {
    interface Item { String id(); String channel(); }
    private final Map<String, T> known = new LinkedHashMap<>();
    private final List<String> waiting = new ArrayList<>();
    private final List<List<String>> pages = new ArrayList<>();
    private final Set<String> roundChannels = new HashSet<>();
    private int index;

    void merge(List<T> items) {
        for (T item : items) {
            if (!known.containsKey(item.id())) waiting.add(item.id());
            known.put(item.id(), item);
        }
    }

    List<T> current() { return page(index); }
    List<T> peekNext() { return page(index + 1); }
    boolean next() {
        if (peekNext().isEmpty()) return false;
        index++;
        return true;
    }
    boolean previous() {
        if (index == 0) return false;
        index--;
        return true;
    }
    void rewind() { index = 0; }
    int start() {
        int count = 0;
        for (int i = 0; i < index; i++) count += pages.get(i).size();
        return count;
    }
    void clear() {
        known.clear(); waiting.clear(); pages.clear(); roundChannels.clear(); index = 0;
    }

    private List<T> page(int target) {
        while (pages.size() <= target) {
            List<String> group = new ArrayList<>();
            Set<String> channels = new HashSet<>();
            while (group.size() < 3) {
                int chosen = find(channels, true);
                if (chosen < 0) {
                    chosen = find(channels, false);
                    if (chosen < 0) break;
                    roundChannels.clear();
                }
                String id = waiting.remove(chosen);
                group.add(id);
                String channel = known.get(id).channel();
                channels.add(channel);
                roundChannels.add(channel);
            }
            if (group.isEmpty()) break;
            pages.add(group);
        }
        if (target >= pages.size()) return Collections.emptyList();
        List<T> result = new ArrayList<>();
        for (String id : pages.get(target)) result.add(known.get(id));
        return Collections.unmodifiableList(result);
    }

    private int find(Set<String> group, boolean freshChannel) {
        for (int i = 0; i < waiting.size(); i++) {
            String channel = known.get(waiting.get(i)).channel();
            if (!group.contains(channel) && (!freshChannel || !roundChannels.contains(channel))) return i;
        }
        return -1;
    }
}
