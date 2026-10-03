package com.anistream.app;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;

import java.util.List;

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
        adapter.submit(items);
        totalText.setText(getString(R.string.bookmark_total, items.size()));

        boolean none = items.isEmpty();
        emptyBox.setVisibility(none ? View.VISIBLE : View.GONE);
        progress.setVisibility(View.GONE);
        refresh.setRefreshing(false);
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
        new AlertDialog.Builder(requireContext())
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
