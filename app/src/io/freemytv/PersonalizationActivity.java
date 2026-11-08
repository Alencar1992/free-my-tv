package io.freemytv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Private, remote-friendly editor. Opening it never changes TV/Home mode or installs/removes apps. */
public final class PersonalizationActivity extends Activity {
    static final String EXTRA_IMPORT = "favorite_channels_import";
    private static final int ROOT = 0, APPS = 1, FAVORITES = 2, TRAILERS = 3;
    private static final int MAX_FAVORITES = 12, MAX_TRAILERS = 8;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Skin.Palette palette = new Skin.Palette();
    private Ui.Fonts fonts;
    private Skin skin;
    private LinearLayout content;
    private ScrollView scroll;
    private int page = ROOT;
    private boolean ready, destroyed, loadFailed;
    private List<String> pendingImport;
    private List<HomeApps.Item> available = new ArrayList<>();
    private List<String> apps = new ArrayList<>(), favorites = new ArrayList<>();
    private List<Personalization.Trailer> trailers = new ArrayList<>();
    private boolean publicDiscovery, trailersEnabled;
    private String returnFocus;
    private String requestedFocus;
    private AlertDialog importReview;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        skin = SkinPreferences.load(this);
        fonts = Ui.fonts(this, skin);
        skin.apply(palette, skin.isNight(HomeData.get(this).isNight(System.currentTimeMillis())) ? 1 : 0);
        if (state != null) page = state.getInt("page", ROOT);
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(36), dp(20), dp(36), dp(16));
        outer.setBackgroundColor(palette.BACKGROUND);
        TextView title = text(Ui.tr("Personalizar home", "Personalize home"), 28, fonts.displaySemibold, palette.TEXT);
        outer.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = text(Ui.tr("Setas para navegar · OK para escolher · Voltar para sair", "Arrows to navigate · OK to choose · Back to leave"), 16, fonts.regular, palette.SECONDARY);
        hint.setPadding(0, dp(5), 0, dp(12));
        outer.addView(hint);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4), dp(4), dp(4), dp(20));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        outer.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(outer);
        render();
        load();
        if (state == null) consumeImport(getIntent());
        else getIntent().removeExtra(EXTRA_IMPORT);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        consumeImport(intent);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("page", page);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        destroyed = true;
        io.shutdownNow();
        super.onDestroy();
    }

    /** Both package discovery and reading source defaults happen off the UI thread. */
    private void load() {
        ready = false;
        loadFailed = false;
        render();
        io.execute(() -> {
            List<HomeApps.Item> found;
            List<String> selected, channels;
            List<Personalization.Trailer> sources;
            boolean discovery, enabled;
            try {
                found = HomeApps.editableApps(this);
                selected = HomeApps.selectedPackages(this);
                channels = Personalization.favorites(this);
                sources = Personalization.trailerSources(this);
                discovery = Personalization.publicDiscovery(this);
                enabled = Personalization.trailersEnabled(this);
            } catch (RuntimeException e) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    ready = true;
                    loadFailed = true;
                    render();
                    error(Ui.tr("Não foi possível carregar os aplicativos. Feche e tente novamente.", "Could not load apps. Close and try again."));
                });
                return;
            }
            runOnUiThread(() -> {
                if (destroyed) return;
                available = new ArrayList<>(found);
                apps = new ArrayList<>(selected);
                favorites = new ArrayList<>(channels);
                trailers = new ArrayList<>(sources);
                publicDiscovery = discovery;
                trailersEnabled = enabled;
                ready = true;
                render();
                if (pendingImport != null) {
                    List<String> incoming = pendingImport;
                    pendingImport = null;
                    reviewImport(incoming);
                }
            });
        });
    }

    private void render() {
        if (content == null) return;
        View focused = getCurrentFocus();
        String focus = requestedFocus != null ? requestedFocus
                : focused != null && focused.getTag() instanceof String ? (String) focused.getTag() : returnFocus;
        requestedFocus = null;
        int position = scroll.getScrollY();
        content.removeAllViews();
        if (!ready) {
            note(Ui.tr("Carregando aplicativos e preferências…", "Loading apps and preferences…"));
            return;
        }
        if (loadFailed) {
            note(Ui.tr("Não foi possível carregar as preferências.", "Could not load preferences."));
            button("retry", Ui.tr("Tentar novamente", "Try again"), this::load);
            button("done", Ui.tr("Voltar", "Back"), this::finish);
        } else if (page == ROOT) renderRoot();
        else {
            button("back", Ui.tr("‹ Voltar à personalização", "‹ Back to personalization"), () -> show(ROOT));
            if (page == APPS) renderApps();
            if (page == FAVORITES) renderFavorites();
            if (page == TRAILERS) renderTrailers();
        }
        final String target = focus;
        content.post(() -> {
            if (destroyed) return;
            View restore = target == null ? null : content.findViewWithTag(target);
            if (restore != null && restore.isFocusable()) {
                restore.requestFocus();
                scroll.scrollTo(0, position);
            } else {
                View first = firstFocusable(content);
                if (first != null) first.requestFocus();
            }
        });
    }

    private void renderRoot() {
        button("apps", Ui.tr("Aplicativos da home", "Home apps") + "  ·  " + apps.size() + "/7", () -> show(APPS));
        button("favorites", Ui.tr("Canais do YouTube", "YouTube channels") + "  ·  " + favorites.size() + "/12", () -> show(FAVORITES));
        button("xbox", Ui.tr("🎮 Xbox · jogos e estatísticas", "🎮 Xbox · games and stats"), () -> startActivity(new Intent(this,XboxActivity.class)));
        button("trailers", Ui.tr("Trailers de streaming", "Streaming trailers") + "  ·  " + trailers.size() + "/8", () -> show(TRAILERS));
        section(Ui.tr("Conteúdo da home", "Home content"));
        note(Ui.tr("Escolha uma fonte. A configuração do servidor é preservada ao alternar.", "Choose one source. Switching preserves the server configuration."));
        button("source", Ui.tr("Fonte: ", "Source: ") + (publicDiscovery ? Ui.tr("Canais e trailers", "Channels and trailers") : Ui.tr("Feed do servidor", "Server feed")), this::chooseSource);
        button("refresh", Ui.tr("Atualizar canais e trailers agora", "Refresh channels and trailers now"), () -> {
            if (!publicDiscovery) {
                error(Ui.tr("Selecione Canais e trailers em Fonte para usar essa atualização.", "Choose Channels and trailers under Source to refresh this content."));
                return;
            }
            Recs.get(this).refreshPublicNow();
            Toast.makeText(this, Ui.tr("Atualização iniciada. Os dados disponíveis continuam na home.", "Refresh started. Available content remains on Home."), Toast.LENGTH_LONG).show();
        });
        button("done", Ui.tr("Concluir e voltar", "Done and back"), this::finish);
    }

    private void renderApps() {
        section(Ui.tr("Aplicativos da home", "Home apps") + "  ·  " + apps.size() + "/7");
        note(Ui.tr("OK em um app para mover ou remover seu atalho. O aplicativo continua instalado e disponível em Todos os Apps.", "OK on an app to move or remove its shortcut. The app stays installed and available in All Apps."));
        if (apps.isEmpty()) note(Ui.tr("Nenhum atalho selecionado. Ajustes e Todos os Apps continuam disponíveis.", "No shortcuts selected. Settings and All Apps remain available."));
        for (int i = 0; i < apps.size(); i++) {
            final int index = i;
            String pkg = apps.get(i);
            button("app:" + pkg, (i + 1) + "  " + appLabel(pkg), () -> appActions(index));
        }
        button("add-app", Ui.tr("+ Adicionar aplicativo", "+ Add app"), this::addApp);
        button("restore-apps", Ui.tr("Restaurar atalhos padrão", "Restore default shortcuts"), () -> confirm(
                Ui.tr("Restaurar somente os atalhos da home?", "Restore only Home shortcuts?"), () -> {
                    Personalization.restoreApps(this);
                    changed();
                    returnFocus = "restore-apps";
                    load();
                }));
    }

    private void renderFavorites() {
        section(Ui.tr("Canais do YouTube", "YouTube channels") + "  ·  " + favorites.size() + "/12");
        note(Ui.tr("Use @canal ou o link público /channel/UC…. Vídeos recentes abrem no YouTube com OK; histórico e publicações de texto não são consultados.", "Use @channel or a public /channel/UC… link. OK opens recent videos in YouTube; watch history and text posts are not accessed."));
        if (!publicDiscovery) note(Ui.tr("Para exibir os canais na home, escolha Canais e trailers em Fonte.", "To show channels on Home, select Channels and trailers under Source."));
        if (favorites.isEmpty()) note(Ui.tr("Nenhum canal adicionado.", "No channels added."));
        for (int i = 0; i < favorites.size(); i++) {
            final int index = i;
            String ref = favorites.get(i);
            button("favorite:" + ChannelRef.key(ref), (i + 1) + "  " + shortRef(ref), () -> favoriteActions(index));
        }
        button("add-favorite", Ui.tr("+ Adicionar canal", "+ Add channel"), () -> {
            if (favorites.size() >= MAX_FAVORITES) { error(limit(12)); return; }
            editChannel(Ui.tr("Adicionar canal", "Add channel"), "", value -> {
                List<String> next = new ArrayList<>(favorites);
                ensureNewChannel(next, value, -1);
                next.add(value);
                Personalization.saveFavorites(this, next);
                favorites = next;
                changed();
                render();
            });
        });
    }

    private void renderTrailers() {
        section(Ui.tr("Trailers de streaming", "Streaming trailers") + "  ·  " + trailers.size() + "/8");
        note(Ui.tr("Vídeos com trailer, teaser ou prévia no título. OK abre o vídeo no YouTube. Esta lista não é o catálogo da sua conta.", "Videos with trailer, teaser or preview in the title. OK opens YouTube. This list is not your account's catalog."));
        if (!publicDiscovery) note(Ui.tr("Para exibir trailers na home, escolha Canais e trailers em Fonte.", "To show trailers on Home, select Channels and trailers under Source."));
        button("trailers-enabled", (trailersEnabled ? "✓ " : "○ ") + Ui.tr("Mostrar trailers na home", "Show trailers on Home"), () -> {
            trailersEnabled = !trailersEnabled;
            Personalization.setTrailersEnabled(this, trailersEnabled);
            changed();
            render();
        });
        if (trailers.isEmpty()) note(Ui.tr("Nenhuma fonte disponível para os apps selecionados. Adicione uma fonte ou restaure as predefinições.", "No sources available for the selected apps. Add a source or restore presets."));
        for (int i = 0; i < trailers.size(); i++) {
            final int index = i;
            Personalization.Trailer source = trailers.get(i);
            button("trailer:" + source.pkg + ":" + ChannelRef.key(source.channel), source.label + "  ·  " + shortRef(source.channel) + "\n" +
                    (source.official ? Ui.tr("Predefinição oficial", "Official preset") : Ui.tr("Fonte personalizada", "Custom source")), () -> trailerActions(index));
        }
        button("add-trailer", Ui.tr("+ Associar canal a um aplicativo", "+ Link channel to an app"), this::addTrailer);
        button("restore-trailers", Ui.tr("Restaurar fontes predefinidas", "Restore source presets"), () -> confirm(
                Ui.tr("Substituir as fontes de trailers pelas predefinições dos apps instalados na home?", "Replace trailer sources with presets for installed Home apps?"), () -> {
                    Personalization.restoreTrailerSources(this);
                    changed();
                    returnFocus = "restore-trailers";
                    load();
                }));
    }

    private void chooseSource() {
        String[] labels = {Ui.tr("Canais e trailers", "Channels and trailers"), Ui.tr("Feed do servidor", "Server feed")};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(Ui.tr("Fonte do conteúdo da home", "Home content source"))
                .setSingleChoiceItems(labels, publicDiscovery ? 0 : 1, (d, which) -> {
                    publicDiscovery = which == 0;
                    Personalization.setPublicDiscovery(this, publicDiscovery);
                    changed();
                    d.dismiss();
                    render();
                }).setNegativeButton(Ui.tr("Cancelar", "Cancel"), null).create();
        dialog.show();
    }

    private void appActions(int index) {
        if (index < 0 || index >= apps.size()) return;
        String pkg = apps.get(index);
        List<String> labels = new ArrayList<>();
        List<Integer> actions = new ArrayList<>();
        if (index > 0) { labels.add(Ui.tr("Subir uma posição", "Move up")); actions.add(-1); }
        if (index + 1 < apps.size()) { labels.add(Ui.tr("Descer uma posição", "Move down")); actions.add(1); }
        labels.add(Ui.tr("Remover atalho da home", "Remove Home shortcut")); actions.add(0);
        new AlertDialog.Builder(this).setTitle(appLabel(pkg)).setItems(labels.toArray(new String[0]), (d, which) -> {
            int action = actions.get(which);
            apps = action == 0 ? AppOrder.remove(apps, pkg) : AppOrder.move(apps, index, action);
            Personalization.saveApps(this, apps);
            changed();
            render();
        }).setNegativeButton(Ui.tr("Cancelar", "Cancel"), null).show();
    }

    private void addApp() {
        if (apps.size() >= AppOrder.MAX) { error(limit(7)); return; }
        List<HomeApps.Item> choices = new ArrayList<>();
        for (HomeApps.Item item : available) if (!apps.contains(item.pkg)) choices.add(item);
        if (choices.isEmpty()) { error(Ui.tr("Todos os aplicativos disponíveis já estão na home.", "All available apps are already on Home.")); return; }
        pickApp(choices, item -> {
            apps = AppOrder.add(apps, item.pkg);
            Personalization.saveApps(this, apps);
            changed();
            render();
        });
    }

    private void favoriteActions(int index) {
        if (index < 0 || index >= favorites.size()) return;
        String ref = favorites.get(index);
        new AlertDialog.Builder(this).setTitle(shortRef(ref)).setItems(
                new String[]{Ui.tr("Editar canal", "Edit channel"), Ui.tr("Remover canal", "Remove channel")}, (d, which) -> {
                    if (which == 1) {
                        List<String> next = new ArrayList<>(favorites);
                        next.remove(index);
                        Personalization.saveFavorites(this, next);
                        favorites = next;
                        changed();
                        render();
                    } else editChannel(Ui.tr("Editar canal", "Edit channel"), ref, value -> {
                        List<String> next = new ArrayList<>(favorites);
                        ensureNewChannel(next, value, index);
                        next.set(index, value);
                        Personalization.saveFavorites(this, next);
                        favorites = next;
                        changed();
                        render();
                    });
                }).setNegativeButton(Ui.tr("Cancelar", "Cancel"), null).show();
    }

    private void addTrailer() {
        if (trailers.size() >= MAX_TRAILERS) { error(limit(8)); return; }
        if (available.isEmpty()) { error(Ui.tr("Nenhum aplicativo de TV disponível.", "No TV apps available.")); return; }
        pickApp(available, item -> editChannel(Ui.tr("Canal público de trailers · ", "Public trailer channel · ") + item.label, "", value -> {
            List<Personalization.Trailer> next = new ArrayList<>(trailers);
            ensureNewTrailer(next, item.pkg, value, -1);
            next.add(new Personalization.Trailer(item.pkg, item.label, value, false));
            Personalization.saveTrailerSources(this, next);
            trailers = next;
            changed();
            render();
        }));
    }

    private void trailerActions(int index) {
        if (index < 0 || index >= trailers.size()) return;
        Personalization.Trailer source = trailers.get(index);
        new AlertDialog.Builder(this).setTitle(source.label).setItems(
                new String[]{Ui.tr("Editar canal (fonte personalizada)", "Edit channel (custom source)"), Ui.tr("Remover fonte", "Remove source")}, (d, which) -> {
                    if (which == 1) {
                        List<Personalization.Trailer> next = new ArrayList<>(trailers);
                        next.remove(index);
                        Personalization.saveTrailerSources(this, next);
                        trailers = next;
                        changed();
                        render();
                    } else editChannel(Ui.tr("Fonte personalizada · ", "Custom source · ") + source.label, source.channel, value -> {
                        List<Personalization.Trailer> next = new ArrayList<>(trailers);
                        ensureNewTrailer(next, source.pkg, value, index);
                        next.set(index, new Personalization.Trailer(source.pkg, source.label, value, false));
                        Personalization.saveTrailerSources(this, next);
                        trailers = next;
                        changed();
                        render();
                    });
                }).setNegativeButton(Ui.tr("Cancelar", "Cancel"), null).show();
    }

    private interface ChannelAction { void save(String normalized); }
    private interface AppAction { void choose(HomeApps.Item item); }

    private void pickApp(List<HomeApps.Item> choices, AppAction action) {
        String[] labels = new String[choices.size()];
        for (int i = 0; i < choices.size(); i++) labels[i] = choices.get(i).label;
        new AlertDialog.Builder(this).setTitle(Ui.tr("Escolher aplicativo", "Choose app"))
                .setItems(labels, (d, which) -> action.choose(choices.get(which)))
                .setNegativeButton(Ui.tr("Cancelar", "Cancel"), null).show();
    }

    private void editChannel(String title, String current, ChannelAction action) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(21);
        input.setTextColor(palette.TEXT);
        input.setHintTextColor(palette.SECONDARY);
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(palette.ACCENT));
        input.setTypeface(fonts.regular);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint(Ui.tr("@canal ou https://www.youtube.com/channel/UC…", "@channel or https://www.youtube.com/channel/UC…"));
        input.setText(current);
        input.setSelectAllOnFocus(true);
        LinearLayout box = dialogBox();
        box.addView(input, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setView(box)
                .setPositiveButton(Ui.tr("Salvar", "Save"), null)
                .setNegativeButton(Ui.tr("Cancelar", "Cancel"), null).create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    String value = ChannelRef.normalize(input.getText().toString());
                    action.save(value);
                    dialog.dismiss();
                } catch (IllegalArgumentException e) {
                    input.setError(e.getMessage() != null && e.getMessage().startsWith("UI:") ? e.getMessage().substring(3) : Ui.tr("Use um @canal ou link HTTPS válido de canal do YouTube.", "Use a valid YouTube @channel or HTTPS channel link."));
                    input.requestFocus();
                }
            });
            input.requestFocus();
        });
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private void consumeImport(Intent intent) {
        if (intent == null || !intent.hasExtra(EXTRA_IMPORT)) return;
        String encoded;
        try { encoded = intent.getStringExtra(EXTRA_IMPORT); }
        catch (RuntimeException e) { encoded = null; }
        intent.removeExtra(EXTRA_IMPORT);
        final List<String> incoming;
        try { incoming = ChannelRef.decodeImport(encoded); }
        catch (IllegalArgumentException e) {
            error(Ui.tr("Importação inválida. Nenhuma configuração foi alterada.", "Invalid import. No settings were changed."));
            return;
        }
        if (!ready || loadFailed) {
            pendingImport = incoming;
            return;
        }
        reviewImport(incoming);
    }

    private void reviewImport(List<String> incoming) {
        if (importReview != null) importReview.dismiss();
        LinearLayout box = dialogBox();
        TextView description = text(Ui.tr("Confira os canais abaixo. Confirmar substitui somente sua lista de canais; cancelar mantém a lista atual.", "Review the channels below. Confirming replaces only your channel list; cancel keeps the current list."), 18, fonts.regular, palette.TEXT);
        box.addView(description);
        ScrollView list = new ScrollView(this);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        for (String ref : incoming) {
            TextView row = text("• " + ref, 17, fonts.regular, palette.SECONDARY);
            row.setPadding(0, dp(5), 0, dp(5));
            rows.addView(row);
        }
        if (incoming.isEmpty()) rows.addView(text(Ui.tr("Lista vazia: os canais atuais serão removidos.", "Empty list: current channels will be removed."), 17, fonts.regular, palette.SECONDARY));
        list.addView(rows);
        list.setFocusable(true);
        box.addView(list, new LinearLayout.LayoutParams(-1, dp(160)));
        CheckBox use = new CheckBox(this);
        use.setText(Ui.tr("Usar Canais e trailers na home", "Use Channels and trailers on Home"));
        use.setTextColor(palette.TEXT);
        use.setTextSize(18);
        use.setTypeface(fonts.medium);
        use.setChecked(false);
        box.addView(use);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(Ui.tr("Revisar importação de canais", "Review channel import"))
                .setView(box).setNegativeButton(Ui.tr("Cancelar", "Cancel"), null)
                .setPositiveButton(Ui.tr("Confirmar canais", "Confirm channels"), (d, which) -> {
                    Personalization.saveFavorites(this, incoming);
                    favorites = new ArrayList<>(incoming);
                    if (use.isChecked()) {
                        Personalization.setPublicDiscovery(this, true);
                        publicDiscovery = true;
                    }
                    changed();
                    page = FAVORITES;
                    render();
                }).create();
        importReview = dialog;
        dialog.setOnDismissListener(d -> { if (importReview == dialog) importReview = null; });
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus());
        dialog.show();
    }

    private void ensureNewChannel(List<String> refs, String value, int except) {
        for (int i = 0; i < refs.size(); i++) if (i != except && ChannelRef.key(refs.get(i)).equals(ChannelRef.key(value)))
            throw new IllegalArgumentException("UI:" + Ui.tr("Este canal já foi adicionado.", "This channel is already added."));
    }

    private void ensureNewTrailer(List<Personalization.Trailer> sources, String pkg, String value, int except) {
        for (int i = 0; i < sources.size(); i++) {
            Personalization.Trailer source = sources.get(i);
            if (i != except && source.pkg.equals(pkg) && ChannelRef.key(source.channel).equals(ChannelRef.key(value)))
                throw new IllegalArgumentException("UI:" + Ui.tr("Esta fonte já foi adicionada ao aplicativo.", "This source is already linked to the app."));
        }
    }

    private void show(int next) {
        page = next;
        returnFocus = next == APPS ? "apps" : next == FAVORITES ? "favorites" : next == TRAILERS ? "trailers" : returnFocus;
        requestedFocus = next == ROOT ? returnFocus : "back";
        render();
        scroll.scrollTo(0, 0);
    }

    @Override public void onBackPressed() {
        if (page != ROOT) show(ROOT);
        else super.onBackPressed();
    }

    private void changed() { Recs.get(this).settingsChanged(); }
    private String limit(int count) { return Ui.tr("Limite de ", "Limit of ") + count + Ui.tr(" itens. Remova um antes de adicionar outro.", " items. Remove one before adding another."); }
    private void error(String message) { new AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show(); }
    private void confirm(String message, Runnable action) {
        AlertDialog dialog = new AlertDialog.Builder(this).setMessage(message).setNegativeButton(Ui.tr("Cancelar", "Cancel"), null)
                .setPositiveButton(Ui.tr("Restaurar", "Restore"), (d, which) -> action.run()).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus());
        dialog.show();
    }
    private String appLabel(String pkg) { for (HomeApps.Item item : available) if (pkg.equals(item.pkg)) return item.label; return pkg; }
    private String shortRef(String ref) { return ref.replace("https://www.youtube.com/", ""); }
    private int dp(float size) { return Math.round(size * getResources().getDisplayMetrics().density); }
    private TextView text(String value, float size, Typeface face, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTypeface(face); view.setTextColor(color);
        return view;
    }
    private void section(String title) {
        TextView view = text(title, 24, fonts.displaySemibold, palette.TEXT);
        view.setPadding(dp(2), dp(22), 0, dp(8));
        content.addView(view);
    }
    private void note(String value) {
        TextView view = text(value, 17, fonts.regular, palette.SECONDARY);
        view.setPadding(dp(2), dp(3), dp(8), dp(12));
        content.addView(view);
    }
    private LinearLayout dialogBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), dp(8));
        box.setBackgroundColor(palette.BACKGROUND);
        return box;
    }
    private void button(String key, String label, Runnable action) {
        Button view = new Button(this);
        view.setTag(key);
        view.setText(label);
        view.setAllCaps(false);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        view.setTypeface(fonts.medium);
        view.setTextSize(20);
        view.setTextColor(palette.TEXT);
        view.setMinHeight(dp(58));
        view.setPadding(dp(18), dp(10), dp(18), dp(10));
        view.setBackground(buttonBackground());
        view.setFocusable(true);
        view.setOnFocusChangeListener((v, focused) -> {
            v.animate().translationZ(focused ? dp(4) : 0).setDuration(130).start();
        });
        view.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(9);
        content.addView(view, params);
    }
    private StateListDrawable buttonBackground() {
        GradientDrawable plain = new GradientDrawable();
        plain.setColor(palette.SURFACE); plain.setCornerRadius(dp(12)); plain.setStroke(dp(1), palette.LINE);
        GradientDrawable focused = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                skin == Skin.GTA_VI ? new int[]{0xFF5B365D, 0xFF4B3C65, 0xFF584358} : new int[]{palette.RAISED, palette.SURFACE});
        focused.setCornerRadius(dp(12)); focused.setStroke(dp(3), palette.FOCUS);
        StateListDrawable state = new StateListDrawable();
        state.addState(new int[]{android.R.attr.state_focused}, focused);
        state.addState(new int[]{android.R.attr.state_pressed}, focused);
        state.addState(new int[]{}, plain);
        return state;
    }
    private View firstFocusable(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child.isFocusable()) return child;
            if (child instanceof ViewGroup) {
                View found = firstFocusable((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }
}
