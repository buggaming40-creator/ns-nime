package com.anistream.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Tab "Riwayat": satu kartu per JUDUL anime (bukan per episode) — judul,
 * episode terakhir ditonton + menitnya, progres bar, total akumulasi, aksi
 * Lanjutkan, daftar Episode terdekat, dan hapus satu judul/bersihkan semua.
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

            @Override public void onDelete(HistoryGroup group) {
                store.deleteSeries(group.key);
                refresh();
            }

            @Override public void onEpisodes(HistoryGroup group) { showNearby(group); }
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
        List<HistoryGroup> groups = groupByTitle(items);
        adapter.submit(groups);

        long totalMs = 0;
        for (HistoryItem h : items) totalMs += h.posMs;

        totalText.setText(groups.isEmpty()
                ? getString(R.string.history_total_none)
                : getString(R.string.history_total, Utils.minutes(totalMs), groups.size()));

        boolean none = groups.isEmpty();
        emptyBox.setVisibility(none ? View.VISIBLE : View.GONE);
        if (none) empty.setText(R.string.empty_history);
        progress.setVisibility(View.GONE);
        refresh.setRefreshing(false);
    }

    /** Kelompokkan baris episode per judul (terbaru dulu — daftar sudah DESC). */
    static List<HistoryGroup> groupByTitle(List<HistoryItem> rows) {
        LinkedHashMap<String, HistoryGroup> map = new LinkedHashMap<>();
        if (rows == null) return new ArrayList<>();
        for (HistoryItem h : rows) {
            if (h == null) continue;
            String key = h.seriesUrl != null && !h.seriesUrl.isEmpty()
                    ? h.seriesUrl : (h.epUrl == null ? "" : h.epUrl);
            if (key.isEmpty()) continue;
            HistoryGroup g = map.get(key);
            if (g == null) {
                g = new HistoryGroup(key);
                map.put(key, g);
            }
            if (g.title.isEmpty() && !h.title.isEmpty()) g.title = h.title;
            if (g.thumb.isEmpty() && !h.thumb.isEmpty()) g.thumb = h.thumb;
            g.rows.add(h);
        }
        return new ArrayList<>(map.values());
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

    /**
     * Episode terdekat: ambil daftar episode judul ini, tampilkan jendela
     * ±2 sekitar episode terakhir ditonton (tanda ✓ = sudah ditonton,
     * ▸ = posisi terakhir). Ketuk = putar dengan daftar rel lengkap.
     */
    private void showNearby(final HistoryGroup group) {
        if (getContext() == null || group == null) return;
        final HistoryItem cur = group.latest();
        if (cur == null || cur.seriesUrl == null || cur.seriesUrl.isEmpty()) {
            Toast.makeText(requireContext(),
                    R.string.no_episodes, Toast.LENGTH_SHORT).show();
            return;
        }
        refresh.setRefreshing(true);
        final String seriesUrl = cur.seriesUrl;
        Async.go(() -> Sources.loadSeriesLite(seriesUrl),
                new Async.Done<Oploverz.Series>() {
                    @Override public void ok(Oploverz.Series s) {
                        if (!isAdded()) return;
                        refresh.setRefreshing(false);
                        if (s == null || s.episodes.isEmpty()) {
                            Toast.makeText(requireContext(),
                                    R.string.no_episodes, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        openNearbyDialog(group, s.episodes, cur.epUrl);
                    }

                    @Override public void err(Throwable t) {
                        if (!isAdded()) return;
                        refresh.setRefreshing(false);
                        Toast.makeText(requireContext(),
                                R.string.err_net, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void openNearbyDialog(final HistoryGroup group,
                                  final List<EpisodeItem> eps, String curUrl) {
        int idx = -1;
        for (int i = 0; i < eps.size(); i++) {
            if (curUrl != null && curUrl.equals(eps.get(i).url)) { idx = i; break; }
        }
        // URL tidak ketemu (situs berubah) — tampilkan beberapa teratas.
        int from = idx < 0 ? 0 : Math.max(0, idx - 2);
        int to = idx < 0 ? Math.min(eps.size(), 6) : Math.min(eps.size(), idx + 3);

        final List<EpisodeItem> window = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        for (int i = from; i < to; i++) {
            EpisodeItem e = eps.get(i);
            window.add(e);
            String name = (e.num == null || e.num.isEmpty())
                    ? e.title : ("Episode " + e.num);
            String mark = (idx >= 0 && i == idx) ? "▸ "
                    : (group.findByUrl(e.url) != null ? "✓ " : "");
            labels.add(mark + name);
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(group.title.isEmpty() ? getString(R.string.episode_list)
                        : group.title)
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    EpisodeItem pick = window.get(which);
                    HistoryItem seen = group.findByUrl(pick.url);
                    playFull(group, eps, pick,
                            seen != null && !seen.finished() ? seen.posMs : 0L);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Putar episode dari dialog dengan daftar rel lengkap + posisi resume. */
    private void playFull(HistoryGroup group, List<EpisodeItem> eps,
                          EpisodeItem pick, long pos) {
        if (getContext() == null) return;
        HistoryItem cur = group.latest();
        Intent i = new Intent(requireContext(), PlayerActivity.class);
        i.putExtra("epUrl", pick.url);
        i.putExtra("epTitle", pick.title);
        i.putExtra("title", cur != null ? cur.title : group.title);
        i.putExtra("thumb", cur != null ? cur.thumb : group.thumb);
        i.putExtra("seriesUrl", cur != null ? cur.seriesUrl : group.key);
        i.putExtra("pos", pos);

        int at = -1;
        ArrayList<String> urls = new ArrayList<>();
        ArrayList<String> titles = new ArrayList<>();
        for (int n = 0; n < eps.size(); n++) {
            urls.add(eps.get(n).url);
            titles.add(eps.get(n).title);
            if (pick.url != null && pick.url.equals(eps.get(n).url)) at = n;
        }
        if (at >= 0) {
            i.putExtra("epIndex", at);
            i.putStringArrayListExtra("epUrlList", urls);
            i.putStringArrayListExtra("epTitleList", titles);
        }
        startActivity(i);
    }

    private void confirmClear() {
        if (getContext() == null) return;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
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
