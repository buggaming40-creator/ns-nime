package com.anistream.app;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Layar utama: ViewPager2 (bisa digeser) + BottomNavigationView —
 * Terbaru, Cari, Tersimpan, Riwayat, Setelan.
 * Keduanya disinkronkan dua arah — tap tab pindah halaman, geser pindah tab.
 */
public class MainActivity extends AppCompatActivity {

    private static final int[] TABS = {
            R.id.nav_latest, R.id.nav_search, R.id.nav_saved,
            R.id.nav_history, R.id.nav_settings
    };
    private static final String KEY_TAB = "anistream.tab";

    /** Extra untuk membuka tab tertentu (mis. Cari dari chip genre). */
    public static final String EXTRA_TAB = "anistream.tab.extra";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Aksen pilihan diterapkan paling awal sebelum layout diinflasi.
        ThemeUtils.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        hideSystemBars();

        final ViewPager2 pager = findViewById(R.id.pager);
        final BottomNavigationView nav = findViewById(R.id.bottomNav);

        pager.setAdapter(new PagerAdapter(this));
        // Semua halaman disimpan di memori supaya tidak memuat ulang tiap berpindah.
        pager.setOffscreenPageLimit(PagerAdapter.PAGE_COUNT);
        pager.setUserInputEnabled(true);
        // Posisi awal; tab terakhir dipulihkan lewat onRestoreInstanceState.
        pager.setCurrentItem(0, false);

        nav.setOnItemSelectedListener(item -> {
            int idx = indexOf(item.getItemId());
            if (idx >= 0 && pager.getCurrentItem() != idx) {
                pager.setCurrentItem(idx, true);
            }
            return true;
        });

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position < 0 || position >= TABS.length) return;
                int id = TABS[position];
                // setSelectedItemId akan memanggil listener di atas, tetapi
                // currentItem sudah sama sehingga tidak terjadi loop.
                if (nav.getSelectedItemId() != id) nav.setSelectedItemId(id);
            }
        });

        if (savedInstanceState != null) {
            // Dipanggil lagi setelah onRestoreInstanceState; di sini hanya
            // memastikan posisi tidak kosong.
            pager.setCurrentItem(savedInstanceState.getInt(KEY_TAB, 0), false);
        } else {
            applyExtraTab(getIntent());
        }
    }

    /**
     * Tab aktif disimpan terpisah dari state bawaan ViewPager2 karena state
     * bawaannya bisa ter-reset saat Activity dibuat ulang (misal ganti tema).
     */
    @Override
    protected void onRestoreInstanceState(@NonNull android.os.Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        ViewPager2 pager = findViewById(R.id.pager);
        if (pager == null) return;
        int tab = savedInstanceState.getInt(KEY_TAB, 0);
        if (tab >= 0 && tab < PagerAdapter.PAGE_COUNT && pager.getCurrentItem() != tab) {
            pager.setCurrentItem(tab, false);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull android.os.Bundle outState) {
        super.onSaveInstanceState(outState);
        ViewPager2 pager = findViewById(R.id.pager);
        if (pager != null) outState.putInt(KEY_TAB, pager.getCurrentItem());
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyExtraTab(intent);
    }

    /** Buka tab titipan dari Intent (CLEAR_TOP dari SeriesActivity). */
    private void applyExtraTab(android.content.Intent intent) {
        if (intent == null || !intent.hasExtra(EXTRA_TAB)) return;
        ViewPager2 pager = findViewById(R.id.pager);
        int tab = intent.getIntExtra(EXTRA_TAB, -1);
        if (pager != null && tab >= 0 && tab < PagerAdapter.PAGE_COUNT) {
            pager.setCurrentItem(tab, false);
        }
    }

    private int indexOf(int id) {
        for (int i = 0; i < TABS.length; i++) {
            if (TABS[i] == id) return i;
        }
        return -1;
    }

    /** Layar penuh imersif: sembunyikan status bar + pil navigasi. Muncul
     *  sementara hanya saat di-swipe (sticky immersive). Dipanggil ulang saat
     *  fokus kembali karena swipe sistematik menampilkannya lagi. */
    private void hideSystemBars() {
        android.view.Window w = getWindow();
        if (w == null) return;
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w, false);
        androidx.core.view.WindowInsetsControllerCompat c =
                androidx.core.view.WindowCompat.getInsetsController(
                        w, w.getDecorView());
        if (c == null) return;
        c.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());
        c.setSystemBarsBehavior(androidx.core.view.WindowInsetsControllerCompat
                .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }
}
