package com.anistream.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter riwayat per JUDUL anime: poster, judul, episode terakhir ditonton
 * + menitnya, progres bar, waktu tonton, dan aksi Lanjutkan / Episode
 * terdekat / Hapus satu judul.
 */
public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {

    public interface OnClick {
        void onPlay(HistoryItem item);
        void onDelete(HistoryGroup group);
        void onEpisodes(HistoryGroup group);
    }

    private final List<HistoryGroup> data = new ArrayList<>();
    private final OnClick click;

    public HistoryAdapter(OnClick click) { this.click = click; }

    public void submit(List<HistoryGroup> groups) {
        data.clear();
        if (groups != null) data.addAll(groups);
        notifyDataSetChanged();
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_history, p, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int i) {
        final HistoryGroup g = data.get(i);
        final HistoryItem it = g.latest();
        if (it == null) return;

        h.title.setText(g.title.isEmpty() ? it.epTitle : g.title);
        h.ep.setText(it.epTitle);

        if (it.durMs > 0) {
            // Posisi / durasi dalam format jam video: 12:34 / 23:45.
            h.progressText.setText(Utils.clock(it.posMs)
                    + " / " + Utils.clock(it.durMs)
                    + (it.finished() ? " · selesai" : ""));
            h.progressBar.setProgress(it.percent());
            h.progressBar.setVisibility(View.VISIBLE);
            h.progressText.setVisibility(View.VISIBLE);
        } else {
            h.progressText.setText("Belum ada data durasi");
            h.progressBar.setVisibility(View.GONE);
        }

        h.time.setText("Terakhir ditonton: " + Utils.timeAgo(it.watchedAt)
                + " · " + g.episodeCount() + " episode");
        ImageLoader.load(g.thumb.isEmpty() ? it.thumb : g.thumb, h.thumb);

        h.itemView.setOnClickListener(v -> { if (click != null) click.onPlay(it); });
        h.btnResume.setOnClickListener(v -> { if (click != null) click.onPlay(it); });
        h.btnDelete.setOnClickListener(v -> { if (click != null) click.onDelete(g); });
        h.btnEpisodes.setOnClickListener(v -> { if (click != null) click.onEpisodes(g); });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextView title, ep, progressText, time;
        final ProgressBar progressBar;
        final MaterialButton btnResume, btnDelete, btnEpisodes;
        VH(@NonNull View v) {
            super(v);
            thumb = v.findViewById(R.id.thumb);
            title = v.findViewById(R.id.title);
            ep = v.findViewById(R.id.ep);
            progressText = v.findViewById(R.id.progressText);
            progressBar = v.findViewById(R.id.progressBar);
            time = v.findViewById(R.id.time);
            btnResume = v.findViewById(R.id.btnResume);
            btnDelete = v.findViewById(R.id.btnDelete);
            btnEpisodes = v.findViewById(R.id.btnEpisodes);
        }
    }
}
