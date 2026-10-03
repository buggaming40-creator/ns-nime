package com.anistream.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/** Adapter grid untuk daftar terbaru dan hasil pencarian. */
public class AnimeAdapter extends RecyclerView.Adapter<AnimeAdapter.VH> {

    public interface OnClick { void onPick(AnimeItem item); }

    private final List<AnimeItem> data = new ArrayList<>();
    private final OnClick click;

    public AnimeAdapter(OnClick click) { this.click = click; }

    public void submit(List<AnimeItem> items) {
        data.clear();
        if (items != null) data.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_anime, p, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int i) {
        final AnimeItem it = data.get(i);
        h.title.setText(it.title);
        h.meta.setText(it.meta);
        h.badge.setText(badgeOf(it.meta));
        h.badge.setVisibility(it.meta.isEmpty() ? View.GONE : View.VISIBLE);
        ImageLoader.load(it.thumb, h.thumb);
        h.itemView.setOnClickListener(v -> { if (click != null) click.onPick(it); });
    }

    @Override public int getItemCount() { return data.size(); }

    /**
     * Lencana di atas poster memakai bagian terakhir dari metadata
     * ("Anime · Sub · Ep 13" → "Ep 13", "Anime · Sub · Ongoing" → "Ongoing").
     */
    private static String badgeOf(String meta) {
        if (meta == null || meta.trim().isEmpty()) return "";
        String m = meta.trim();
        int idx = m.lastIndexOf(" · ");
        String last = (idx >= 0) ? m.substring(idx + 3).trim() : m;
        if (last.length() > 16) last = m;
        return last;
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextView title, meta, badge;
        VH(@NonNull View v) {
            super(v);
            thumb = v.findViewById(R.id.thumb);
            title = v.findViewById(R.id.title);
            meta = v.findViewById(R.id.meta);
            badge = v.findViewById(R.id.badge);
        }
    }
}
