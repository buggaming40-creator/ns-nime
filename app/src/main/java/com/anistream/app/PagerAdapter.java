package com.anistream.app;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

/**
 * Lima halaman utama yang bisa digeser:
 * Terbaru (0), Cari (1), Tersimpan (2), Riwayat (3), Setelan (4).
 */
public class PagerAdapter extends FragmentStateAdapter {

    public static final int PAGE_HOME = 0;
    public static final int PAGE_SEARCH = 1;
    public static final int PAGE_SAVED = 2;
    public static final int PAGE_HISTORY = 3;
    public static final int PAGE_SETTINGS = 4;
    public static final int PAGE_COUNT = 5;

    public PagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    @NonNull @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case PAGE_SEARCH:   return new SearchFragment();
            case PAGE_SAVED:    return new BookmarkFragment();
            case PAGE_HISTORY:  return new HistoryFragment();
            case PAGE_SETTINGS: return new SettingsFragment();
            default:            return new HomeFragment();
        }
    }

    @Override
    public int getItemCount() {
        return PAGE_COUNT;
    }
}
