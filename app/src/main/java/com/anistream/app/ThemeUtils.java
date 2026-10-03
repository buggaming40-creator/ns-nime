package com.anistream.app;

import android.app.Activity;

/**
 * Penerapan gaya aksen warna pilihan pengguna.
 *
 * Gaya overlay (values/accents.xml) hanya mengubah atribut warna aksen,
 * sehingga aman diterapkan lewat setTheme() di atas tema dasar mana pun
 * yang dipakai Activity — utama, detail seri, maupun pemutar.
 */
public final class ThemeUtils {

    private ThemeUtils() {}

    /**
     * Terapkan aksen pilihan. Panggil sebagai langkah pertama onCreate,
     * sebelum setContentView.
     */
    public static void apply(Activity activity) {
        if (activity == null) return;
        activity.setTheme(styleFor(Prefs.accentIndex(activity)));
    }

    /** Gaya overlay aksen untuk indeks tersimpan; Indigo bila di luar daftar. */
    static int styleFor(int accent) {
        switch (accent) {
            case Prefs.ACCENT_UNGU:   return R.style.Theme_AniStream_Accent_Ungu;
            case Prefs.ACCENT_BIRU:   return R.style.Theme_AniStream_Accent_Biru;
            case Prefs.ACCENT_TOSKA:  return R.style.Theme_AniStream_Accent_Toska;
            case Prefs.ACCENT_HIJAU:  return R.style.Theme_AniStream_Accent_Hijau;
            case Prefs.ACCENT_MERAH:  return R.style.Theme_AniStream_Accent_Merah;
            case Prefs.ACCENT_ORANYE: return R.style.Theme_AniStream_Accent_Oranye;
            case Prefs.ACCENT_PINK:   return R.style.Theme_AniStream_Accent_Pink;
            case Prefs.ACCENT_INDIGO:
            default:                  return R.style.Theme_AniStream_Accent_Indigo;
        }
    }
}
