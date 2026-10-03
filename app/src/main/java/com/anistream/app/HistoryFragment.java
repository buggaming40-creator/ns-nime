package com.anistream.app;

import android.content.Intent;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * Tab "Riwayat": daftar tontonan lengkap dengan menit yang sudah ditonton,
 * total akumulasi, progres bar, aksi lanjutkan dan bersihkan.
 */
public class HistoryFragment extends Fragment {

    private HistoryAdapter adapter;
    private HistoryStore store;
    private TextView empty;
    private TextView totalText;
    private View emptyBox;
    private ProgressBar progress;
    private SwipeRefreshLayout refresh;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup grp,
                             @Nullable Bundle st) {
        View v = inf.inflate(R.layout.fragment_history, grp, false);

        refresh = v.findViewById(R.id.refresh);
        progress = v.findViewById(R.id.progress);
        emptyBox = v.findViewById(R.id.emptyBox);
        empty = v.findViewById(R.id.empty);
        totalText = v.findViewById(R.id.totalText);
        ImageView emptyIcon = v.findViewById(R.id.emptyIcon);
        RecyclerView rv = v.findViewById(R.id.recycler);
        MaterialButton btnClear = v.findViewById(R.id.btnClear);

        store = new HistoryStore(requireContext());
        emptyIcon.setImageResource(R.drawable.ic_history);

        adapter = new HistoryAdapter(new HistoryAdapter.OnClick() {
            @Override public void onPlay(HistoryItem item) { play(item); }

            @Override public void onDelete(HistoryItem item) {
                store.delete(item.id);
                refresh();
            }
        });

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        btnClear.setOnClickListener(x -> confirmClear());

        refresh.setColorSchemeColors(
                com.google.android.material.color.MaterialColors.getColor(
                        v, com.google.android.material.R.attr.colorPrimary),
                com.google.android.material.color.MaterialColors.getColor(
                        v, com.google.android.material.R.attr.colorSecondary));
        refresh.setOnRefreshListener(this::refresh);

        refresh();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (store != null) refresh();
    }

    private void refresh() {
        if (store == null || !isAdded()) return;
        if (getView() == null) return;

        List<HistoryItem> items = store.all();
        adapter.submit(items);

        long totalMs = 0;
        for (HistoryItem h : items) totalMs += h.posMs;

        totalText.setText(items.isEmpty()
                ? getString(R.string.history_total_none)
                : getString(R.string.history_total, Utils.minutes(totalMs), items.size()));

        boolean none = items.isEmpty();
        emptyBox.setVisibility(none ? View.VISIBLE : View.GONE);
        if (none) empty.setText(R.string.empty_history);
        progress.setVisibility(View.GONE);
        refresh.setRefreshing(false);
    }

    private void play(HistoryItem item) {
        if (getContext() == null) return;
        Intent i = new Intent(requireContext(), PlayerActivity.class);
        i.putExtra("epUrl", item.epUrl);
        i.putExtra("epTitle", item.epTitle);
        i.putExtra("title", item.title);
        i.putExtra("thumb", item.thumb);
        i.putExtra("seriesUrl", item.seriesUrl);
        i.putExtra("pos", item.finished() ? 0L : item.posMs);
        startActivity(i);
    }

    private void confirmClear() {
        if (getContext() == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.clear_history)
                .setMessage(R.string.clear_history_msg)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    store.clear();
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
