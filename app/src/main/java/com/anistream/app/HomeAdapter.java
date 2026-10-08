package com.anistream.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter beranda: satu RecyclerView dengan empat jenis baris —
 * judul seksi (penuh), kartu poster grid (1 kolom), baris horizontal
 * (penuh) untuk seksi "Sedang Tayang" serta "Top Rating", dan baris
 * kartu progres "Lanjutkan Menonton" dari riwayat tonton.
 *
 * Seksi yang datanya kosong tidak pernah ditampilkan (tanpa data karangan).
 * Grid memakai GridLayoutManager; {@link #isFullSpan(int)} memberi tahu
 * LayoutManager baris mana yang harus melebar penuh.
 */
public class HomeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnClick { void onPick(AnimeItem item); }

    /** Lanjutkan tontonan: buka episode dari kartu riwayat. */
    public interface OnResume { void onContinue(HistoryItem item); }

    private static final int TYPE_SECTION = 0;
    private static final int TYPE_POSTER = 1;
    private static final int TYPE_ROW = 2;
    private static final int TYPE_CONTINUE = 3;

    /** Satu baris daftar: judul seksi, kartu poster, baris, atau lanjutkan. */
    private static final class Row {
        final int type, titleRes, hintRes;
        final AnimeItem item;
        final List<AnimeItem> items;
        final List<HistoryItem> hist;

        Row(int type, int titleRes, int hintRes, AnimeItem item, List<AnimeItem> items) {
            this(type, titleRes, hintRes, item, items, null);
        }

        Row(int type, int titleRes, int hintRes, AnimeItem item,
                List<AnimeItem> items, List<HistoryItem> hist) {
            this.type = type;
            this.titleRes = titleRes;
            this.hintRes = hintRes;
            this.item = item;
            this.items = items;
            this.hist = hist;
        }
    }

    private final OnClick click;
    private final OnResume resume;
    private final List<Row> rows = new ArrayList<>();

    private final List<AnimeItem> latest = new ArrayList<>();
    private final List<AnimeItem> ongoing = new ArrayList<>();
    private final List<AnimeItem> top = new ArrayList<>();
    private final List<HistoryItem> cont = new ArrayList<>();
    private boolean hasOngoing, hasTop;

    public HomeAdapter(OnClick click) { this(click, null); }

    public HomeAdapter(OnClick click, OnResume resume) {
        this.click = click;
        this.resume = resume;
    }

    // ------------------------------------------------------------- data

    public void setLatest(List<AnimeItem> items) {
        latest.clear();
        if (items != null) latest.addAll(items);
        rebake();
    }

    /** Seksi Sedang Tayang — kosong/berisi, tampil hanya bila ada datanya. */
    public void setOngoing(List<AnimeItem> items) {
        ongoing.clear();
        if (items != null) ongoing.addAll(items);
        hasOngoing = !ongoing.isEmpty();
        rebake();
    }

    /** Seksi Top Rating — urutan situs, tanpa angka rating. */
    public void setTop(List<AnimeItem> items) {
        top.clear();
        if (items != null) top.addAll(items);
        hasTop = !top.isEmpty();
        rebake();
    }

    /** Seksi "Lanjutkan Menonton" — kartu progres dari riwayat (terbaru dulu). */
    public void setContinueWatching(List<HistoryItem> items) {
        cont.clear();
        if (items != null) cont.addAll(items);
        rebake();
    }

    private void rebake() {
        rows.clear();
        if (!cont.isEmpty()) {
            rows.add(new Row(TYPE_SECTION, R.string.resume_card_title, 0, null, null));
            rows.add(new Row(TYPE_CONTINUE, 0, 0, null, null, new ArrayList<>(cont)));
        }
        if (!latest.isEmpty()) {
            rows.add(new Row(TYPE_SECTION, R.string.section_latest, R.string.ui_refresh_hint,
                    null, null));
            for (AnimeItem a : latest) {
                rows.add(new Row(TYPE_POSTER, 0, 0, a, null));
            }
        }
        if (hasOngoing) {
            rows.add(new Row(TYPE_SECTION, R.string.section_ongoing, 0, null, null));
            rows.add(new Row(TYPE_ROW, 0, 0, null, new ArrayList<>(ongoing)));
        }
        if (hasTop) {
            rows.add(new Row(TYPE_SECTION, R.string.section_top, 0, null, null));
            rows.add(new Row(TYPE_ROW, 0, 0, null, new ArrayList<>(top)));
        }
        notifyDataSetChanged();
    }

    /** Baris mana yang melebar penuh (semua kecuali kartu poster). */
    public boolean isFullSpan(int position) {
        return position < 0 || position >= rows.size()
                || rows.get(position).type != TYPE_POSTER;
    }

    // -------------------------------------------------------- recycling

    @Override public int getItemCount() { return rows.size(); }

    @Override public int getItemViewType(int position) { return rows.get(position).type; }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (type == TYPE_SECTION) {
            return new SectionVH(inf.inflate(R.layout.ui_item_section, parent, false));
        }
        if (type == TYPE_ROW || type == TYPE_CONTINUE) {
            return new RowVH(inf.inflate(R.layout.ui_item_row, parent, false));
        }
        return new PosterVH(inf.inflate(R.layout.item_anime, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (row.type == TYPE_SECTION) {
            SectionVH h = (SectionVH) holder;
            h.title.setText(row.titleRes);
            if (row.hintRes != 0) {
                h.hint.setText(row.hintRes);
                h.hint.setVisibility(View.VISIBLE);
            } else {
                h.hint.setVisibility(View.GONE);
            }
            return;
        }

        if (row.type == TYPE_ROW) {
            ((RowVH) holder).rv.setAdapter(new RowAdapter(row.items, click));
            return;
        }

        if (row.type == TYPE_CONTINUE) {
            ((RowVH) holder).rv.setAdapter(new ContinueAdapter(row.hist, resume));
            return;
        }

        bindPoster((PosterVH) holder, row.item);
    }

    private void bindPoster(final PosterVH h, final AnimeItem it) {
        if (it == null) return;
        h.title.setText(it.title);
        h.meta.setText(it.meta);
        h.badge.setText(badgeOf(it.meta));
        h.badge.setVisibility(it.meta.isEmpty() ? View.GONE : View.VISIBLE);
        ImageLoader.load(it.thumb, h.thumb);
        h.itemView.setOnClickListener(v -> { if (click != null) click.onPick(it); });
    }

    /** Lencana episode diambil dari metadata ("… · Ep 13" → "Ep 13"). */
    private static String badgeOf(String meta) {
        if (meta == null || meta.trim().isEmpty()) return "";
        String m = meta.trim();
        int idx = m.lastIndexOf(" · ");
        String last = (idx >= 0) ? m.substring(idx + 3).trim() : m;
        if (last.length() > 16) last = m;
        return last;
    }

    // ------------------------------------------------------------ VH

    static class SectionVH extends RecyclerView.ViewHolder {
        final TextView title, hint;
        SectionVH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.uiSectionTitle);
            hint = v.findViewById(R.id.uiSectionHint);
        }
    }

    static class PosterVH extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextView title, meta, badge;
        PosterVH(@NonNull View v) {
            super(v);
            thumb = v.findViewById(R.id.thumb);
            title = v.findViewById(R.id.title);
            meta = v.findViewById(R.id.meta);
            badge = v.findViewById(R.id.badge);
        }
    }

    static class RowVH extends RecyclerView.ViewHolder {
        final RecyclerView rv;
        RowVH(@NonNull View v) {
            super(v);
            rv = (RecyclerView) v;
            rv.setLayoutManager(new LinearLayoutManager(
                    v.getContext(), LinearLayoutManager.HORIZONTAL, false));
            rv.setNestedScrollingEnabled(false);
        }
    }

    /** Adapter baris horizontal: kartu poster berlebar tetap. */
    private static class RowAdapter extends RecyclerView.Adapter<RowAdapter.VH> {
        private final List<AnimeItem> data;
        private final OnClick click;

        RowAdapter(List<AnimeItem> data, OnClick click) {
            this.data = data == null ? new ArrayList<>() : data;
            this.click = click;
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            View v = LayoutInflater.from(p.getContext())
                    .inflate(R.layout.ui_item_serie, p, false);
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

    /** Adapter kartu "Lanjutkan Menonton": thumbnail + chip + bar progres. */
    private static class ContinueAdapter extends RecyclerView.Adapter<ContinueAdapter.VH> {
        private final List<HistoryItem> data;
        private final OnResume resume;

        ContinueAdapter(List<HistoryItem> data, OnResume resume) {
            this.data = data == null ? new ArrayList<>() : data;
            this.resume = resume;
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            View v = LayoutInflater.from(p.getContext())
                    .inflate(R.layout.item_continue, p, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int i) {
            final HistoryItem it = data.get(i);
            h.title.setText(it.title);
            h.chip.setText(it.epTitle == null || it.epTitle.isEmpty()
                    ? it.title : it.epTitle);
            ImageLoader.load(it.thumb, h.thumb);
            if (it.durMs > 0) {
                h.time.setText(fmt(it.posMs) + " / " + fmt(it.durMs));
                h.time.setVisibility(View.VISIBLE);
                h.progress.setProgressCompat(it.percent(), false);
                h.progress.setVisibility(View.VISIBLE);
            } else {
                h.time.setVisibility(View.GONE);
                h.progress.setVisibility(View.GONE);
            }
            h.itemView.setOnClickListener(v -> {
                if (resume != null) resume.onContinue(it);
            });
        }

        /** Format ms → "m:ss" atau "h:mm:ss". */
        private static String fmt(long ms) {
            long total = ms / 1000;
            long s = total % 60;
            long m = (total / 60) % 60;
            long h = total / 3600;
            if (h > 0) return h + ":" + String.format(java.util.Locale.US, "%02d:%02d", m, s);
            return m + ":" + String.format(java.util.Locale.US, "%02d", s);
        }

        @Override public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final TextView title, chip, time;
            final com.google.android.material.progressindicator.LinearProgressIndicator progress;
            VH(@NonNull View v) {
                super(v);
                thumb = v.findViewById(R.id.contThumb);
                title = v.findViewById(R.id.contTitle);
                chip = v.findViewById(R.id.contChip);
                time = v.findViewById(R.id.contTime);
                progress = v.findViewById(R.id.contProgress);
            }
        }
    }
}
