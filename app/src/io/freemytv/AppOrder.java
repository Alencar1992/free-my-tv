package io.freemytv;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Immutable edits of the user's shortcuts. Null uses defaults; empty is intentional. */
final class AppOrder {
    static final int MAX = 7;
    private AppOrder() {}
    static boolean valid(String pkg) { return pkg != null && pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"); }
    static List<String> visible(List<String> configured, List<String> defaults, Set<String> available) {
        List<String> out = new ArrayList<>();
        for (String pkg : configured == null ? defaults : configured) {
            if (valid(pkg) && available.contains(pkg) && !out.contains(pkg)) out.add(pkg);
            if (out.size() == MAX) break;
        }
        return out;
    }
    static List<String> add(List<String> list, String pkg) {
        if (!valid(pkg)) throw new IllegalArgumentException("Aplicativo inválido");
        List<String> out = new ArrayList<>(list);
        if (out.contains(pkg)) return out;
        if (out.size() >= MAX) throw new IllegalArgumentException("Escolha até 7 aplicativos");
        out.add(pkg); return out;
    }
    static List<String> remove(List<String> list, String pkg) {
        List<String> out = new ArrayList<>(list); out.removeAll(java.util.Collections.singleton(pkg)); return out;
    }
    static List<String> move(List<String> list, int index, int delta) {
        List<String> out = new ArrayList<>(list);
        int target = index + delta;
        if (index >= 0 && index < out.size() && target >= 0 && target < out.size()) {
            String pkg = out.remove(index); out.add(target, pkg);
        }
        return out;
    }
}
