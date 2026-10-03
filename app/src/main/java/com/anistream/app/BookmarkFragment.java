package com.anistream.app;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tab "Tersimpan": grid bookmark dengan pengurutan (Abjad / Ditambahkan /
 * Diperbarui), lencana "Ada Episode Baru!", hapus cepat per kartu, dan
 * bersihkan seluruh. Data lokal dari {@link BookmarkStore} — tanpa akun.
 */
public class BookmarkFragment extends Fragment {

    private BookmarkStore store;
    private BookmarkAdapter adapter;
    private SwipeRefreshLayout refresh;
    private TextView totalText, empty;
    private View emptyBox;
    private ProgressBar progress;
    private Chip sortAlpha, sortAdded, sortUpdated;
    private int sortMode = BookmarkStore.SORT_ADDED;
    /** Filter kategori: CAT_ALL_ID = semua; chip dibangun dari kode. */
    private View catScroll;
    private LinearLayout catRow;
    private long selectedCat = BookmarkStore.CAT_ALL_ID;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup grp,
                             @Nullable Bundle st) {
        View v = inf.inflate(R.layout.fragment_bookmark, grp, false);

        refresh = v.findViewById(R.id.refresh);
        progress = v.findViewById(R.id.progress);
        emptyBox = v.findViewById(R.id.emptyBox);
        empty = v.findViewById(R.id.empty);
        totalText = v.findViewById(R.id.uiTotal);
        ImageView emptyIcon = v.findViewById(R.id.emptyIcon);
        RecyclerView rv = v.findViewById(R.id.recycler);

        emptyIcon.setImageResource(R.drawable.ic_bookmark);

        store = new BookmarkStore(requireContext());
        adapter = new BookmarkAdapter(new BookmarkAdapter.OnClick() {
            @Override public void onOpen(BookmarkItem item) { open(item); }
            @Override public void onDelete(BookmarkItem item) {
                store.remove(item.id);
                reload();
            }
        });

        rv.setLayoutManager(grid());
        rv.setAdapter(adapter);

        sortAlpha = v.findViewById(R.id.uiSortAlpha);
        sortAdded = v.findViewById(R.id.uiSortAdded);
        sortUpdated = v.findViewById(R.id.uiSortUpdated);
        catScroll = v.findViewById(R.id.catScroll);
        catRow = v.findViewById(R.id.catRow);
        sortAlpha.setOnClickListener(x -> pickSort(BookmarkStore.SORT_ALPHA));
        sortAdded.setOnClickListener(x -> pickSort(BookmarkStore.SORT_ADDED));
        sortUpdated.setOnClickListener(x -> pickSort(BookmarkStore.SORT_UPDATED));

        bindClearButton(v);

        refresh.setColorSchemeColors(
                MaterialColors.getColor(v, com.google.android.material.R.attr.colorPrimary),
                MaterialColors.getColor(v, com.google.android.material.R.attr.colorSecondary));
        refresh.setOnRefreshListener(this::reload);

        syncSortUi();
        reload();
        return v;
    }

    private void bindClearButton(View v) {
        MaterialButton btn = v.findViewById(R.id.btnClear);
        btn.setOnClickListener(x -> confirmClear());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (store != null) reload();
    }

    /** Jumlah kolom grid mengikuti orientasi layar (3 potret, 5 lanskap). */
    private GridLayoutManager grid() {
        int orientation = getResources().getConfiguration().orientation;
        int span = (orientation == Configuration.ORIENTATION_LANDSCAPE) ? 5 : 3;
        return new GridLayoutManager(requireContext(), span);
    }

    private void pickSort(int mode) {
        sortMode = mode;
        syncSortUi();
        reload();
    }

    /** Sorot chip yang terpilih sesuai mode urutan aktif. */
    private void syncSortUi() {
        if (sortAlpha == null) return;
        sortAlpha.setChecked(sortMode == BookmarkStore.SORT_ALPHA);
        sortAdded.setChecked(sortMode == BookmarkStore.SORT_ADDED);
        sortUpdated.setChecked(sortMode == BookmarkStore.SORT_UPDATED);
    }

    private void reload() {
        if (store == null || !isAdded()) return;

        List<BookmarkItem> items = store.all(sortMode);
        // Saring sisi klien berdasar kategori terpilih.
        List<BookmarkItem> shown = new ArrayList<>();
        for (BookmarkItem b : items) {
            if (b == null) continue;
            if (selectedCat != BookmarkStore.CAT_ALL_ID && b.catId != selectedCat) continue;
            shown.add(b);
        }
        // Peta nama kategori untuk subjudul kartu.
        Map<Long, String> names = new HashMap<>();
        List<BookmarkStore.Cat> cats = store.cats();
        for (BookmarkStore.Cat c : cats) {
            if (c != null && c.id != BookmarkStore.CAT_ALL_ID) names.put(c.id, c.name);
        }
        adapter.setCatNames(names);
        adapter.submit(shown);
        totalText.setText(getString(R.string.bookmark_total, shown.size()));

        boolean none = shown.isEmpty();
        emptyBox.setVisibility(none ? View.VISIBLE : View.GONE);
        progress.setVisibility(View.GONE);
        refresh.setRefreshing(false);

        buildCatChips(cats);
    }

    /**
     * Chip filter kategori ("Semua" + tiap kategori). Baris disembunyikan
     * bila hanya ada chip "Semua".
     */
    private void buildCatChips(List<BookmarkStore.Cat> cats) {
        if (catScroll == null || catRow == null || getContext() == null) return;
        if (cats == null) cats = store.cats();
        // Kategori yang dipilih bisa saja terhapus — kembalikan ke Semua.
        boolean known = selectedCat == BookmarkStore.CAT_ALL_ID;
        for (BookmarkStore.Cat c : cats) {
            if (c != null && c.id == selectedCat) { known = true; break; }
        }
        if (!known) selectedCat = BookmarkStore.CAT_ALL_ID;

        catRow.removeAllViews();
        if (cats.size() <= 1) {
            catScroll.setVisibility(View.GONE);
            return;
        }
        catScroll.setVisibility(View.VISIBLE);
        for (final BookmarkStore.Cat c : cats) {
            if (c == null) continue;
            Chip chip = new Chip(requireContext());
            chip.setText(c.id == BookmarkStore.CAT_ALL_ID
                    ? getString(R.string.cat_all) : c.name);
            chip.setCheckable(true);
            chip.setChecked(c.id == selectedCat);
            chip.setMinHeight(0);
            chip.setTextSize(12);
            chip.setChipBackgroundColor(androidx.core.content.ContextCompat
                    .getColorStateList(requireContext(), R.color.ui_chip_fill));
            chip.setTextColor(androidx.core.content.ContextCompat
                    .getColorStateList(requireContext(), R.color.ui_chip_text));
            chip.setChipStrokeColor(androidx.core.content.ContextCompat
                    .getColorStateList(requireContext(), R.color.ui_chip_stroke));
            chip.setChipStrokeWidth(dp(1));
            chip.setChipCornerRadius(dp(99));
            chip.setEnsureMinTouchTargetSize(false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(x -> {
                selectedCat = c.id;
                reload();
            });
            catRow.addView(chip);
        }
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void open(BookmarkItem item) {
        if (getContext() == null) return;
        Intent i = new Intent(requireContext(), SeriesActivity.class);
        i.putExtra("url", item.seriesUrl);
        i.putExtra("title", item.title);
        i.putExtra("thumb", item.thumb);
        startActivity(i);
    }

    private void confirmClear() {
        if (getContext() == null || store == null) return;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clear_bookmark)
                .setMessage(R.string.clear_bookmark_msg)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    store.clear();
                    reload();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
