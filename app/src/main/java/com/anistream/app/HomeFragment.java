package com.anistream.app;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tab "Terbaru" (beranda):
 *   H-1 header aplikasi (logo, nama, tagline, tombol cari & setelan)
 *   H-2 banner carousel unggulan — 5 rilis terbaru pertama, auto-scroll + dot
 *   H-3 chip filter Semua/Anime/Donghua (disaring klien dari `AnimeItem.meta`)
 *   H-4 seksi "Rilis Terbaru" + grid poster (3 kolom potret, 5 lanskap)
 *   H-5 seksi "Sedang Tayang" — best-effort dari `loadSeries().status`
 *   H-6 seksi "Top Rating" — urutan situs dari `latest()`, tanpa angka rating
 *   H-7 keadaan kosong/muat berikon
 *
 * Seksi tanpa data tidak pernah ditampilkan (tidak ada data karangan).
 */
public class HomeFragment extends Fragment {

    /** Jumlah kartu yang diperiksa untuk seksi Sedang Tayang (hemat jaringan). */
    private static final int PROBE_LIMIT = 6;

    private HomeAdapter adapter;
    private BannerAdapter bannerAdapter;
    private SwipeRefreshLayout refresh;
    private ProgressBar progress;
    private View emptyBox;
    private TextView empty;
    private ViewPager2 banner;
    private LinearLayout dots;
    private View bannerBox;

    private final List<AnimeItem> all = new ArrayList<>();
    private boolean loadedOnce;

    private final Handler auto = new Handler(Looper.getMainLooper());

