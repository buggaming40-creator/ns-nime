package com.anistream.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Adapter daftar episode (baris / grid). */
public class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.VH> {

    public interface OnClick { void onPick(EpisodeItem item); }

    private static final int TYPE_LIST = 0;
    private static final int TYPE_GRID = 1;

    private final List<EpisodeItem> data = new ArrayList<>();
    private final OnClick click;
    private boolean grid;

    public EpisodeAdapter(OnClick click) { this.click = click; }

    /** Ganti mode tampil grid (sel pil nomor) / baris (kartu lengkap). */
    public void setGrid(boolean grid) {
        if (this.grid == grid) return;
        this.grid = grid;
        notifyDataSetChanged();
    }

    public boolean isGrid() { return grid; }

    public void submit(List<EpisodeItem> items) {
        data.clear();
        if (items != null) data.addAll(items);
        notifyDataSetChanged();
    }

    @Override public int getItemViewType(int position) {
        return grid ? TYPE_GRID : TYPE_LIST;
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        int layout = (t == TYPE_GRID) ? R.layout.item_episode_grid : R.layout.item_episode;
        View v = LayoutInflater.from(p.getContext()).inflate(layout, p, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int i) {
        final EpisodeItem it = data.get(i);
        h.num.setText(it.num.isEmpty() ? "EP" : it.num);
        if (h.title != null) h.title.setText(it.title);
        h.date.setText(it.date);
        // Baris daftar tetap seperti semula; sel grid menyembunyikan tanggal kosong.
        boolean emptyDate = it.date == null || it.date.isEmpty();
        if (h.title == null) h.date.setVisibility(emptyDate ? View.GONE : View.VISIBLE);
        else h.date.setVisibility(View.VISIBLE);
        h.itemView.setOnClickListener(v -> { if (click != null) click.onPick(it); });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView num, title, date;
        VH(@NonNull View v) {
            super(v);
            num = v.findViewById(R.id.num);
            // Layout grid hanya memuat num + date (tanpa judul).
            TextView t = null;
            try { t = v.findViewById(R.id.title); } catch (Throwable ignored) {}
            title = t;
            date = v.findViewById(R.id.date);
        }
    }
}
