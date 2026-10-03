package com.anistream.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;
import java.util.Locale;

/**
 * Tab "Setelan": tema aplikasi, warna aksen, preferensi player (kualitas,
 * autoplay, aspect ratio), penyimpanan (cache, riwayat pencarian, statistik),
 * informasi aplikasi, changelog, disclaimer, dan pengelolaan data riwayat.
 *
 * Status tema tidak memakai OnCheckedChangeListener karena listener itu juga
 * terpicu oleh pemulihan state bawaan view (restore) sehingga bisa mengganti
 * tema tanpa disengaja. Yang dipakai: klik langsung + disinkronkan ulang lewat
 * {@link #syncThemeUi()}. Pola serupa dipakai untuk chip player
 * ({@link #syncPlayerUi()}).
 *
 * Pilihan aksen disimpan di {@link Prefs} lalu Activity dibuat ulang sehingga
 * overlay warna baru terbaca oleh seluruh layar.
 */
public class SettingsFragment extends Fragment {

    /** Warna pratinjau tiap pilihan aksen (indeks sama dengan Prefs.ACCENT_*). */
    private static final int[] ACCENT_COLORS = {
            R.color.accent_indigo, R.color.accent_ungu, R.color.accent_biru,
            R.color.accent_toska, R.color.accent_hijau, R.color.accent_merah,
            R.color.accent_oranye, R.color.accent_pink
    };
    /** Label Indonesia tiap pilihan aksen. */
    private static final int[] ACCENT_LABELS = {
            R.string.accent_name_indigo, R.string.accent_name_ungu,
            R.string.accent_name_biru, R.string.accent_name_toska,
            R.string.accent_name_hijau, R.string.accent_name_merah,
            R.string.accent_name_oranye, R.string.accent_name_pink
    };

    // Atribut warna dari pustaka Material; R aplikasi tidak memuatnya.
    private static final int ATTR_COLOR_PRIMARY =
            com.google.android.material.R.attr.colorPrimary;
    private static final int ATTR_COLOR_ON_PRIMARY =
            com.google.android.material.R.attr.colorOnPrimary;

    private HistoryStore store;
    private BookmarkStore bookmarks;
    private TextView historyCount, storageStatus;
    private RadioGroup themeGroup;
    private RadioButton radioDark, radioLight, radioSystem;
    private LinearLayout accentRow;