    /** Auto-scroll banner: lanjut ke halaman berikutnya, berputar di ujung. */
    private final Runnable bannerTick = new Runnable() {
        @Override public void run() {
            int n = bannerAdapter == null ? 0 : bannerAdapter.getItemCount();
            if (banner != null && n > 1) {
                banner.setCurrentItem((banner.getCurrentItem() + 1) % n, true);
            }
            auto.postDelayed(this, 4500);
        }
    };

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup grp,
                             @Nullable Bundle st) {
        View v = inf.inflate(R.layout.fragment_home, grp, false);

        refresh = v.findViewById(R.id.refresh);
        progress = v.findViewById(R.id.progress);
        emptyBox = v.findViewById(R.id.emptyBox);
        empty = v.findViewById(R.id.empty);
        ImageView emptyIcon = v.findViewById(R.id.emptyIcon);
        RecyclerView rv = v.findViewById(R.id.recycler);

        emptyIcon.setImageResource(R.drawable.ic_empty_state);

        // ---- H-4: grid + baris horizontal ----
        adapter = new HomeAdapter(this::open);
        final GridLayoutManager glm = grid();
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                return adapter.isFullSpan(position) ? glm.getSpanCount() : 1;
            }
        });
        rv.setLayoutManager(glm);
        rv.setAdapter(adapter);

        // ---- H-2: banner ----
        banner = v.findViewById(R.id.uiBanner);
        dots = v.findViewById(R.id.uiDots);
        bannerBox = v.findViewById(R.id.uiBannerBox);
        bannerAdapter = new BannerAdapter(this::open);
        banner.setAdapter(bannerAdapter);
        banner.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) { buildDots(position); }
        });

        // ---- H-1: tombol header ----
        View search = v.findViewById(R.id.uiSearchButton);
        search.setOnClickListener(x -> gotoTab(PagerAdapter.PAGE_SEARCH));
        View btnSettings = v.findViewById(R.id.btnSettings);
        btnSettings.setOnClickListener(x -> gotoTab(PagerAdapter.PAGE_SETTINGS));

        refresh.setColorSchemeColors(
                MaterialColors.getColor(v, com.google.android.material.R.attr.colorPrimary),
                MaterialColors.getColor(v, com.google.android.material.R.attr.colorSecondary));
        refresh.setOnRefreshListener(this::load);

        load();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (bannerAdapter != null && bannerAdapter.getItemCount() > 0) {
            auto.removeCallbacks(bannerTick);
            auto.postDelayed(bannerTick, 4500);
        }
    }

    @Override
    public void onPause() {
        auto.removeCallbacks(bannerTick);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        auto.removeCallbacks(bannerTick);
        super.onDestroyView();
    }

    /** Jumlah kolom grid mengikuti orientasi layar. */
    private GridLayoutManager grid() {
        int orientation = getResources().getConfiguration().orientation;
        int span = (orientation == Configuration.ORIENTATION_LANDSCAPE) ? 5 : 3;
        return new GridLayoutManager(requireContext(), span);
    }

    // ------------------------------------------------------------- muat data

    private void load() {
        if (!loadedOnce && refresh != null) {
            progress.setVisibility(View.VISIBLE);
            emptyBox.setVisibility(View.GONE);
        }
        Async.go(Oploverz::latest, new Async.Done<List<AnimeItem>>() {
            @Override public void ok(List<AnimeItem> items) {
                if (!isAdded() || adapter == null) return;
                loadedOnce = true;
                refresh.setRefreshing(false);
                progress.setVisibility(View.GONE);

                all.clear();
                if (items != null) all.addAll(items);

                adapter.setLatest(all);
                adapter.setTop(topOf(all));
                setupBanner(all);
                syncEmpty();

                if (!all.isEmpty()) probeOngoing(all);
            }

            @Override public void err(Throwable t) {
                if (!isAdded() || adapter == null) return;
                loadedOnce = true;
                refresh.setRefreshing(false);
                progress.setVisibility(View.GONE);
                all.clear();
                adapter.setLatest(new ArrayList<>());
                adapter.setTop(new ArrayList<>());
                setupBanner(new ArrayList<>());
                showEmpty(true, R.string.err_net);
            }
        });
    }

    /** H-6: urutan situs (bukan angka rating) — ambil beberapa judul teratas. */
    private List<AnimeItem> topOf(List<AnimeItem> items) {
        List<AnimeItem> out = new ArrayList<>();
        int n = Math.min(items.size(), 8);
        for (int i = 0; i < n; i++) out.add(items.get(i));
        return out;
    }

    /** H-5: best-effort — cek status tiap halaman series; bila gagal, seksi disembunyikan. */
    private void probeOngoing(final List<AnimeItem> items) {
        final List<AnimeItem> probe = new ArrayList<>();
        for (int i = 0; i < items.size() && i < PROBE_LIMIT; i++) probe.add(items.get(i));

        Async.go(() -> {
            List<AnimeItem> found = new ArrayList<>();
            for (AnimeItem a : probe) {
                try {
                    Oploverz.Series s = Oploverz.loadSeriesLite(a.url);
                    String st = s == null || s.status == null ? "" : s.status;
                    if (st.toLowerCase(Locale.ROOT).contains("ongoing")) found.add(a);
                } catch (Throwable ignored) {
                    // Gagal memuat satu judul bukan alasan membatalkan seluruh seksi.
                }
                if (found.size() >= 5) break;
            }
            return found;
        }, new Async.Done<List<AnimeItem>>() {
            @Override public void ok(List<AnimeItem> result) {
                if (isAdded() && adapter != null) adapter.setOngoing(result);
            }

            @Override public void err(Throwable t) {
                if (isAdded() && adapter != null) adapter.setOngoing(null);
            }
        });
    }

    // --------------------------------------------------------- keadaan kosong

    private void syncEmpty() {
        if (all.isEmpty()) {
            showEmpty(true, loadedOnce ? R.string.empty_latest : 0);
        } else {
            showEmpty(false, 0);
        }
    }

    private void showEmpty(boolean show, int msg) {
        if (emptyBox == null) return;
        emptyBox.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show && msg != 0) empty.setText(msg);
    }

    // -------------------------------------------------------------- banner

    private void setupBanner(List<AnimeItem> items) {
        if (banner == null || getContext() == null) return;
        List<AnimeItem> top5 = new ArrayList<>();
        for (int i = 0; i < items.size() && i < 5; i++) top5.add(items.get(i));

        bannerAdapter.submit(top5);
        if (bannerBox != null) {
            bannerBox.setVisibility(top5.isEmpty() ? View.GONE : View.VISIBLE);
        }
        buildDots(0);

        if (!top5.isEmpty()) {
            auto.removeCallbacks(bannerTick);
            auto.postDelayed(bannerTick, 4500);
        }
    }

    /** Susun indikator dot sesuai jumlah halaman banner dan halaman aktif. */
    private void buildDots(int active) {
        if (dots == null || getContext() == null) return;
        int count = bannerAdapter == null ? 0 : bannerAdapter.getItemCount();
        dots.removeAllViews();
        if (count <= 1) return;

        int accent = MaterialColors.getColor(dots, com.google.android.material.R.attr.colorPrimary);
        for (int i = 0; i < count; i++) {
            boolean on = i == active;
            View d = new View(requireContext());
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.RECTANGLE);
            g.setCornerRadius(dp(3));
            g.setColor(on ? accent : 0x80FFFFFF);
            d.setBackground(g);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    on ? dp(18) : dp(6), dp(6));
            lp.setMargins(dp(3), 0, dp(3), 0);
            dots.addView(d, lp);
        }
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // -------------------------------------------------------------- umum

    /** Tombol header membuka tab lain tanpa menyentuh logika geser halaman. */
    private void gotoTab(int page) {
        if (getContext() == null) return;
        ViewPager2 pager = requireActivity().findViewById(R.id.pager);
        if (pager != null) pager.setCurrentItem(page, true);
    }

    private void open(AnimeItem item) {
        if (getContext() == null) return;
        Intent i = new Intent(requireContext(), SeriesActivity.class);
        i.putExtra("url", item.url);
        i.putExtra("title", item.title);
        i.putExtra("thumb", item.thumb);
        startActivity(i);
    }
}
