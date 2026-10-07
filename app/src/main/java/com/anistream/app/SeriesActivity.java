package com.anistream.app;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Halaman detail ala AL: kembali + simpan, poster tengah, judul, baris meta
 * (studio | tipe | status | tahun), chip genre (ketuk = cari), kartu sinopsis
 * yang bisa dibuka, kepala "Episode (N)" + tombol urut (terbaru/terlama),
 * lalu daftar episode. Ketuk episode membuka pilihan Putar/Unduh.
 */
public class SeriesActivity extends AppCompatActivity {

    private static final Pattern EP_INT = Pattern.compile("(\\d+)");

    private String title = "", thumb = "", seriesUrl = "", statusText = "", pageUrl = "";
    private EpisodeAdapter adapter;
    private RecyclerView recycler;
    private ProgressBar progress;
    private TextView empty, metaLine, synopsis, synToggle, epCount;
    private LinearLayout genreRow, castRow;
    private View synCard, castCard;
    private ImageButton btnSort, btnView;
    private HistoryStore store;
    private BookmarkStore bookmarks;
    private com.google.android.material.button.MaterialButton btnBookmark;

    /** Kartu lanjutkan menonton (diisi dari riwayat judul ini). */
    private View resumeCard;
    private ImageView resumeThumb;
    private TextView resumeLine;
    private ProgressBar resumeProgress;
    private HistoryItem resumeRow;

    /** false = terbaru dulu (bawaan situs); true = episode 1 dulu. */
    private boolean asc;

    /** false = baris daftar; true = grid pil nomor. */
    private boolean gridMode;

    /** Daftar episode hasil pemuatan — selalu dalam urutan tampil. */
    private final List<EpisodeItem> episodes = new ArrayList<>();

    /** Tampilan setelah filter rentang (yang dibaca adapter + intent rel). */
    private final List<EpisodeItem> displayed = new ArrayList<>();