    // Preferensi player (urutan chip = nilai Prefs.QUALITY_* / Prefs.RATIO_*).
    private Chip[] qualityChips, ratioChips;
    private MaterialSwitch autoplaySwitch, pipSwitch;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup grp,
                             @Nullable Bundle st) {
        View v = inf.inflate(R.layout.fragment_settings, grp, false);

        if (getContext() != null) {
            store = new HistoryStore(requireContext());
            bookmarks = new BookmarkStore(requireContext());
        }

        // ---- versi ----
        TextView version = v.findViewById(R.id.versionLabel);
        version.setText(getString(R.string.version_fmt, installedVersion()));

        // ---- tema ----
        themeGroup = v.findViewById(R.id.themeGroup);
        radioDark = v.findViewById(R.id.radioDark);
        radioLight = v.findViewById(R.id.radioLight);
        radioSystem = v.findViewById(R.id.radioSystem);

        View.OnClickListener pick = x -> onThemePicked(x.getId());
        radioDark.setOnClickListener(pick);
        radioLight.setOnClickListener(pick);
        radioSystem.setOnClickListener(pick);

        // ---- aksen ----
        accentRow = v.findViewById(R.id.accentRow);
        buildSwatches();

        // ---- kredit ----
        TextView dev = v.findViewById(R.id.devName);
        dev.setText(R.string.developer_name);

        // ---- data ----
        historyCount = v.findViewById(R.id.historyCount);
        updateCount();

        MaterialButton btnClear = v.findViewById(R.id.btnClearHistory);
        btnClear.setOnClickListener(x -> confirmClear());

        // ---- player ----
        qualityChips = new Chip[]{
                v.findViewById(R.id.uiQuality360), v.findViewById(R.id.uiQuality480),
                v.findViewById(R.id.uiQuality720), v.findViewById(R.id.uiQuality1080)};
        final int[] qualityValues = {
                Prefs.QUALITY_360, Prefs.QUALITY_480, Prefs.QUALITY_720, Prefs.QUALITY_1080};
        for (int i = 0; i < qualityChips.length; i++) {
            final int quality = qualityValues[i];
            qualityChips[i].setOnClickListener(x -> {
                if (!isAdded()) return;
                Prefs.setPlayerQuality(requireContext(), quality);
                syncPlayerUi();
            });
        }

        ratioChips = new Chip[]{
                v.findViewById(R.id.uiRatioFit), v.findViewById(R.id.uiRatioFill),
                v.findViewById(R.id.uiRatioZoom)};
        final int[] ratioValues = {Prefs.RATIO_FIT, Prefs.RATIO_FILL, Prefs.RATIO_ZOOM};
        for (int i = 0; i < ratioChips.length; i++) {
            final int ratio = ratioValues[i];
            ratioChips[i].setOnClickListener(x -> {
                if (!isAdded()) return;
                Prefs.setPlayerRatio(requireContext(), ratio);
                syncPlayerUi();
            });
        }

        // Klik, bukan OnCheckedChangeListener — sama seperti pemilih tema di atas.
        autoplaySwitch = v.findViewById(R.id.uiAutoplay);
        autoplaySwitch.setOnClickListener(x -> {
            if (!isAdded()) return;
            Prefs.setPlayerAutoplay(requireContext(), autoplaySwitch.isChecked());
        });

        // Mini-player PiP — pola kabel sama dengan sakelar autoplay.
        pipSwitch = v.findViewById(R.id.switchPip);
        pipSwitch.setOnClickListener(x -> {
            if (!isAdded()) return;
            Prefs.setPlayerPip(requireContext(), pipSwitch.isChecked());
        });

        // ---- penyimpanan ----
        storageStatus = v.findViewById(R.id.uiStorageStatus);
        updateStorage();

        MaterialButton btnClearCache = v.findViewById(R.id.btnClearCache);
        btnClearCache.setOnClickListener(x -> confirmClearCache());

        MaterialButton btnClearSearch = v.findViewById(R.id.btnClearSearch);
        btnClearSearch.setOnClickListener(x -> confirmClearSearch());

        syncPlayerUi();
        return v;
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        // Kunci ulang pilihan tema ke nilai tersimpan; state yang dipulihkan
        // bawaan view tidak boleh mengubah preferensi.
        syncThemeUi();
    }

    @Override
    public void onResume() {
        super.onResume();
        syncThemeUi();
        buildSwatches();
        updateCount();
        syncPlayerUi();
        updateStorage();
    }

    private void onThemePicked(int viewId) {
        if (!isAdded()) return;
        int next = viewId == R.id.radioLight ? Prefs.THEME_LIGHT
                : viewId == R.id.radioSystem ? Prefs.THEME_SYSTEM
                : Prefs.THEME_DARK;

        syncThemeUi();
        if (next == Prefs.themeMode(requireContext())) return;

        // Memicu recreate Activity — warna berubah untuk seluruh aplikasi.
        Prefs.setThemeMode(requireContext(), next);
    }

    /** Setel status ketiga radio sesuai preferensi tersimpan (tanpa menulis). */
    private void syncThemeUi() {
        if (radioDark == null || !isAdded()) return;
        int mode = Prefs.themeMode(requireContext());
        int checked = mode == Prefs.THEME_LIGHT ? R.id.radioLight
                : mode == Prefs.THEME_SYSTEM ? R.id.radioSystem
                : R.id.radioDark;

        // Satu jalur: biarkan RadioGroup yang mencentang anaknya setelah layout
        // menempel, lalu paksa drawable ke status akhir. SetChecked manual per
        // tombol + check() grup terbukti membuat titik radio tidak tergambar
        // (cincin terwarnai tapi kosong) pada sebagian perangkat.
        final int want = checked;
        themeGroup.post(() -> {
            themeGroup.check(want);
            themeGroup.jumpDrawablesToCurrentState();
        });

        // Teks pilihan terpilih ikut warna aksen; sisanya warna teks biasa.
        int active = attrColor(ATTR_COLOR_PRIMARY, R.color.primary);
        int idle = ContextCompat.getColor(requireContext(), R.color.text_primary);
        radioDark.setTextColor(checked == R.id.radioDark ? active : idle);
        radioLight.setTextColor(checked == R.id.radioLight ? active : idle);
        radioSystem.setTextColor(checked == R.id.radioSystem ? active : idle);
    }

    // ---------------------------------------------------------------- aksen

    /** Susun deretan bulatan warna sesuai indeks aksen yang tersimpan. */
    private void buildSwatches() {
        if (accentRow == null || !isAdded()) return;
        accentRow.removeAllViews();
        int selected = Prefs.accentIndex(requireContext());
        for (int i = 0; i < ACCENT_COLORS.length; i++) {
            accentRow.addView(buildSwatch(i, i == selected));
        }
    }

    /** Satu bulatan warna 36dp + label; yang terpilih diberi cincin dan centang. */
    private View buildSwatch(int index, boolean selected) {
        Context ctx = requireContext();
        int accent = attrColor(ATTR_COLOR_PRIMARY, R.color.primary);
        int onAccent = attrColor(ATTR_COLOR_ON_PRIMARY, R.color.on_primary);
        int idleText = ContextCompat.getColor(ctx, R.color.text_secondary);

        LinearLayout item = new LinearLayout(ctx);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setPadding(dp(6), dp(3), dp(6), dp(3));
        item.setContentDescription(getString(ACCENT_LABELS[index]));

        // Cincin penanda: kotak berbingkai berongga berisi bulatan warna.
        FrameLayout holder = new FrameLayout(ctx);
        holder.setLayoutParams(new LinearLayout.LayoutParams(dp(46), dp(46)));

        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.TRANSPARENT);
        if (selected) ring.setStroke(dp(2), accent);
        holder.setBackground(ring);

        View dot = new View(ctx);
        GradientDrawable fill = new GradientDrawable();
        fill.setShape(GradientDrawable.OVAL);
        fill.setColor(ContextCompat.getColor(ctx, ACCENT_COLORS[index]));
        dot.setBackground(fill);
        holder.addView(dot, new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER));

        if (selected) {
            TextView check = new TextView(ctx);
            check.setText("\u2713");
            check.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            check.setTypeface(Typeface.DEFAULT_BOLD);
            check.setTextColor(onAccent);
            holder.addView(check, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));
        }

        item.addView(holder);

        TextView label = new TextView(ctx);
        label.setText(ACCENT_LABELS[index]);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        label.setGravity(Gravity.CENTER);
        label.setTextColor(selected ? accent : idleText);
        label.setPadding(0, dp(4), 0, 0);
        item.addView(label);

        item.setOnClickListener(x -> onAccentPicked(index));
        return item;
    }

    /** Simpan pilihan aksen lalu buat ulang Activity agar warna langsung berubah. */
    private void onAccentPicked(int index) {
        if (!isAdded()) return;
        if (index == Prefs.accentIndex(requireContext())) return;
        Prefs.setAccent(requireContext(), index);
        buildSwatches();
        requireActivity().recreate();
    }

    /** Ambil warna dari atribut tema; kembali ke warna cadangan bila tidak ada. */
    private int attrColor(int attrRes, int fallbackRes) {
        if (!isAdded()) return 0;
        TypedValue tv = new TypedValue();
        if (requireContext().getTheme().resolveAttribute(attrRes, tv, true)
                && tv.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return tv.data;
        }
        return ContextCompat.getColor(requireContext(), fallbackRes);
    }

    private int dp(float value) {
        return Math.round(value * requireContext().getResources()
                .getDisplayMetrics().density);
    }

    // --------------------------------------------------------------- lainnya

    private void updateCount() {
        if (store == null || historyCount == null) return;
        historyCount.setText(getString(R.string.history_items_fmt, store.count()));
    }

    /** Versi aplikasi dibaca dari PackageManager agar tidak bergantung pada BuildConfig. */
    private String installedVersion() {
        try {
            return requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "1.0";
        }
    }

    // --------------------------------------------------------------- player

    /** Sinkronkan chip kualitas/aspect ratio dan sakelar autoplay ke Prefs. */
    private void syncPlayerUi() {
        if (!isAdded() || qualityChips == null) return;
        int quality = Prefs.playerQuality(requireContext());
        for (int i = 0; i < qualityChips.length; i++) {
            if (qualityChips[i] != null) qualityChips[i].setChecked(i == quality);
        }
        int ratio = Prefs.playerRatio(requireContext());
        for (int i = 0; i < ratioChips.length; i++) {
            if (ratioChips[i] != null) ratioChips[i].setChecked(i == ratio);
        }
        if (autoplaySwitch != null) {
            autoplaySwitch.setChecked(Prefs.playerAutoplay(requireContext()));
        }
        if (pipSwitch != null) {
            pipSwitch.setChecked(Prefs.playerPip(requireContext()));
        }
    }

    // ----------------------------------------------------------- penyimpanan

    /** Baris status "X episode · Y bookmark · Z KB cache". */
    private void updateStorage() {
        if (storageStatus == null || !isAdded()) return;
        int episodes = store != null ? store.count() : 0;
        int saved = bookmarks != null ? bookmarks.count() : 0;
        storageStatus.setText(getString(R.string.storage_status,
                episodes, saved, formatSize(cacheBytes())));
    }

    private long cacheBytes() {
        if (getContext() == null) return 0;
        return sizeOf(requireContext().getCacheDir());
    }

    private static long sizeOf(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long total = 0;
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) total += sizeOf(k);
        }
        return total;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) {
            long kb = Math.max(1L, Math.round(bytes / 1024.0));
            return String.format(Locale.getDefault(), "%d KB", kb);
        }
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /**
     * Hapus isi cache gambar lalu kosongkan LruCache memori. Hanya direktori
     * cache yang dibersihkan — database, SharedPreferences, dan berkas sistem
     * aplikasi (lib) tidak pernah disentuh.
     */
    private void clearCache() {
        if (getContext() == null) return;
        deleteContents(requireContext().getCacheDir());
        ImageLoader.clearMemory();
    }

    private void deleteContents(File dir) {
        File[] kids = dir == null ? null : dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            String name = f.getName();
            // Jaga-jaga: berkas data aplikasi tidak boleh ikut terhapus.
            if (name.equals("lib") || name.equals("shared_prefs")
                    || name.equals("databases") || name.equals("code_cache")) {
                continue;
            }
            if (f.isDirectory()) deleteContents(f);
            f.delete();
        }
    }

    private void confirmClearCache() {
        if (getContext() == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.clear_cache)
                .setMessage(R.string.clear_cache_msg)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    clearCache();
                    updateStorage();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmClearSearch() {
        if (getContext() == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.clear_search_history)
                .setMessage(R.string.clear_search_history_msg)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    Prefs.clearRecents(requireContext());
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmClear() {
        if (getContext() == null || store == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.clear_history)
                .setMessage(R.string.clear_history_msg)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    store.clear();
                    updateCount();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
