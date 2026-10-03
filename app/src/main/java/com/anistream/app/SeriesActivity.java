package com.anistream.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
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
    private ProgressBar progress;
    private TextView empty, metaLine, synopsis, synToggle, epCount;
    private LinearLayout genreRow;
    private View synCard;
    private ImageButton btnSort;
    private HistoryStore store;
    private BookmarkStore bookmarks;
    private ImageButton btnBookmark;

    /** false = terbaru dulu (bawaan situs); true = episode 1 dulu. */
    private boolean asc;

    /** Daftar episode hasil pemuatan — selalu dalam urutan tampil. */
    private final List<EpisodeItem> episodes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Aksen pilihan diterapkan paling awal sebelum layout diinflasi.
        ThemeUtils.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_series);

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
        synCard = findViewById(R.id.synCard);
        synopsis = findViewById(R.id.synopsis);
        synToggle = findViewById(R.id.synToggle);
        epCount = findViewById(R.id.epCount);
        btnSort = findViewById(R.id.btnSort);

        store = new HistoryStore(this);
        bookmarks = new BookmarkStore(this);

        if (!thumb.isEmpty()) ImageLoader.load(thumb, iv);
        if (!title.isEmpty()) tv.setText(title);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Tombol bookmark di header: simpan / lepas simpan judul ini.
        btnBookmark = findViewById(R.id.btnBookmark);
        btnBookmark.setOnClickListener(v -> toggleBookmark());
        syncBookmarkButton();

        btnSort.setOnClickListener(v -> {
            asc = !asc;
            sortEpisodes();
            syncSortButton();
        });
        syncSortButton();

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
        adapter = new EpisodeAdapter(this::askEpisode);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

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

                syncSeriesToBookmark(s);
                syncBookmarkButton();

                episodes.clear();
                episodes.addAll(s.episodes);
                sortEpisodes();
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
        if (adapter != null) adapter.submit(new ArrayList<>(episodes));
    }

    private void syncSortButton() {
        if (btnSort == null) return;
        btnSort.setContentDescription(getString(
                asc ? R.string.sort_newest : R.string.sort_oldest));
        btnSort.setAlpha(asc ? 1f : 0.55f);
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

    // ------------------------------------------------------- pilihan episode

    /** Ketuk episode: Putar Sekarang / Unduh / Batal (ala AL, tanpa login). */
    private void askEpisode(EpisodeItem item) {
        if (item == null) return;
        String[] options = {getString(R.string.play_now), getString(R.string.download)};
        new AlertDialog.Builder(this)
                .setTitle(item.title.isEmpty() ? title : item.title)
                .setItems(options, (d, which) -> {
                    if (which == 0) openEpisode(item);
                    else downloadEpisode(item);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Unduh via tautan GoFile situs (dibuka di peramban). */
    private void downloadEpisode(EpisodeItem item) {
        Toast.makeText(this, R.string.loading, Toast.LENGTH_SHORT).show();
        Async.go(() -> Oploverz.loadEpisode(item.url),
                new Async.Done<Oploverz.Episode>() {
                    @Override public void ok(Oploverz.Episode ep) {
                        if (isFinishing() || isDestroyed()) return;
                        if (ep != null && !ep.downloadUrl.isEmpty()) {
                            try {
                                startActivity(new Intent(Intent.ACTION_VIEW,
                                        Uri.parse(ep.downloadUrl)));
                            } catch (Throwable t) {
                                Toast.makeText(SeriesActivity.this,
                                        R.string.err_net, Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(SeriesActivity.this,
                                    R.string.no_download, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override public void err(Throwable t) {
                        if (isFinishing() || isDestroyed()) return;
                        Toast.makeText(SeriesActivity.this,
                                R.string.err_net, Toast.LENGTH_SHORT).show();
                    }
                });
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

        int siteCount = episodes.size();
        if (siteCount <= 0) siteCount = BookmarkStore.parseCount(statusText);
        String status = BookmarkStore.buildStatus(statusText, siteCount);

        bookmarks.toggle(key, title, thumb, status, siteCount);
        syncBookmarkButton();
    }

    /** Kunci bookmark: URL series, atau halaman yang dibuka bila belum ada. */
    private String bookmarkKey() {
        if (!seriesUrl.isEmpty()) return seriesUrl;
        return pageUrl;
    }

    /** Ikon tombol bookmark mengikuti keadaan simpan dan aksen tema aktif. */
    private void syncBookmarkButton() {
        if (btnBookmark == null || bookmarks == null) return;
        boolean saved = !bookmarkKey().isEmpty() && bookmarks.has(bookmarkKey());

        btnBookmark.setImageResource(saved ? R.drawable.ui_ic_bookmark_filled
                                           : R.drawable.ic_bookmark);
        btnBookmark.setContentDescription(getString(
                saved ? R.string.bookmark_remove : R.string.bookmark_add));

        int tint = saved
                ? com.google.android.material.color.MaterialColors.getColor(
                        btnBookmark, com.google.android.material.R.attr.colorPrimary)
                : androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary);
        btnBookmark.setImageTintList(android.content.res.ColorStateList.valueOf(tint));
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
        for (int n = 0; n < episodes.size(); n++) {
            if (url != null && url.equals(episodes.get(n).url)) return n;
        }
        return -1;
    }

    private ArrayList<String> extractUrls() {
        ArrayList<String> out = new ArrayList<>();
        for (EpisodeItem e : episodes) out.add(e.url);
        return out;
    }

    private ArrayList<String> extractTitles() {
        ArrayList<String> out = new ArrayList<>();
        for (EpisodeItem e : episodes) out.add(e.title);
        return out;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
