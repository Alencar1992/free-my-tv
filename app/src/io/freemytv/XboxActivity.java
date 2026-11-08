package io.freemytv;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Optional private Xbox history picker. Credentials only enter through the masked field. */
public final class XboxActivity extends Activity {
    private static final int PAGE_SIZE = 8;
    private final Skin.Palette palette = new Skin.Palette();
    private final GameRow[] rows = new GameRow[PAGE_SIZE];
    private final Runnable sourceChanged = this::updateSnapshot;
    private final Runnable artChanged = this::updateArtwork;
    private Skin skin;
    private Ui.Fonts fonts;
    private XboxSource source;
    private XboxArt art;
    private XboxSource.Snapshot snapshot;
    private TextView status, historyCount, empty, pageLabel, detailName, detailStatus;
    private Button connectionToggle, disconnect, refresh, previous, next;
    private EditText keyInput, searchInput;
    private LinearLayout connectionBox, metrics;
    private ImageView detailCover;
    private ScrollView gameScroll;
    private List<XboxData.Game> filtered = Collections.emptyList();
    private List<XboxData.Game> visible = Collections.emptyList();
    private int page;
    private boolean active, setupOpen;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        skin = SkinPreferences.load(this);
        fonts = Ui.fonts(this, skin);
        skin.apply(palette, skin.isNight(HomeData.get(this).isNight(System.currentTimeMillis())) ? 1 : 0);
        source = XboxSource.get(this);
        art = XboxArt.get(this);
        snapshot = source.snapshot();
        setupOpen = !snapshot.configured;
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        buildPage();
        updateSnapshot();
    }

    @Override protected void onResume() {
        super.onResume();
        active = true;
        source.addListener(sourceChanged);
        art.addListener(artChanged);
        updateSnapshot();
        if (snapshot.configured) source.refresh(false);
    }

    @Override protected void onPause() {
        active = false;
        source.removeListener(sourceChanged);
        art.removeListener(artChanged);
        art.prefetch(Collections.emptyList());
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (keyInput != null) keyInput.setText("");
        super.onDestroy();
    }

    private void buildPage() {
        LinearLayout outer = column();
        outer.setPadding(dp(30), dp(15), dp(30), dp(12));
        outer.setBackgroundColor(palette.BACKGROUND);
        outer.addView(text("Xbox", 28, fonts.displaySemibold, palette.TEXT));
        TextView hint = text(Ui.tr("Setas para navegar · OK para escolher · Voltar para sair",
                "Arrows to navigate · OK to choose · Back to leave"), 15, fonts.regular, palette.SECONDARY);
        hint.setPadding(0, 0, 0, dp(5));
        outer.addView(hint);
        status = text("", 16, fonts.medium, palette.SECONDARY);
        status.setPadding(0, 0, 0, dp(7));
        outer.addView(status);

        LinearLayout actions = row();
        connectionToggle = button("", () -> {
            setupOpen = !setupOpen;
            updateConnection();
            if (setupOpen) keyInput.requestFocus();
        });
        addAction(actions, connectionToggle, 1);
        disconnect = button(Ui.tr("Desconectar e remover chave", "Disconnect and remove key"), () -> {
            keyInput.setText("");
            hideKeyboard();
            source.disconnect();
            setupOpen = true;
            updateSnapshot();
            connectionToggle.requestFocus();
        });
        addAction(actions, disconnect, 1.25f);
        refresh = button(Ui.tr("Atualizar agora", "Refresh now"), () -> source.refresh(true));
        addAction(actions, refresh, .8f);
        addAction(actions, button(Ui.tr("Voltar", "Back"), this::finish), .5f);
        outer.addView(actions);

        connectionBox = column();
        connectionBox.setPadding(dp(10), dp(3), dp(10), dp(7));
        connectionBox.setBackgroundColor(palette.SURFACE);
        TextView privacy = text(Ui.tr(
                "Conexão opcional OpenXBL. Use sua chave somente em uma TV local e privada: ela dá acesso aos dados da conta. A chave será guardada criptografada após a validação. Não a compartilhe nem a envie por ADB.",
                "Optional OpenXBL connection. Use your key only on a local, private TV: it grants access to account data. The key is stored encrypted after validation. Do not share it or send it through ADB."),
                16, fonts.regular, palette.SECONDARY);
        privacy.setPadding(0, 0, 0, dp(3));
        connectionBox.addView(privacy);
        LinearLayout entry = row();
        keyInput = edit(Ui.tr("Chave pessoal OpenXBL", "Personal OpenXBL key"));
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        keyInput.setTransformationMethod(PasswordTransformationMethod.getInstance());
        keyInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2048)});
        // Never put the credential in view-state, autofill, an Intent or a second persistent store.
        keyInput.setSaveEnabled(false);
        keyInput.setSaveFromParentEnabled(false);
        keyInput.setFreezesText(false);
        keyInput.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        keyInput.setPrivateImeOptions("nm");
        entry.addView(keyInput, new LinearLayout.LayoutParams(0, dp(54), 1));
        Button connect = button(Ui.tr("Conectar", "Connect"), this::connect);
        LinearLayout.LayoutParams connectParams = new LinearLayout.LayoutParams(dp(150), dp(54));
        connectParams.leftMargin = dp(8);
        entry.addView(connect, connectParams);
        connectionBox.addView(entry);
        outer.addView(connectionBox);

        LinearLayout body = row();
        body.setPadding(0, dp(10), 0, 0);
        LinearLayout history = column();
        historyCount = text("", 20, fonts.displaySemibold, palette.TEXT);
        history.addView(historyCount);
        TextView scope = text(Ui.tr("Histórico retornado pelo Xbox · até 1.000 jogos. Compras nunca jogadas podem não aparecer.",
                "History returned by Xbox · up to 1,000 games. Purchases never played may be absent."),
                14, fonts.regular, palette.SECONDARY);
        scope.setPadding(0, dp(2), 0, dp(6));
        history.addView(scope);
        searchInput = edit(Ui.tr("Buscar no histórico", "Search history"));
        searchInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(120)});
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        history.addView(searchInput, new LinearLayout.LayoutParams(-1, dp(52)));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                page = 0;
                updateGames();
                gameScroll.scrollTo(0, 0);
            }
            @Override public void afterTextChanged(Editable value) {}
        });
        gameScroll = new ScrollView(this);
        gameScroll.setFillViewport(false);
        LinearLayout gameList = column();
        gameList.setPadding(dp(3), dp(7), dp(3), dp(5));
        empty = text("", 17, fonts.regular, palette.SECONDARY);
        empty.setPadding(dp(5), dp(10), dp(5), dp(15));
        gameList.addView(empty);
        for (int i = 0; i < PAGE_SIZE; i++) {
            rows[i] = new GameRow();
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = dp(7);
            gameList.addView(rows[i].view, params);
        }
        gameScroll.addView(gameList);
        history.addView(gameScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout pages = row();
        previous = button(Ui.tr("‹ Anterior", "‹ Previous"), () -> changePage(-1));
        next = button(Ui.tr("Próxima ›", "Next ›"), () -> changePage(1));
        addAction(pages, previous, 1);
        pageLabel = text("", 15, fonts.medium, palette.SECONDARY);
        pageLabel.setGravity(Gravity.CENTER);
        pages.addView(pageLabel, new LinearLayout.LayoutParams(0, -1, .9f));
        addAction(pages, next, 1);
        history.addView(pages);
        body.addView(history, new LinearLayout.LayoutParams(0, -1, 1.05f));

        ScrollView detailScroll = new ScrollView(this);
        detailScroll.setFillViewport(false);
        LinearLayout details = column();
        details.setPadding(dp(18), 0, dp(5), dp(12));
        TextView selected = text(Ui.tr("Jogo na home", "Game on Home"), 20, fonts.displaySemibold, palette.TEXT);
        details.addView(selected);
        LinearLayout selectedHeading = row();
        selectedHeading.setPadding(0, dp(8), 0, dp(8));
        detailCover = image();
        selectedHeading.addView(detailCover, new LinearLayout.LayoutParams(dp(78), dp(104)));
        detailName = text("", 22, fonts.displaySemibold, palette.TEXT);
        detailName.setGravity(Gravity.CENTER_VERTICAL);
        detailName.setPadding(dp(12), 0, 0, 0);
        selectedHeading.addView(detailName, new LinearLayout.LayoutParams(0, -1, 1));
        details.addView(selectedHeading);
        detailStatus = text("", 15, fonts.regular, palette.SECONDARY);
        detailStatus.setPadding(0, 0, 0, dp(8));
        details.addView(detailStatus);
        metrics = column();
        details.addView(metrics);
        TextView explanation = text(Ui.tr("📊 Progresso de conquistas; não representa a campanha. “Indisponível” significa que a fonte não informou o dado; 0 é um valor informado.",
                "📊 Achievement progress; it does not represent the campaign. “Unavailable” means the source did not report a value; 0 is a reported value."),
                15, fonts.regular, palette.SECONDARY);
        explanation.setPadding(0, dp(10), 0, dp(6));
        // Default typeface lets Android render emoji even with a skin's custom font.
        explanation.setTypeface(Typeface.DEFAULT);
        details.addView(explanation);
        detailScroll.addView(details);
        body.addView(detailScroll, new LinearLayout.LayoutParams(0, -1, 1));
        outer.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(outer);
        connectionToggle.requestFocus();
    }

    private void connect() {
        String key = keyInput.getText().toString().trim();
        keyInput.setText("");
        if (key.isEmpty()) {
            keyInput.setError(Ui.tr("Digite sua chave pessoal para conectar.", "Enter your personal key to connect."));
            keyInput.requestFocus();
            return;
        }
        keyInput.setError(null);
        hideKeyboard();
        connectionToggle.requestFocus();
        source.connect(key);
    }

    private void updateSnapshot() {
        XboxSource.Snapshot nextSnapshot = source.snapshot();
        XboxSource.Snapshot previousSnapshot = snapshot;
        snapshot = nextSnapshot;
        if (snapshot.configured && previousSnapshot != null && !previousSnapshot.configured) setupOpen = false;
        updateConnection();
        String account = snapshot.configured
                ? Ui.tr("Conectado", "Connected") + (snapshot.profileName.isEmpty() ? "" : " · " + snapshot.profileName)
                : Ui.tr("Xbox opcional · sem conexão", "Optional Xbox · disconnected");
        String message = snapshot.message == null ? "" : snapshot.message.trim();
        status.setText(account + (snapshot.loading ? Ui.tr(" · carregando…", " · loading…") : "")
                + (message.isEmpty() ? "" : "\n" + message));
        updateGames();
        updateDetail();
    }

    /** Source notifications do not recreate the connection/search fields or request focus. */
    private void updateConnection() {
        connectionBox.setVisibility(setupOpen ? View.VISIBLE : View.GONE);
        connectionToggle.setText(setupOpen ? Ui.tr("Ocultar conexão", "Hide connection")
                : snapshot.configured ? Ui.tr("Trocar chave", "Change key") : Ui.tr("Conectar Xbox", "Connect Xbox"));
        disconnect.setEnabled(snapshot.configured || snapshot.loading);
        refresh.setEnabled(snapshot.configured && !snapshot.loading);
    }

    private void updateGames() {
        filtered = XboxData.search(snapshot.games, searchInput.getText().toString());
        int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));
        historyCount.setText(Ui.tr("Jogos com histórico", "Games with history") + " · " + snapshot.games.size());
        empty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(!snapshot.configured ? Ui.tr("Conecte opcionalmente sua conta para consultar o histórico.", "Optionally connect your account to view history.")
                : snapshot.loading && snapshot.games.isEmpty() ? Ui.tr("Carregando histórico…", "Loading history…")
                : snapshot.games.isEmpty() ? Ui.tr("Nenhum jogo retornado no histórico disponível.", "No games returned in the available history.")
                : Ui.tr("Nenhum jogo corresponde à busca.", "No games match your search."));
        int start = page * PAGE_SIZE, end = Math.min(start + PAGE_SIZE, filtered.size());
        visible = new ArrayList<>(filtered.subList(start, end));
        for (int i = 0; i < PAGE_SIZE; i++) rows[i].bind(i < visible.size() ? visible.get(i) : null);
        previous.setEnabled(page > 0);
        next.setEnabled(page + 1 < pages);
        pageLabel.setText((page + 1) + "/" + pages + " · " + filtered.size());
        updateArtWindow();
    }

    private void changePage(int delta) {
        page += delta;
        updateGames();
        gameScroll.scrollTo(0, 0);
        if (!visible.isEmpty()) rows[0].view.requestFocus();
    }

    private void updateDetail() {
        XboxData.Game game = snapshot.selected();
        detailName.setText(game == null ? Ui.tr("Escolha um jogo no histórico", "Choose a game from history") : game.name);
        detailCover.setVisibility(game == null ? View.GONE : View.VISIBLE);
        detailCover.setContentDescription(game == null ? "" : Ui.tr("Capa de ", "Cover for ") + game.name);
        XboxData.Summary summary = snapshot.summary;
        if (game == null || summary != null && !game.id.equals(summary.game.id)) summary = null;
        String age = snapshot.updatedAt > 0
                ? Ui.tr("Dados de ", "Data from ") + new SimpleDateFormat(Ui.en ? "MMM d, HH:mm" : "dd/MM, HH:mm",
                        Ui.en ? Locale.US : Ui.PT).format(new Date(snapshot.updatedAt))
                    + (snapshot.stale ? Ui.tr(" · em cache / desatualizados", " · cached / out of date") : "")
                : Ui.tr("Dados detalhados ainda indisponíveis", "Detailed data not available yet");
        if (summary != null && summary.partial) age += Ui.tr("\nDados parciais: a fonte não retornou todos os detalhes.",
                "\nPartial data: the source did not return all details.");
        detailStatus.setText(game == null ? Ui.tr("Selecionar um jogo atualiza o painel da home.", "Selecting a game updates the Home panel.") : age);
        // Detail text is non-focusable; changing it never interrupts a key/search editor.
        metrics.removeAllViews();
        if (game == null) { updateArtwork(); return; }
        int unlocked = summary == null ? game.unlocked : summary.unlocked;
        int total = summary == null ? game.total : summary.total;
        int score = summary == null ? game.earnedScore : summary.earnedScore;
        int totalScore = summary == null ? game.totalScore : summary.totalScore;
        metric("🏆", Ui.tr("Conquistas", "Achievements"), count(unlocked) + " / " + count(total));
        metric("⏱️", Ui.tr("Tempo jogado", "Time played"), summary == null ? unavailable() : available(summary.time()));
        metric("📊", Ui.tr("Progresso de conquistas", "Achievement progress"), summary == null ? unavailable() : available(summary.progress()));
        if (score >= 0 || totalScore >= 0) metric("🏅", "Gamerscore", count(score) + " / " + count(totalScore));
        if (summary != null) for (Map.Entry<String, String> stat : summary.stats.entrySet()) {
            String key = stat.getKey();
            if (isPlaytime(key)) continue; // The formatted playtime above already represents this statistic.
            metric(statEmoji(key), XboxData.metricLabel(key).replaceFirst("^[^\\p{L}\\p{N}]+", ""), available(stat.getValue()));
        }
        updateArtwork();
    }

    private void updateArtWindow() {
        if (!active) return;
        List<XboxData.Game> window = new ArrayList<>(visible);
        XboxData.Game selected = snapshot.selected();
        if (selected != null) {
            boolean present = false;
            for (XboxData.Game game : window) if (game.id.equals(selected.id)) present = true;
            if (!present) window.add(selected);
        }
        art.prefetch(window);
        updateArtwork();
    }

    /** Artwork callbacks only touch ImageViews: no view tree replacement or focus/IME changes. */
    private void updateArtwork() {
        for (GameRow row : rows) if (row != null && row.game != null) row.cover.setImageBitmap(art.get(row.game));
        if (detailCover != null) {
            XboxData.Game selected = snapshot.selected();
            detailCover.setImageBitmap(selected == null ? null : art.get(selected));
        }
    }

    private final class GameRow {
        final LinearLayout view = row();
        final ImageView cover = image();
        final TextView name = text("", 19, fonts.medium, palette.TEXT);
        XboxData.Game game;
        GameRow() {
            view.setPadding(dp(10), dp(7), dp(10), dp(7));
            view.setMinimumHeight(dp(78));
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setBackground(controlBackground());
            view.setFocusable(true);
            view.setClickable(true);
            view.addView(cover, new LinearLayout.LayoutParams(dp(44), dp(60)));
            name.setPadding(dp(11), 0, 0, 0);
            view.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            view.setOnClickListener(v -> {
                if (game != null) { hideKeyboard(); source.select(game.id); }
            });
        }
        void bind(XboxData.Game value) {
            game = value;
            view.setVisibility(value == null ? View.GONE : View.VISIBLE);
            if (value == null) { cover.setImageBitmap(null); return; }
            boolean selected = value.id.equals(snapshot.selectedId);
            name.setText((selected ? "✓ " : "") + value.name);
            view.setContentDescription(value.name + (selected ? Ui.tr(" · selecionado na home", " · selected on Home") : Ui.tr(" · selecionar na home", " · select on Home")));
            cover.setContentDescription(null);
            cover.setImageBitmap(art.get(value));
        }
    }

    private void metric(String emoji, String label, String value) {
        LinearLayout line = row();
        line.setGravity(Gravity.TOP);
        line.setPadding(0, dp(5), 0, dp(7));
        TextView symbol = text(emoji, 23, Typeface.DEFAULT, palette.TEXT);
        symbol.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        line.addView(symbol, new LinearLayout.LayoutParams(dp(36), -2));
        TextView description = text(label + "\n" + value, 19, fonts.regular, palette.TEXT);
        line.addView(description, new LinearLayout.LayoutParams(0, -2, 1));
        metrics.addView(line);
    }

    private static boolean isPlaytime(String key) {
        String compact = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return compact.equals("minutesplayed");
    }

    private static String statEmoji(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.contains("headshot")) return "🎯";
        if (lower.contains("loot")) return "🎒";
        if (lower.contains("poker")) return "♠️";
        if (lower.contains("kill")) return "⚔️";
        if (lower.contains("win")) return "🏅";
        if (lower.contains("distance")) return "📍";
        return "📌";
    }

    private String count(int value) { return value < 0 ? "—" : Integer.toString(value); }
    private String unavailable() { return Ui.tr("Indisponível", "Unavailable"); }
    private String available(String value) { return value == null || value.trim().isEmpty() || value.equals("—") ? unavailable() : value; }
    private int dp(float size) { return Math.round(size * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); return box; }
    private LinearLayout row() { LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.HORIZONTAL); return box; }
    private TextView text(String value, float size, Typeface face, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTypeface(face); view.setTextColor(color);
        return view;
    }
    private EditText edit(String hint) {
        EditText view = new EditText(this);
        view.setHint(hint); view.setTextSize(19); view.setTypeface(fonts.regular);
        view.setSingleLine(true); view.setTextColor(palette.TEXT); view.setHintTextColor(palette.SECONDARY);
        view.setBackground(controlBackground()); view.setPadding(dp(13), dp(8), dp(13), dp(8));
        return view;
    }
    private ImageView image() {
        ImageView view = new ImageView(this);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setBackgroundColor(palette.SURFACE);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }
    private Button button(String label, Runnable action) {
        Button view = new Button(this);
        view.setText(label); view.setAllCaps(false); view.setTypeface(fonts.medium); view.setTextSize(17);
        view.setTextColor(palette.TEXT); view.setGravity(Gravity.CENTER); view.setMinHeight(dp(52));
        view.setPadding(dp(10), dp(6), dp(10), dp(6)); view.setBackground(controlBackground());
        view.setOnClickListener(v -> action.run());
        return view;
    }
    private void addAction(LinearLayout parent, Button button, float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(54), weight);
        params.rightMargin = dp(6);
        params.bottomMargin = dp(6);
        parent.addView(button, params);
    }
    private StateListDrawable controlBackground() {
        StateListDrawable state = new StateListDrawable();
        GradientDrawable focused = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                skin == Skin.GTA_VI ? new int[]{0xFF5B365D, 0xFF4B3C65} : new int[]{palette.RAISED, palette.SURFACE});
        focused.setCornerRadius(dp(10)); focused.setStroke(dp(3), palette.FOCUS);
        GradientDrawable plain = new GradientDrawable();
        plain.setColor(palette.SURFACE); plain.setCornerRadius(dp(10)); plain.setStroke(dp(1), palette.LINE);
        GradientDrawable disabled = new GradientDrawable();
        disabled.setColor(palette.BACKGROUND); disabled.setCornerRadius(dp(10)); disabled.setStroke(dp(1), palette.LINE);
        state.addState(new int[]{-android.R.attr.state_enabled}, disabled);
        state.addState(new int[]{android.R.attr.state_focused}, focused);
        state.addState(new int[]{android.R.attr.state_pressed}, focused);
        state.addState(new int[]{}, plain);
        return state;
    }
    private void hideKeyboard() {
        InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(keyInput.getWindowToken(), 0);
    }
}
