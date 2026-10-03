package com.anistream.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Preferensi pengguna: tema aplikasi, warna aksen, dan daftar pencarian
 * terakhir. Disimpan di SharedPreferences agar bertahan setelah aplikasi
 * ditutup.
 */
public final class Prefs {

    /** Mode tema — urutannya dipakai langsung oleh RadioGroup di Setelan. */
    public static final int THEME_DARK = 0;     // bawaan
    public static final int THEME_LIGHT = 1;
    public static final int THEME_SYSTEM = 2;

    /** Pilihan warna aksen — urutannya sama dengan deretan bulatan di Setelan. */
    public static final int ACCENT_INDIGO = 0;  // bawaan
    public static final int ACCENT_UNGU = 1;
    public static final int ACCENT_BIRU = 2;
    public static final int ACCENT_TOSKA = 3;
    public static final int ACCENT_HIJAU = 4;
    public static final int ACCENT_MERAH = 5;
    public static final int ACCENT_ORANYE = 6;
    public static final int ACCENT_PINK = 7;

    private static final String FILE = "anistream_prefs";
    private static final String KEY_THEME = "theme";
    private static final String KEY_ACCENT = "accent_choice";
    private static final String KEY_RECENTS = "recents";
    private static final String SEP = "\u0001";
    private static final int MAX_RECENTS = 8;

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ tema

    /** Mode tersimpan; gelap bila belum pernah diubah. */
    public static int themeMode(Context c) {
        return sp(c).getInt(KEY_THEME, THEME_DARK);
    }

    /** Terapkan mode tema ke seluruh proses (memicu recreate Activity). */
    public static void applyThemeMode(int mode) {
        int m;
        if (mode == THEME_LIGHT) {
            m = AppCompatDelegate.MODE_NIGHT_NO;
        } else if (mode == THEME_SYSTEM) {
            m = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        } else {
            m = AppCompatDelegate.MODE_NIGHT_YES;
        }
        AppCompatDelegate.setDefaultNightMode(m);
    }

    public static void setThemeMode(Context c, int mode) {
        SharedPreferences p = sp(c);
        // Nilai yang sama tidak perlu ditulis ulang; hanya ketukan pengguna
        // di Setelan yang boleh mengubah preferensi, dan hanya sekali.
        if (p.getInt(KEY_THEME, THEME_DARK) == mode) return;
        p.edit().putInt(KEY_THEME, mode).apply();
        applyThemeMode(mode);
    }

    /** --------------------------------------------------------- aksen warna */

    /** Indeks aksen tersimpan; Indigo bila belum pernah diubah. */
    public static int accentIndex(Context c) {
        return sp(c).getInt(KEY_ACCENT, ACCENT_INDIGO);
    }

    /** Simpan pilihan aksen; tidak menulis bila nilainya sama dengan tersimpan. */
    public static void setAccent(Context c, int accent) {
        if (accent < ACCENT_INDIGO || accent > ACCENT_PINK) return;
        SharedPreferences p = sp(c);
        if (p.getInt(KEY_ACCENT, ACCENT_INDIGO) == accent) return;
        p.edit().putInt(KEY_ACCENT, accent).apply();
    }

    // ----------------------------------------------------- preferensi player

    /** Kualitas video pilihan (label preferensi; kualitas akhir dari sumber). */
    public static final int QUALITY_360 = 0;
    public static final int QUALITY_480 = 1;
    public static final int QUALITY_720 = 2;   // bawaan
    public static final int QUALITY_1080 = 3;

    /** Aspect ratio pemutar → RESIZE_MODE_FIT / FILL / ZOOM. */
    public static final int RATIO_FIT = 0;     // bawaan
    public static final int RATIO_FILL = 1;
    public static final int RATIO_ZOOM = 2;

    private static final String KEY_PLAYER_QUALITY = "player_quality";
    private static final String KEY_PLAYER_AUTOPLAY = "player_autoplay";
    private static final String KEY_PLAYER_RATIO = "player_ratio";
    private static final String KEY_PLAYER_SPEED = "player_speed";
    private static final String KEY_PLAYER_PIP = "player_pip";

    /** Kecepatan putar (bawaan 1x, batas 0,5–2x). */
    public static float playerSpeed(Context c) {
        try {
            float s = sp(c).getFloat(KEY_PLAYER_SPEED, 1f);
            return (s < 0.5f || s > 2f) ? 1f : s;
        } catch (Throwable t) {
            return 1f;
        }
    }

    public static void setPlayerSpeed(Context c, float speed) {
        float s = Math.max(0.5f, Math.min(2f, speed));
        sp(c).edit().putFloat(KEY_PLAYER_SPEED, s).apply();
    }

    /** Kualitas tersimpan; 720p bila belum pernah diubah. */
    public static int playerQuality(Context c) {
        int q = sp(c).getInt(KEY_PLAYER_QUALITY, QUALITY_720);
        return (q < QUALITY_360 || q > QUALITY_1080) ? QUALITY_720 : q;
    }

    public static void setPlayerQuality(Context c, int quality) {
        if (quality < QUALITY_360 || quality > QUALITY_1080) return;
        sp(c).edit().putInt(KEY_PLAYER_QUALITY, quality).apply();
    }

    /** Autoplay episode berikutnya; menyala secara bawaan. */
    public static boolean playerAutoplay(Context c) {
        return sp(c).getBoolean(KEY_PLAYER_AUTOPLAY, true);
    }

    public static void setPlayerAutoplay(Context c, boolean on) {
        sp(c).edit().putBoolean(KEY_PLAYER_AUTOPLAY, on).apply();
    }

    /** Aspect ratio tersimpan; Fit bawaan. */
    public static int playerRatio(Context c) {
        int r = sp(c).getInt(KEY_PLAYER_RATIO, RATIO_FIT);
        return (r < RATIO_FIT || r > RATIO_ZOOM) ? RATIO_FIT : r;
    }

    public static void setPlayerRatio(Context c, int ratio) {
        if (ratio < RATIO_FIT || ratio > RATIO_ZOOM) return;
        sp(c).edit().putInt(KEY_PLAYER_RATIO, ratio).apply();
    }

    /** Mini-player PiP saat keluar player selagi video berputar; nyala bawaan. */
    public static boolean playerPip(Context c) {
        return sp(c).getBoolean(KEY_PLAYER_PIP, true);
    }

    public static void setPlayerPip(Context c, boolean on) {
        sp(c).edit().putBoolean(KEY_PLAYER_PIP, on).apply();
    }

    // ----------------------------------------------------------- pencarian

    /** Pencarian terakhir, terbaru di depan. */
    public static List<String> recents(Context c) {
        List<String> out = new ArrayList<>();
        String raw = sp(c).getString(KEY_RECENTS, "");
        if (raw == null || raw.isEmpty()) return out;
        for (String s : raw.split(SEP)) {
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    /** Catat pencarian; paling baru diletakkan di urutan pertama. */
    public static void addRecent(Context c, String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return;

        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add(q);
        for (String s : recents(c)) set.add(s);

        while (set.size() > MAX_RECENTS) {
            Iterator<String> it = set.iterator();
            it.next();
            it.remove();
        }

        StringBuilder sb = new StringBuilder();
        for (String s : set) {
            if (sb.length() > 0) sb.append(SEP);
            sb.append(s);
        }
        sp(c).edit().putString(KEY_RECENTS, sb.toString()).apply();
    }

    public static void clearRecents(Context c) {
        sp(c).edit().remove(KEY_RECENTS).apply();
    }
}
