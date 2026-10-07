package com.anistream.app;

import android.app.Application;

/**
 * Titik masuk aplikasi. Mode tema diterapkan di sini (bukan di Activity)
 * supaya day/night sudah benar sebelum Activity pertama menginflasi layout-nya.
 * Bawaan: gelap (hitam pekat) + aksen indigo.
 */
public class AniApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.applyThemeMode(Prefs.THEME_SYSTEM);
    }
}
