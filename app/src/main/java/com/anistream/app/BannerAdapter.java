package com.anistream.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter banner beranda (H-2): lima rilis terbaru pertama, poster + judul.
 * Auto-scroll dan indikator dot ditangani {@link HomeFragment}.
 */
public class BannerAdapter extends RecyclerView.Adapter<BannerAdapter.VH> {

    public interface OnClick { void onPick(AnimeItem item); }

    private final List<AnimeItem> data = new ArrayList<>();
    private final OnClick click;

    public BannerAdapter(OnClick click) { this.click = click; }

    public void submit(List<AnimeItem> items) {
        data.clear();
        if (items != null) data.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        View v = LayoutInflater.from(p.getContext())
                .inflate(R.layout.ui_item_banner, p, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int i) {
        final AnimeItem it = data.get(i);
        h.title.setText(it.title);
        h.meta.setText(it.meta);
        h.meta.setVisibility(it.meta.isEmpty() ? View.GONE : View.VISIBLE);
        ImageLoader.load(it.thumb, h.image);
        h.itemView.setOnClickListener(v -> { if (click != null) click.onPick(it); });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView title, meta;
        VH(@NonNull View v) {
            super(v);
            image = v.findViewById(R.id.uiBannerImage);
            title = v.findViewById(R.id.uiBannerTitle);
            meta = v.findViewById(R.id.uiBannerMeta);
        }
    }
}
