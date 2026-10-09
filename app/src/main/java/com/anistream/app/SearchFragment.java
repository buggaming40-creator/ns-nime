package com.anistream.app;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * Tab "Cari": kolom pencarian, riwayat kata kunci, dan hasil grid.
 */
public class SearchFragment extends Fragment {

    /** Query titipan dari tab lain (mis. chip genre di Beranda). */
    private static String pendingQuery;

    /** Titip query lalu pindah ke tab Cari — dijalankan saat tab tampil. */
    public static void requestQuery(String q) {
        pendingQuery = q == null ? "" : q.trim();
    }

    private AnimeAdapter adapter;
    private EditText input;
    private ProgressBar progress;
    private View emptyBox;
    private TextView empty, searchCount;
    private LinearLayout recentsBox;
    private LinearLayout recentsList;
    private LinearLayout genreRow;
    private View btnClear;
    private boolean searched;

    /** Penjaga basi: hasil async dengan seq lama diabaikan. */
    private int searchSeq;
    /** Debounce pencarian otomatis 600 mdtk setelah teks berubah. */
    private final Handler liveHandler = new Handler();
    private final Runnable liveRun = new Runnable() {
        @Override public void run() {
            if (input == null || !isAdded()) return;
            String q = input.getText().toString().trim();
            if (q.length() >= 3) doSearch();
        }
    };

