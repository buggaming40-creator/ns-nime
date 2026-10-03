package com.anistream.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Adapter daftar episode. */
public class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.VH> {

    public interface OnClick { void onPick(EpisodeItem item); }

    private final List<EpisodeItem> data = new ArrayList<>();
    private final OnClick click;

    public EpisodeAdapter(OnClick click) { this.click = click; }

    public void submit(List<EpisodeItem> items) {
        data.clear();
        if (items != null) data.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_episode, p, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int i) {
        final EpisodeItem it = data.get(i);
        h.num.setText(it.num.isEmpty() ? "EP" : it.num);
        h.title.setText(it.title);
        h.date.setText(it.date);
        h.itemView.setOnClickListener(v -> { if (click != null) click.onPick(it); });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView num, title, date;
        VH(@NonNull View v) {
            super(v);
            num = v.findViewById(R.id.num);
            title = v.findViewById(R.id.title);
            date = v.findViewById(R.id.date);
        }
    }
}