    /** Rentang nomor episode terpilih; -1/-1 = Semua. */
    private int rangeStart = -1, rangeEnd = -1;
    private View epRangeRow;
    private LinearLayout epRangeList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Aksen pilihan diterapkan paling awal sebelum layout diinflasi.
        ThemeUtils.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_series);
        hideSystemBars();

        Intent in = getIntent();
        String url = in.getStringExtra("url");
        title = nz(in.getStringExtra("title"));
        thumb = nz(in.getStringExtra("thumb"));
        pageUrl = nz(url);

        progress = findViewById(R.id.progress);
        empty = findViewById(R.id.empty);
        ImageView iv = findViewById(R.id.thumb);
        TextView tv = findViewById(R.id.title);
        metaLine = findViewById(R.id.metaLine);
        genreRow = findViewById(R.id.genreRow);
        castRow = findViewById(R.id.castRow);
        castCard = findViewById(R.id.castCard);
        synCard = findViewById(R.id.synCard);
        synopsis = findViewById(R.id.synopsis);
        synToggle = findViewById(R.id.synToggle);
        epCount = findViewById(R.id.epCount);
        btnSort = findViewById(R.id.btnSort);
        btnView = findViewById(R.id.btnView);
        epRangeRow = findViewById(R.id.epRangeRow);
        epRangeList = findViewById(R.id.epRangeList);
        resumeCard = findViewById(R.id.resumeCard);
        resumeThumb = findViewById(R.id.resumeThumb);
        resumeLine = findViewById(R.id.resumeLine);
        resumeProgress = findViewById(R.id.resumeProgress);

        store = new HistoryStore(this);
        bookmarks = new BookmarkStore(this);

        if (!thumb.isEmpty()) ImageLoader.load(thumb, iv);
        if (!title.isEmpty()) tv.setText(title);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Tombol bookmark di header: simpan / lepas simpan judul ini.
        btnBookmark = findViewById(R.id.btnBookmark);
        btnBookmark.setOnClickListener(v -> toggleBookmark());
        syncBookmarkButton();

        // Bagikan judul (teks + tautan via aplikasi lain).
        findViewById(R.id.btnShare).setOnClickListener(v -> shareTitle());

        btnSort.setOnClickListener(v -> {
            asc = !asc;
            sortEpisodes();
            syncSortButton();
        });
        syncSortButton();

        // Ganti tampilan baris/grid daftar episode.
        if (btnView != null) {
            btnView.setOnClickListener(v -> {
                gridMode = !gridMode;
                applyViewMode();
            });
        }

        if (synToggle != null) {
            synToggle.setOnClickListener(v -> {
                boolean open = synopsis.getMaxLines() != Integer.MAX_VALUE;
                synopsis.setMaxLines(open ? Integer.MAX_VALUE : 3);
                synopsis.setEllipsize(open ? null
                        : android.text.TextUtils.TruncateAt.END);
                synToggle.setText(open ? R.string.read_less : R.string.read_more);
            });
        }

        RecyclerView rv = findViewById(R.id.recycler);
        recycler = rv;
        adapter = new EpisodeAdapter(this::openEpisode);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);
        applyViewMode();

        if (url == null || url.isEmpty()) {
            progress.setVisibility(View.GONE);
            empty.setText(R.string.err_net);
            empty.setVisibility(TextView.VISIBLE);
            return;
        }

        final String target = url;
        Async.go(() -> Oploverz.loadSeries(target), new Async.Done<Oploverz.Series>() {
            @Override public void ok(Oploverz.Series s) {
                progress.setVisibility(View.GONE);

                if (!s.title.isEmpty()) { title = s.title; tv.setText(title); }
                if (!s.thumb.isEmpty()) { thumb = s.thumb; ImageLoader.load(thumb, iv); }
                if (!s.seriesUrl.isEmpty()) seriesUrl = s.seriesUrl;
                // Halaman episode kadang tidak memuat tautan series — pakai URL tujuan.
                if (seriesUrl.isEmpty()) seriesUrl = pageUrl;

                String st = s.status;
                if (st.isEmpty()) st = s.episodes.size() + " Episode";
                statusText = st;

                metaLine.setText(metaOf(s));
                metaLine.setVisibility(metaOf(s).isEmpty() ? View.GONE : View.VISIBLE);
                buildGenres(s.genres);
                buildSynopsis(s.synopsis);
                buildCasts(s.casts);

                syncSeriesToBookmark(s);
                syncBookmarkButton();

                episodes.clear();
                episodes.addAll(s.episodes);
                sortEpisodes();
                buildRangeChips();
                refreshResume();
                epCount.setText(getString(R.string.episode_count_fmt, episodes.size()));

                if (episodes.isEmpty()) {
                    empty.setText(R.string.err_net);
                    empty.setVisibility(TextView.VISIBLE);
                } else {
                    empty.setVisibility(TextView.GONE);
                }
            }

            @Override public void err(Throwable t) {
                progress.setVisibility(View.GONE);
                metaLine.setText(R.string.err_net);
                empty.setText(R.string.err_net);
                empty.setVisibility(TextView.VISIBLE);
            }
        });
    }

    // ------------------------------------------------------------------ info

    /** "Studio | Tipe | Status | Tahun" — hanya bagian yang ada datanya. */
    private static String metaOf(Oploverz.Series s) {
        List<String> parts = new ArrayList<>();
        if (!s.studio.isEmpty()) parts.add(s.studio);
        if (!s.type.isEmpty()) parts.add(s.type);
        if (!s.statusWord.isEmpty()) parts.add(s.statusWord);
        else if (!s.status.isEmpty()) parts.add(s.status);
        if (!s.released.isEmpty()) parts.add(s.released);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(" | ");
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    /** Chip genre situs; ketuk = cari genre tersebut. */
    private void buildGenres(List<String> genres) {
        if (genreRow == null) return;
        genreRow.removeAllViews();
        if (genres == null || genres.isEmpty()) {
            genreRow.setVisibility(View.GONE);
            return;
        }
        genreRow.setVisibility(View.VISIBLE);
        for (String g : genres) {
            TextView c = new TextView(this);
            c.setText(g);
            c.setTextSize(12);
            c.setTypeface(null, android.graphics.Typeface.BOLD);
            c.setTextColor(getColor(R.color.text_secondary));
            c.setBackgroundResource(R.drawable.ui_bg_pill);
            c.setPadding(dp(14), dp(7), dp(14), dp(7));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            genreRow.addView(c, lp);
            final String genre = g;
            c.setOnClickListener(v -> {
                SearchFragment.requestQuery(genre);
                Intent back = new Intent(SeriesActivity.this, MainActivity.class);
                back.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                back.putExtra(MainActivity.EXTRA_TAB, PagerAdapter.PAGE_SEARCH);
                startActivity(back);
            });
        }
    }

    /**
     * Pengisi suara ala Animok: lingkaran inisial + nama (data teks situs,
     * tanpa foto). Ketuk = cari nama tersebut (filmografi kasar).
     */
    private void buildCasts(List<String> casts) {
        if (castRow == null || castCard == null) return;
        castRow.removeAllViews();
        if (casts == null || casts.isEmpty()) {
            castCard.setVisibility(View.GONE);
            return;
        }
        castCard.setVisibility(View.VISIBLE);
        for (String name : casts) {
            if (name == null || name.trim().isEmpty()) continue;
            final String actor = name.trim();

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            clp.setMarginEnd(dp(12));
            col.setLayoutParams(clp);

            TextView avatar = new TextView(this);
            String initial = actor.substring(0, 1).toUpperCase(java.util.Locale.US);
            avatar.setText(initial);
            avatar.setTextSize(20);
            avatar.setTypeface(null, android.graphics.Typeface.BOLD);
            avatar.setTextColor(getColor(R.color.text_primary));
            avatar.setGravity(android.view.Gravity.CENTER);
            avatar.setBackgroundResource(R.drawable.ui_bg_avatar);
            int sz = dp(56);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(sz, sz);
            avatar.setLayoutParams(alp);

            TextView label = new TextView(this);
            label.setText(actor);
            label.setTextSize(11);
            label.setTextColor(getColor(R.color.text_secondary));
            label.setGravity(android.view.Gravity.CENTER);
            label.setMaxLines(2);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    dp(72), LinearLayout.LayoutParams.WRAP_CONTENT);
            llp.topMargin = dp(6);
            label.setLayoutParams(llp);

            col.addView(avatar);
            col.addView(label);
            col.setOnClickListener(v -> {
                SearchFragment.requestQuery(actor);
                Intent back = new Intent(SeriesActivity.this, MainActivity.class);
                back.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                back.putExtra(MainActivity.EXTRA_TAB, PagerAdapter.PAGE_SEARCH);
                startActivity(back);
            });
            castRow.addView(col);
        }
    }

    /** Bagikan judul + tautan series via aplikasi lain. */
    private void shareTitle() {
        String url = !seriesUrl.isEmpty() ? seriesUrl : pageUrl;
        String text = title.isEmpty() ? url : (title + "\n" + url);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text.trim());
        try {
            startActivity(Intent.createChooser(i, getString(R.string.share_title)));
        } catch (Throwable ignored) {
        }
    }

    private void buildSynopsis(String syn) {
        if (synCard == null) return;
        if (syn == null || syn.trim().isEmpty()) {
            synCard.setVisibility(View.GONE);
            return;
        }
        synCard.setVisibility(View.VISIBLE);
        synopsis.setText(syn.trim());
        synopsis.setMaxLines(3);
        synopsis.setEllipsize(android.text.TextUtils.TruncateAt.END);
        synToggle.setText(R.string.read_more);
    }

    // ------------------------------------------------------------------ urut

    /** Terbaru dulu (angka besar di atas) atau episode 1 dulu. */
    private void sortEpisodes() {
        episodes.sort((a, b) -> asc
                ? Integer.compare(epInt(a.num), epInt(b.num))
                : Integer.compare(epInt(b.num), epInt(a.num)));
        applyRangeFilter();
    }

    /** Saring `episodes` (penuh, sudah diurut) ke `displayed` sesuai rentang. */
    private void applyRangeFilter() {
        displayed.clear();
        boolean all = rangeStart < 0;
        for (EpisodeItem e : episodes) {
            if (all || rangePass(e)) displayed.add(e);
        }
        if (adapter != null) adapter.submit(new ArrayList<>(displayed));
    }

    /** Nomor tak terbaca selalu lolos; yang terbaca harus masuk rentang. */
    private boolean rangePass(EpisodeItem e) {
        int n = epIntOr(e == null ? null : e.num, -1);
        if (n < 0) return true;
        return n >= rangeStart && n <= rangeEnd;
    }

    private void syncSortButton() {
        if (btnSort == null) return;
        btnSort.setContentDescription(getString(
                asc ? R.string.sort_newest : R.string.sort_oldest));
        btnSort.setAlpha(asc ? 1f : 0.55f);
    }

    // ------------------------------------------------------- tampilan grid/list

    /** Terapkan mode baris/grid ke adapter + layout manager + ikon tombol. */
    private void applyViewMode() {
        if (adapter != null) adapter.setGrid(gridMode);
        if (recycler != null) {
            if (gridMode) {
                boolean land = getResources().getConfiguration().orientation
                        == Configuration.ORIENTATION_LANDSCAPE;
                recycler.setLayoutManager(new GridLayoutManager(this, land ? 6 : 4));
            } else {
                recycler.setLayoutManager(new LinearLayoutManager(this));
            }
        }
        syncViewButton();
    }

    private void syncViewButton() {
        if (btnView == null) return;
        // Ikon menunjukkan tampilan TUJUAN: grid saat mode baris, sebaliknya.
        btnView.setImageResource(gridMode ? R.drawable.ic_list : R.drawable.ic_grid);
        btnView.setAlpha(gridMode ? 1f : 0.55f);
    }

    // ------------------------------------------------------- rentang episode

    /**
     * Chip rentang "Semua | 1–25 | 26–50 …" per 25 nomor episode. Hanya
     * ditampilkan bila seri panjang (>25 episode) dan ada lebih dari satu
     * chip; nomor yang tak terbaca selalu lolos filter.
     */
    private void buildRangeChips() {
        if (epRangeRow == null || epRangeList == null) return;
        epRangeList.removeAllViews();
        if (episodes.size() <= 25) {
            epRangeRow.setVisibility(View.GONE);
            rangeStart = -1;
            rangeEnd = -1;
            return;
        }
        int max = 0;
        for (EpisodeItem e : episodes) {
            int n = epIntOr(e.num, -1);
            if (n > max) max = n;
        }
        List<int[]> ranges = new ArrayList<>();
        for (int s = 1; s <= max; s += 25) {
            ranges.add(new int[]{s, Math.min(s + 24, max)});
        }
        if (ranges.size() < 1) {
            epRangeRow.setVisibility(View.GONE);
            rangeStart = -1;
            rangeEnd = -1;
            return;
        }
        epRangeRow.setVisibility(View.VISIBLE);
        // Selalu ada chip "Semua" di urutan pertama.
        addRangeChip(getString(R.string.range_all), -1, -1);
        for (int[] r : ranges) {
            addRangeChip(r[0] + "\u2013" + r[1], r[0], r[1]);
        }
        syncRangeUi();
    }

    private void addRangeChip(String label, final int start, final int end) {
        TextView c = new TextView(this);
        c.setText(label);
        c.setTextSize(12);
        c.setTypeface(null, android.graphics.Typeface.BOLD);
        c.setPadding(dp(14), dp(7), dp(14), dp(7));
        c.setTag(new int[]{start, end});
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(8));
        epRangeList.addView(c, lp);
        c.setOnClickListener(v -> {
            rangeStart = start;
            rangeEnd = end;
            applyRangeFilter();
            syncRangeUi();
        });
    }

    /** Tandai chip terpilih (latar aksen) dan kembalikan sisanya ke pil biasa. */
    private void syncRangeUi() {
        if (epRangeList == null) return;
        int onAccent = com.google.android.material.color.MaterialColors.getColor(
                epRangeList, com.google.android.material.R.attr.colorOnPrimary);
        int idle;
        try {
            idle = getColor(R.color.text_secondary);
        } catch (Throwable t) {
            idle = 0xFF888888;
        }
        for (int i = 0; i < epRangeList.getChildCount(); i++) {
            View child = epRangeList.getChildAt(i);
            if (!(child instanceof TextView)) continue;
            TextView c = (TextView) child;
            int[] tag = (int[]) c.getTag();
            boolean sel = tag != null && tag[0] == rangeStart && tag[1] == rangeEnd;
            c.setBackgroundResource(sel ? R.drawable.bg_badge : R.drawable.ui_bg_pill);
            c.setTextColor(sel ? onAccent : idle);
        }
    }

    private static int epIntOr(String num, int fallback) {
        if (num == null) return fallback;
        Matcher m = EP_INT.matcher(num);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static int epInt(String num) {
        if (num == null) return 0;
        Matcher m = EP_INT.matcher(num);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------ data

    /**
     * Sinkronkan data situs ke bookmark (bila judul ini tersimpan):
     * jumlah episode + status keterbaruan untuk lencana "Ada Episode Baru!".
     */
    private void syncSeriesToBookmark(Oploverz.Series s) {
        if (bookmarks == null || seriesUrl.isEmpty() || !bookmarks.has(seriesUrl)) return;
        int siteCount = s.episodes.size();
        BookmarkItem saved = bookmarks.get(seriesUrl);
        // Pertama kali jumlah episode terbaca (mis. bookmark ditambahkan saat
        // daftar episode belum termuat), anggap sudah dilihat agar tidak
        // muncul lencana palsu.
        if (saved != null && saved.lastSeenEp <= 0 && siteCount > 0) {
            bookmarks.touchEpisodes(seriesUrl, siteCount);
        }
        bookmarks.refresh(seriesUrl, BookmarkStore.buildStatus(s.status, siteCount));
    }

    /** Simpan / lepas simpan judul ini pada tab Tersimpan. */
    private void toggleBookmark() {
        String key = bookmarkKey();
        if (key.isEmpty()) return;

        // Sudah tersimpan → lepas langsung seperti semula.
        if (bookmarks.has(key)) {
            bookmarks.toggle(key, title, thumb, "", 0);
            syncBookmarkButton();
            return;
        }
        showSaveSheet(key);
    }

    /**
     * Lembar "Simpan ke": pilih kategori sekali ketuk + tombol simpan, atau
     * "+ Kategori baru" untuk membuat kategori lalu menyimpan ke sana.
     */
    private void showSaveSheet(final String key) {
        List<BookmarkStore.Cat> all = bookmarks.cats();
        final List<BookmarkStore.Cat> real = new ArrayList<>();
        for (BookmarkStore.Cat c : all) {
            if (c != null && c.id != BookmarkStore.CAT_ALL_ID) real.add(c);
        }
        if (real.isEmpty()) {
            // Tanpa tabel kategori (DB sangat lama) — simpan polos.
            saveBookmark(key, 0);
            return;
        }
        final String[] names = new String[real.size() + 1];
        int checked = 0;
        for (int i = 0; i < real.size(); i++) {
            names[i] = real.get(i).name;
            if ("Favorit".equals(real.get(i).name)) checked = i;
        }
        names[real.size()] = getString(R.string.new_category);
        final int[] pick = {checked};

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.save_to_title)
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    if (which == real.size()) {
                        // Baris "+ Kategori baru" — buka dialog nama.
                        d.dismiss();
                        showNewCatDialog(key);
                    } else {
                        pick[0] = which;
                    }
                })
                .setPositiveButton(R.string.bookmark_add, (d, w) -> {
                    int at = Math.max(0, Math.min(pick[0], real.size() - 1));
                    saveBookmark(key, real.get(at).id);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Dialog nama kategori baru (maks 20 huruf) lalu simpan ke sana. */
    private void showNewCatDialog(final String key) {
        final EditText ed = new EditText(this);
        ed.setHint(R.string.new_category_hint);
        ed.setSingleLine();
        int pad = dp(4);
        ed.setPadding(pad, pad, pad, pad);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.new_category)
                .setView(ed)
                .setPositiveButton(R.string.bookmark_add, (d, w) -> {
                    long id = bookmarks.addCat(ed.getText().toString());
                    if (id < 0) return;
                    saveBookmark(key, id);
                })
                .setNegativeButton(R.string.cancel,
                        (d, w) -> showSaveSheet(key))
                .show();
    }

    /** Tulis bookmark + kategori lalu segarkan ikon. */
    private void saveBookmark(String key, long catId) {
        int siteCount = episodes.size();
        if (siteCount <= 0) siteCount = BookmarkStore.parseCount(statusText);
        String status = BookmarkStore.buildStatus(statusText, siteCount);
        bookmarks.save(key, title, thumb, status, siteCount, catId);
        syncBookmarkButton();
    }

    /** Kunci bookmark: URL series, atau halaman yang dibuka bila belum ada. */
    private String bookmarkKey() {
        if (!seriesUrl.isEmpty()) return seriesUrl;
        return pageUrl;
    }

    /** Pil Simpan mengikuti keadaan simpan dan aksen tema aktif. */
    private void syncBookmarkButton() {
        if (btnBookmark == null || bookmarks == null) return;
        boolean saved = !bookmarkKey().isEmpty() && bookmarks.has(bookmarkKey());

        btnBookmark.setText(saved ? R.string.saved : R.string.save);
        btnBookmark.setIconResource(saved ? R.drawable.ui_ic_bookmark_filled
                                          : R.drawable.ic_bookmark);
        btnBookmark.setContentDescription(getString(
                saved ? R.string.bookmark_remove : R.string.bookmark_add));
    }

    private void openEpisode(EpisodeItem item) {
        // Pengguna membuka episode → lencana "Ada Episode Baru!" dianggap sudah dilihat.
        if (bookmarks != null && !seriesUrl.isEmpty() && episodes.size() > 0
                && bookmarks.has(seriesUrl)) {
            bookmarks.touchEpisodes(seriesUrl, episodes.size());
        }

        // Lanjutkan dari posisi terakhir bila episode ini pernah ditonton.
        long pos = 0;
        for (HistoryItem h : store.all()) {
            if (h.epUrl != null && h.epUrl.equals(item.url)) {
                pos = h.finished() ? 0L : h.posMs;
                break;
            }
        }

        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra("epUrl", item.url);
        i.putExtra("epTitle", item.title);
        i.putExtra("title", title);
        i.putExtra("thumb", thumb);
        i.putExtra("seriesUrl", seriesUrl);
        i.putExtra("pos", pos);

        // Daftar episode + indeksnya dipakai tombol sebelumnya/berikutnya.
        int idx = indexOf(item.url);
        if (idx >= 0) {
            i.putExtra("epIndex", idx);
            i.putStringArrayListExtra("epUrlList", extractUrls());
            i.putStringArrayListExtra("epTitleList", extractTitles());
        }
        startActivity(i);
    }

    private int indexOf(String url) {
        for (int n = 0; n < displayed.size(); n++) {
            if (url != null && url.equals(displayed.get(n).url)) return n;
        }
        return -1;
    }

    private ArrayList<String> extractUrls() {
        ArrayList<String> out = new ArrayList<>();
        for (EpisodeItem e : displayed) out.add(e.url);
        return out;
    }

    private ArrayList<String> extractTitles() {
        ArrayList<String> out = new ArrayList<>();
        for (EpisodeItem e : displayed) out.add(e.title);
        return out;
    }

    // ------------------------------------------------------- lanjutkan nonton

    @Override
    protected void onResume() {
        super.onResume();
        // Riwayat bisa bertambah dari player — segarkan kartu + tombol simpan.
        refreshResume();
        syncBookmarkButton();
    }

    /**
     * Kartu "Lanjutkan Menonton": baris riwayat terbaru milik judul ini
     * (cocok series_url, daftar riwayat sudah watched_at DESC). Ketuk kartu /
     * tombol Lanjut memutar baris itu persis seperti openEpisode (posisi
     * resume + daftar rel dari `displayed`).
     */
    private void refreshResume() {
        if (resumeCard == null || store == null) return;
        String key = bookmarkKey();
        HistoryItem best = null;
        if (!key.isEmpty()) {
            for (HistoryItem h : store.all()) {
                if (h != null && key.equals(h.seriesUrl)) { best = h; break; }
            }
        }
        resumeRow = best;
        if (best == null) {
            resumeCard.setVisibility(View.GONE);
            return;
        }
        resumeCard.setVisibility(View.VISIBLE);
        if (resumeThumb != null && !thumb.isEmpty()) ImageLoader.load(thumb, resumeThumb);
        if (resumeLine != null) {
            String num = resumeNumOf(best);
            resumeLine.setText(getString(R.string.resume_line_fmt,
                    num, Utils.clock(best.posMs), Utils.clock(best.durMs)));
        }
        if (resumeProgress != null) {
            resumeProgress.setMax(100);
            resumeProgress.setProgress(best.percent());
        }
        View.OnClickListener go = v -> playResume();
        resumeCard.setOnClickListener(go);
        View btn = findViewById(R.id.resumeGo);
        if (btn != null) btn.setOnClickListener(go);
    }

    /** Nomor episode untuk baris resume (dari daftar, atau judul bila tak ada). */
    private String resumeNumOf(HistoryItem h) {
        for (EpisodeItem e : episodes) {
            if (e != null && h.epUrl != null && h.epUrl.equals(e.url)
                    && e.num != null && !e.num.isEmpty()) {
                return e.num;
            }
        }
        return h.epTitle == null || h.epTitle.isEmpty() ? "?" : h.epTitle;
    }

    /** Putar baris resume lewat jalur openEpisode yang sama. */
    private void playResume() {
        if (resumeRow == null) return;
        EpisodeItem found = null;
        for (EpisodeItem e : episodes) {
            if (e != null && resumeRow.epUrl != null && resumeRow.epUrl.equals(e.url)) {
                found = e;
                break;
            }
        }
        if (found == null) {
            // URL tak ada di daftar situs — putar tanpa tambahan rel.
            found = new EpisodeItem(
                    resumeRow.epTitle == null ? "" : resumeRow.epTitle,
                    resumeRow.epUrl == null ? "" : resumeRow.epUrl, "", "");
        }
        openEpisode(found);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static String nz(String s) { return s == null ? "" : s; }

    /** Layar penuh imersif: sembunyikan status bar + pil navigasi. Muncul
     *  sementara hanya saat di-swipe (sticky immersive). Dipanggil ulang saat
     *  fokus kembali karena swipe sistematik menampilkannya lagi. */
    private void hideSystemBars() {
        android.view.Window w = getWindow();
        if (w == null) return;
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w, false);
        androidx.core.view.WindowInsetsControllerCompat c =
                androidx.core.view.WindowCompat.getInsetsController(
                        w, w.getDecorView());
        if (c == null) return;
        c.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());
        c.setSystemBarsBehavior(androidx.core.view.WindowInsetsControllerCompat
                .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }
}