    /** Genre populer di baris Cari; dialog "Lainnya" memuat 16 genre. */
    private static final String[] GENRE_POPULAR = {
            "Action", "Adventure", "Comedy", "Romance",
            "Fantasy", "Drama", "Horror", "Mystery"};
    private static final String[] GENRE_ALL = {
            "Action", "Adventure", "Comedy", "Romance",
            "Fantasy", "Drama", "Horror", "Mystery",
            "Avant Garde", "Award Winning", "Shoujo", "Shounen",
            "Slice of Life", "Sports", "Supernatural", "Suspense"};

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup grp,
                             @Nullable Bundle st) {
        View v = inf.inflate(R.layout.fragment_search, grp, false);

        input = v.findViewById(R.id.input);
        btnClear = v.findViewById(R.id.btnClear);
        MaterialButton btn = v.findViewById(R.id.btnSearch);
        progress = v.findViewById(R.id.progress);
        emptyBox = v.findViewById(R.id.emptyBox);
        empty = v.findViewById(R.id.empty);
        ImageView emptyIcon = v.findViewById(R.id.emptyIcon);
        recentsBox = v.findViewById(R.id.recentsBox);
        recentsList = v.findViewById(R.id.recentsList);
        searchCount = v.findViewById(R.id.searchCount);
        genreRow = v.findViewById(R.id.genreRow);
        RecyclerView rv = v.findViewById(R.id.recycler);

        // Hapus seluruh riwayat pencarian (kunci disimpan di Prefs.recents).
        MaterialButton btnClearRecents = v.findViewById(R.id.uiClearRecents);
        btnClearRecents.setOnClickListener(x -> {
            if (getContext() == null) return;
            Prefs.clearRecents(requireContext());
            showRecents();
        });

        adapter = new AnimeAdapter(this::open);
        rv.setLayoutManager(grid());
        rv.setAdapter(adapter);

        emptyIcon.setImageResource(R.drawable.ic_search);
        empty.setText(R.string.empty_search);

        btn.setOnClickListener(x -> doSearch());
        btnClear.setOnClickListener(x -> {
            input.setText("");
            btnClear.setVisibility(View.GONE);
            showRecents();
        });
        input.setOnEditorActionListener((tv, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch();
                return true;
            }
            return false;
        });
        input.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void onTextChanged(CharSequence s) {
                btnClear.setVisibility(s != null && s.length() > 0 ? View.VISIBLE : View.GONE);
                // Pencarian otomatis: debounce 600 mdtk bila >=3 huruf;
                // teks dikosongkan = kembali ke awal (recents).
                liveHandler.removeCallbacks(liveRun);
                int len = s == null ? 0 : s.toString().trim().length();
                if (len == 0) {
                    searched = false;
                    adapter.submit(null);
                    hideCount();
                    showRecents();
                } else if (len >= 3) {
                    liveHandler.postDelayed(liveRun, 600);
                }
            }
        });

        buildGenreRow();
        showRecents();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Jalankan titipan query dari Beranda (chip genre) sekali saja.
        if (pendingQuery != null && !pendingQuery.isEmpty()
                && input != null && isAdded()) {
            input.setText(pendingQuery);
            pendingQuery = "";
            doSearch();
            // Teks terisi memicu debounce — matikan agar tidak cari dobel.
            liveHandler.removeCallbacks(liveRun);
        }
    }

    @Override
    public void onDestroyView() {
        liveHandler.removeCallbacks(liveRun);
        super.onDestroyView();
    }

    private GridLayoutManager grid() {
        int orientation = getResources().getConfiguration().orientation;
        int span = (orientation == Configuration.ORIENTATION_LANDSCAPE) ? 5 : 3;
        return new GridLayoutManager(requireContext(), span);
    }

    // -------------------------------------------------------- pencarian

    private void doSearch() {
        final String q = input.getText().toString().trim();
        if (q.isEmpty()) return;
        if (getContext() == null) return;

        InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);

        progress.setVisibility(View.VISIBLE);
        emptyBox.setVisibility(View.GONE);

        final int seq = ++searchSeq;
        Async.go(() -> Sources.search(q), new Async.Done<List<AnimeItem>>() {
            @Override public void ok(List<AnimeItem> items) {
                // Hasil basi (pencarian lebih baru sudah jalan) — abaikan.
                if (!isAdded() || seq != searchSeq) return;
                progress.setVisibility(View.GONE);
                adapter.submit(items);
                searched = true;
                if (!items.isEmpty()) Prefs.addRecent(requireContext(), q);
                showEmpty(items.isEmpty() ? R.string.empty_search2 : 0);
                showCount(items.isEmpty() ? 0 : items.size(), q);
                showRecents();
            }

            @Override public void err(Throwable t) {
                if (!isAdded() || seq != searchSeq) return;
                progress.setVisibility(View.GONE);
                hideCount();
                showEmpty(R.string.err_net);
            }
        });
    }

    /** Tampilkan "N hasil untuk 'q'"; sembunyi bila tidak ada hasil. */
    private void showCount(int n, String q) {
        if (searchCount == null) return;
        if (n <= 0) {
            searchCount.setVisibility(View.GONE);
            return;
        }
        searchCount.setText(getString(R.string.search_count_fmt, n, q));
        searchCount.setVisibility(View.VISIBLE);
    }

    private void hideCount() {
        if (searchCount != null) searchCount.setVisibility(View.GONE);
    }

    // -------------------------------------------------------- indeks genre

    /**
     * Baris chip genre populer + "+ Lainnya" (dialog 16 genre). Ketuk chip
     * langsung menjalankan pencarian genre tersebut.
     */
    private void buildGenreRow() {
        if (genreRow == null || getContext() == null) return;
        genreRow.removeAllViews();
        for (final String g : GENRE_POPULAR) {
            genreRow.addView(genreChip(g, () -> genreSearch(g)));
        }
        // Chip terakhir membuka dialog 16 genre.
        genreRow.addView(genreChip("+ Lainnya", this::showMoreGenres));
    }

    /** Satu pil genre untuk baris jelajah. */
    private View genreChip(String label, final Runnable action) {
        MaterialButton chip = new MaterialButton(requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        chip.setText(label);
        chip.setAllCaps(false);
        chip.setTextSize(12);
        chip.setMinHeight(0);
        chip.setInsetTop(0);
        chip.setInsetBottom(0);
        chip.setCornerRadius(40);
        chip.setStrokeColor(androidx.core.content.ContextCompat.getColorStateList(
                requireContext(), R.color.ui_chip_stroke));
        chip.setStrokeWidth(dp(1));
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(16);
        chip.setLayoutParams(lp);
        chip.setOnClickListener(x -> { if (action != null) action.run(); });
        return chip;
    }

    /** Isi kolom dengan genre lalu cari langsung. */
    private void genreSearch(String genre) {
        if (input == null || !isAdded()) return;
        liveHandler.removeCallbacks(liveRun);
        input.setText(genre);
        input.setSelection(genre.length());
        doSearch();
    }

    /** Dialog 16 genre netral — pilih = cari langsung. */
    private void showMoreGenres() {
        if (getContext() == null) return;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.genre_browse)
                .setItems(GENRE_ALL, (d, which) -> genreSearch(GENRE_ALL[which]))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showEmpty(int msg) {
        if (msg == 0) {
            emptyBox.setVisibility(View.GONE);
            return;
        }
        emptyBox.setVisibility(View.VISIBLE);
        empty.setText(msg);
    }

    /** Tampilkan daftar kata kunci terakhir hanya sebelum ada hasil. */
    private void showRecents() {
        if (recentsBox == null || recentsList == null || getContext() == null) return;
        List<String> recents = Prefs.recents(requireContext());

        boolean show = !recents.isEmpty() && !searched;
        recentsBox.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;

        recentsList.removeAllViews();
        for (final String q : recents) {
            MaterialButton chip = new MaterialButton(requireContext(), null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            chip.setText(q);
            chip.setAllCaps(false);
            chip.setTextSize(12);
            chip.setMinHeight(0);
            chip.setInsetTop(0);
            chip.setInsetBottom(0);
            chip.setCornerRadius(40);
            // Outline pil mengikuti aksen tema aktif (lihat res/color/ui_chip_stroke).
            chip.setStrokeColor(androidx.core.content.ContextCompat.getColorStateList(
                    requireContext(), R.color.ui_chip_stroke));
            chip.setStrokeWidth(dp(1));
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(16);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(x -> {
                input.setText(q);
                input.setSelection(q.length());
                doSearch();
            });
            recentsList.addView(chip);
        }
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
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
