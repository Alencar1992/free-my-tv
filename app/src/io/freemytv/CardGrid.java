package io.freemytv;

/** Three prominent cards; horizontal edge movement is handled by VideoRail. */
final class CardGrid {
    static final int COLUMNS = 3, LIMIT = 3;
    static final float TOP = 266, HEIGHT = 460, GAP = 40;
    private CardGrid() {}

    static float width() { return (1840 - 80 - 2 * GAP) / COLUMNS; }
    static float left(int index) { return 80 + (index % COLUMNS) * (width() + GAP); }
    static float top(int index) { return TOP; }

    /** -1 means a page edge or vertical exit. */
    static int next(int index, int dx, int dy, int count) {
        int size = Math.min(LIMIT, Math.max(0, count));
        if (size == 0) return -1;
        index = Math.max(0, Math.min(size - 1, index));
        if (dy != 0) return -1;
        int target = index + dx;
        return target >= 0 && target < size ? target : -1;
    }
}
