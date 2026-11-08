package io.freemytv;

/** Presentation catalog. IDs are persisted; data, navigation and TV mode never depend on them. */
enum Skin {
    VIKTORKAV("viktorkav", "ViktorKav", "Grafite · ciano · identidade do canal", "Graphite · cyan · channel identity", false, 4, 4, .07f,
            new int[] {TvTheme.BACKGROUND, TvTheme.FIELD, TvTheme.SURFACE, TvTheme.RAISED,
                    TvTheme.LINE, TvTheme.TEXT, TvTheme.SECONDARY, TvTheme.MUTED,
                    TvTheme.BRAND, TvTheme.FOCUS, TvTheme.FOCUS_FIELD, TvTheme.ON_FOCUS}, null),
    LIQUID_GLASS("liquid-glass", "Apple Liquid Glass", "Vidro · claro de dia, escuro à noite", "Glass · light by day, dark at night", true, 22, 18, .16f,
            new int[] {0xFFF0F1F5, 0xFFE0E2E8, 0xFFF5F5F7, 0xFFFFFFFF,
                    0xFFC8CBD2, 0xFF1C1C1E, 0xFF45454C, 0xFF63636B,
                    0xFF1C1C1E, 0xFFFFFFFF, 0xFFF5F5F7, 0xFF1C1C1E},
            new int[] {0xFF101115, 0xFF17181D, 0xFF24252B, 0xFF36373E,
                    0xFF55565F, 0xFFF5F5F7, 0xFFDDDEE4, 0xFFB8BBC4,
                    0xFFF5F5F7, 0xFFFFFFFF, 0xFFF5F5F7, 0xFF1C1C1E}),
    GTA_VI("gta-vi", "GTA VI", "Leonida · pôr do sol · galeria offline", "Leonida · sunset · offline gallery", false, 18, 18, .07f,
            new int[] {0xFF261D3D, 0xFF21192F, 0xFF302541, 0xFF423452,
                    0xFF8E739D, 0xFFFFFFFF, 0xFFF1DFF0, 0xFFD7BEDB,
                    0xFFE7A5CA, 0xFFFFD6A3, 0xFF4A3658, 0xFF261D3D}, null);

    final String id, label, descriptionPt, descriptionEn;
    final boolean glass;
    final float tileRadius, radius, focusGrow;
    private final int[] day, night;

    Skin(String id, String label, String descriptionPt, String descriptionEn, boolean glass, float tileRadius, float radius, float focusGrow, int[] day, int[] night) {
        this.id = id;
        this.label = label;
        this.descriptionPt = descriptionPt;
        this.descriptionEn = descriptionEn;
        this.glass = glass;
        this.tileRadius = tileRadius;
        this.radius = radius;
        this.focusGrow = focusGrow;
        this.day = day;
        this.night = night == null ? day : night;
    }

    static Skin fromId(String id) {
        for (Skin skin : values()) if (skin.id.equals(id)) return skin;
        return VIKTORKAV;
    }

    boolean isNight(boolean localNight) { return !glass || localNight; }

    /** Updates a view-owned palette without allocating during a day/night animation. */
    void apply(Palette out, float darkness) {
        float t = Math.max(0, Math.min(1, darkness));
        out.BACKGROUND = color(0, t); out.FIELD = color(1, t);
        out.SURFACE = color(2, t); out.RAISED = color(3, t); out.LINE = color(4, t);
        out.TEXT = color(5, t); out.SECONDARY = color(6, t); out.MUTED = color(7, t);
        out.BRAND = color(8, t); out.FOCUS = color(9, t);
        out.FOCUS_FIELD = color(10, t); out.ON_FOCUS = color(11, t);
        out.ACCENT = glass ? out.TEXT : out.FOCUS;
    }

    private int color(int role, float t) {
        int a = day[role], b = night[role], result = 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int x = (a >> shift) & 255, y = (b >> shift) & 255;
            result |= Math.round(x + (y - x) * t) << shift;
        }
        return result;
    }

    static final class Palette {
        int BACKGROUND, FIELD, SURFACE, RAISED, LINE, TEXT, SECONDARY, MUTED,
                BRAND, FOCUS, FOCUS_FIELD, ON_FOCUS, ACCENT;

        int appLabel(float focus) {
            float t = Math.max(0, Math.min(1, focus));
            int result = 0xFF000000;
            for (int shift = 0; shift <= 16; shift += 8) {
                int a = (SECONDARY >> shift) & 255, b = (TEXT >> shift) & 255;
                result |= Math.round(a + (b - a) * t) << shift;
            }
            return result;
        }
    }
}
