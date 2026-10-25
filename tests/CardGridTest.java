package io.freemytv;

public final class CardGridTest {
    private static int checks;
    public static void main(String[] args) {
        eq(3, CardGrid.LIMIT);
        eq(1, CardGrid.next(0, 1, 0, 3));
        eq(2, CardGrid.next(1, 1, 0, 3));
        eq(-1, CardGrid.next(2, 1, 0, 3));
        eq(-1, CardGrid.next(0, -1, 0, 3));
        eq(1, CardGrid.next(2, -1, 0, 3));
        eq(-1, CardGrid.next(1, 0, -1, 3));
        eq(-1, CardGrid.next(1, 0, 1, 3));
        eq(-1, CardGrid.next(1, 1, 0, 2));
        eq(-1, CardGrid.next(0, 1, 0, 1));
        eq(-1, CardGrid.next(0, 1, 0, 0));
        for (int i = 0; i < 3; i++) {
            yes(CardGrid.top(i) >= 256, "Card overlaps header");
            yes(CardGrid.top(i) + CardGrid.HEIGHT <= 780, "Card overlaps dock");
            yes(CardGrid.width() >= 550, "Thumbnail must remain prominent");
            yes(CardGrid.left(i) >= 80 && CardGrid.left(i) + CardGrid.width() <= 1840, "Content margins");
            yes(CardGrid.top(i) == CardGrid.top(0), "One horizontal row");
        }
        System.out.println("CardGridTest: " + checks + " assertions passed");
    }
    private static void eq(int expected, int actual) { yes(actual == expected, "Expected " + expected + ", got " + actual); }
    private static void yes(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
}
