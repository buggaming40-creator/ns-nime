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

/** Adapter grid tab Tersimpan: sampul, judul, status, lencana episode baru. */
public class BookmarkAdapter extends RecyclerView.Adapter<BookmarkAdapter.VH> {

    public interface OnClick {
        void onOpen(BookmarkItem item);
        void onDelete(BookmarkItem item);
    }

    private final List<BookmarkItem> data = new ArrayList<>();
    private final OnClick click;
    /** Nama kategori per id (dari fragment); label sembunyi bila kosong. */
    private java.util.Map<Long, String> catNames = new java.util.HashMap<>();

    public BookmarkAdapter(OnClick click) { this.click = click; }

    public void setCatNames(java.util.Map<Long, String> names) {
        catNames = names == null ? new java.util.HashMap<>() : names;
    }

    public void submit(List<BookmarkItem> items) {
        data.clear();
        if (items != null) data.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        View v = LayoutInflater.from(p.getContext())
                .inflate(R.layout.ui_item_bookmark, p, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int i) {
        final BookmarkItem it = data.get(i);
        h.title.setText(it.title);
        h.status.setText(it.status);
        h.status.setVisibility(it.status.isEmpty() ? View.GONE : View.VISIBLE);
        // Lencana hanya bila jumlah episode situs melebihi yang sudah dilihat.
        h.newEp.setVisibility(it.hasNewEpisode() ? View.VISIBLE : View.GONE);
        // Subjudul kategori: sekunder, sembunyi bila kosong / tanpa kategori.
        String cn = catNames.get(it.catId);
        boolean showCat = cn != null && !cn.isEmpty() && it.catId > 0;
        h.catName.setText(showCat ? cn : "");
        h.catName.setVisibility(showCat ? View.VISIBLE : View.GONE);
        ImageLoader.load(it.thumb, h.thumb);

        h.itemView.setOnClickListener(v -> { if (click != null) click.onOpen(it); });
        h.btnDelete.setOnClickListener(v -> { if (click != null) click.onDelete(it); });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextView title, status, newEp, catName;
        final View btnDelete;
        VH(@NonNull View v) {
            super(v);
            thumb = v.findViewById(R.id.thumb);
            title = v.findViewById(R.id.title);
            status = v.findViewById(R.id.status);
            newEp = v.findViewById(R.id.uiNewEp);
            catName = v.findViewById(R.id.catName);
            btnDelete = v.findViewById(R.id.uiDelete);
        }
    }
}
