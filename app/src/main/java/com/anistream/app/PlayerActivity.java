package com.anistream.app;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Layar pemutar.
 *
 * Alur: WebView memuat URL mirror (Blogger) lalu klik-tombol-play disuntik via JS.
 * Saat WebView menarik berkas media, `shouldInterceptRequest` menangkap URL langsung
 * (googlevideo.com/videoplayback, mp4) dan kita serahkan ke ExoPlayer —
 * ExoPlayer yang memberi kontrol posisi sehingga riwayat menyimpan menit dan
 * resume bekerja. Bila penangkapan gagal, WebView tetap memutar sendiri.
 *
 * Dilayar lanskap: `sensorLandscape` + `configChanges` membuat rotasi tidak
 * me-recreate Activity sehingga posisi tonton tetap. Sediakan tombol episode
 * sebelumnya/berikutnya pada rel di sisi kanan.
 */
public class PlayerActivity extends AppCompatActivity {

    private String epUrl = "", epTitle = "", title = "", thumb = "", seriesUrl = "";
    private long savedPos = 0;

    private WebView web;
    private PlayerView playerView;
    private View overlay, topbar, sideRail, btnPrev, btnNext;
    private TextView status, titleBar, subtitleBar, modeBadge, epCounter, qualityBadge;
    private TextView btnSpeed, btnServer;
    private View btnEps;
    /** Rel episode siap (lebih dari 1 episode) — tampil hanya saat kontrol terlihat. */
    private boolean railReady;

    /** Langkah kecepatan putar yang bisa dipilih. */
    private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
    private static final String[] SPEED_LABELS = {
            "0,5×", "0,75×", "1×", "1,25×", "1,5×", "1,75×", "2×"};

    /** Mirror server streaming (Blogger dkk) + indeks aktif. */
    private final ArrayList<String> mirrorList = new ArrayList<>();
    private int mirrorIdx;

    private ExoPlayer player;
    private boolean exoStarted, seekDone;
    private String mediaUrl;

    private HistoryStore store;
    private HistoryItem current;
    private final Handler tick = new Handler();
    private int attempts;
    private long webElapsed;

    // Daftar episode untuk navigasi sebelumnya/berikutnya.
    private final ArrayList<String> epUrls = new ArrayList<>();
    private final ArrayList<String> epTitles = new ArrayList<>();
    private int epIndex = -1;

    /** Klik tombol play di halaman player (elemen dalam dengan kursor pointer). */
    private static final String CLICK_JS =
            "(function(){try{"
          + "var root=document.querySelector('main')||document.body;"
          + "var els=root.querySelectorAll('*'),t=null;"
          + "for(var i=0;i<els.length;i++){"
          + "  if(getComputedStyle(els[i]).cursor==='pointer')t=els[i];}"
          + "if(t){t.click();return 'ok:'+t.tagName;}"
          + "return 'none';"
          + "}catch(e){return 'err:'+e.message;}})()";

    /** Fallback bila URL media tidak tertangkap. */
    private final Runnable fallbackRun = new Runnable() {
        @Override public void run() { fallbackToWeb(); }
    };

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            long pos = current.posMs, dur = current.durMs;

            if (player != null) {
                long p = player.getCurrentPosition();
                long d = player.getDuration();
                if (p > 0) pos = p;
                if (d > 0 && d != androidx.media3.common.C.TIME_UNSET) dur = d;
            } else if (!exoStarted) {
                // Mode web: perkirakan durasi tonton dari waktu layar aktif.
                webElapsed += 3000;
                pos = webElapsed;
            }

            current.posMs = pos;
            current.durMs = dur;
            current.watchedAt = System.currentTimeMillis();
            persist();

            tick.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Aksen pilihan diterapkan paling awal sebelum layout diinflasi.
        ThemeUtils.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        epUrl = nz(getIntent().getStringExtra("epUrl"));
        epTitle = nz(getIntent().getStringExtra("epTitle"));
        title = nz(getIntent().getStringExtra("title"));
        thumb = nz(getIntent().getStringExtra("thumb"));
        seriesUrl = nz(getIntent().getStringExtra("seriesUrl"));
        savedPos = getIntent().getLongExtra("pos", 0L);
        readEpisodeList();

        web = findViewById(R.id.webview);
        playerView = findViewById(R.id.playerView);
        overlay = findViewById(R.id.overlay);
        topbar = findViewById(R.id.topbar);
        sideRail = findViewById(R.id.sideRail);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        epCounter = findViewById(R.id.epCounter);
        status = findViewById(R.id.status);
        titleBar = findViewById(R.id.title);
        subtitleBar = findViewById(R.id.subtitle);
        modeBadge = findViewById(R.id.modeBadge);
        qualityBadge = findViewById(R.id.uiQualityBadge);

        syncTitle();
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnPrev.setOnClickListener(v -> gotoEpisode(epIndex - 1));
        btnNext.setOnClickListener(v -> gotoEpisode(epIndex + 1));

        // Kecepatan putar, daftar episode, dan ganti server (ala AL).
        btnSpeed = findViewById(R.id.btnSpeed);
        btnEps = findViewById(R.id.btnEps);
        btnServer = findViewById(R.id.btnServer);
        if (btnSpeed != null) {
            syncSpeedLabel();
            btnSpeed.setOnClickListener(v -> cycleSpeed());
        }
        if (btnEps != null) btnEps.setOnClickListener(v -> showEpisodePicker());
        if (btnServer != null) btnServer.setOnClickListener(v -> switchServer());

        // Preferensi pemutar: aspect ratio & label kualitas (Setelan → Player).
        applyRatio();
        syncQualityLabel();

        // Kontrol bawaan ExoPlayer punya tombol prev/next di TENGAH — disembunyikan
        // supaya tidak dobel dengan rel episode di sisi kanan. Rel + bilah atas
        // mengikuti visibilitas kontrol (muncul saat video diketuk, ala YouTube).
        hideDefaultPrevNext();
        playerView.setControllerVisibilityListener(
                new PlayerView.ControllerVisibilityListener() {
                    @Override
                    public void onVisibilityChanged(int visibility) {
                        if (topbar != null && exoStarted) topbar.setVisibility(visibility);
                        if (sideRail != null && railReady) sideRail.setVisibility(visibility);
                    }
                });

        store = new HistoryStore(this);
        newEpisodeState();

        setupWebView();
        updateRail();
        loadSource();
        fetchEpisodeList();
        tick.postDelayed(ticker, 3000);
        tick.postDelayed(fallbackRun, 12000);
    }

    // ------------------------------------------------------------------ intent

    private static String nz(String s) { return s == null ? "" : s; }

    // -------------------------------------------------------- preferensi player

    /** Terapkan aspect ratio sesuai pilihan di Setelan (Fit / Fill / Zoom). */
    private void applyRatio() {
        if (playerView == null) return;
        int ratio = Prefs.playerRatio(this);
        int mode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
        if (ratio == Prefs.RATIO_FILL) mode = AspectRatioFrameLayout.RESIZE_MODE_FILL;
        else if (ratio == Prefs.RATIO_ZOOM) mode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
        playerView.setResizeMode(mode);
    }

    /**
     * Tampilkan kualitas yang dipilih di Setelan sebagai label preferensi.
     * Sumber video menentukan kualitas akhir — label ini tidak mengaku
     * melakukan transcode ulang.
     */
    private void syncQualityLabel() {
        if (qualityBadge == null) return;
        int q = Prefs.playerQuality(this);
        String label;
        switch (q) {
            case Prefs.QUALITY_360:  label = getString(R.string.quality_360p); break;
            case Prefs.QUALITY_480:  label = getString(R.string.quality_480p); break;
            case Prefs.QUALITY_1080: label = getString(R.string.quality_1080p); break;
            case Prefs.QUALITY_720:
            default:                 label = getString(R.string.quality_720p); break;
        }
        qualityBadge.setText(getString(R.string.quality_badge, label));
        qualityBadge.setVisibility(exoStarted ? View.VISIBLE : View.GONE);
    }

    @SuppressWarnings("unchecked")
    private void readEpisodeList() {
        ArrayList<String> urls = getIntent().getStringArrayListExtra("epUrlList");
        ArrayList<String> titles = getIntent().getStringArrayListExtra("epTitleList");
        if (urls != null && !urls.isEmpty()) {
            epUrls.clear();
            epUrls.addAll(urls);
            if (titles != null) {
                epTitles.clear();
                epTitles.addAll(titles);
            }
            epIndex = getIntent().getIntExtra("epIndex", -1);
        }
    }

    /** Daftar episode belum ada (dibuka dari Riwayat) — ambil dari halaman series. */
    private void fetchEpisodeList() {
        if (!epUrls.isEmpty() || seriesUrl.isEmpty()) return;
        Async.go(() -> Oploverz.loadSeries(seriesUrl), new Async.Done<Oploverz.Series>() {
            @Override public void ok(Oploverz.Series s) {
                if (s.episodes.isEmpty()) return;
                epUrls.clear();
                epTitles.clear();
                for (EpisodeItem e : s.episodes) {
                    epUrls.add(e.url);
                    epTitles.add(e.title);
                }
                epIndex = epUrls.indexOf(epUrl);
                updateRail();
            }

            @Override public void err(Throwable t) { updateRail(); }
        });
    }

    // ------------------------------------------------------------------ webview

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setUserAgentString(Net.UA);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setBlockNetworkImage(false);

        web.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageStarted(WebView v, String url, Bitmap favicon) {
                attempts = 0;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                if (exoStarted) return;
                scheduleClick();
            }

            @Nullable @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {
                // Dipanggil di thread latar belakang.
                String u = req.getUrl().toString();
                if (isMediaUrl(u)) captureMedia(u);
                return null;   // biarkan WebView memuat seperti biasa
            }

            @SuppressWarnings("deprecation")
            @Nullable @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, String url) {
                if (isMediaUrl(url)) captureMedia(url);
                return null;
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req,
                                        android.webkit.WebResourceError err) {
                if (req.isForMainFrame()) status.setText(R.string.err_net);
            }
        });
    }

    private static boolean isMediaUrl(String u) {
        if (u == null) return false;
        String x = u.toLowerCase();
        return x.contains("videoplayback")
                || x.endsWith(".mp4") || x.contains(".mp4?")
                || x.endsWith(".m3u8") || x.contains(".m3u8?")
                || x.endsWith(".webm");
    }

    private void captureMedia(final String url) {
        runOnUiThread(() -> {
            if (exoStarted) return;
            mediaUrl = url;
            startExo(url);
        });
    }

    private void scheduleClick() {
        if (exoStarted || attempts >= 8) return;
        attempts++;
        tick.postDelayed(() -> {
            if (exoStarted || isFinishing() || isDestroyed()) return;
            web.evaluateJavascript(CLICK_JS, null);
            scheduleClick();
        }, 1500);
    }

    private void loadSource() {
        status.setText(R.string.loading_player);
        if (epUrl.isEmpty()) {
            status.setText(R.string.err_net);
            return;
        }
        Async.go(() -> Oploverz.loadEpisode(epUrl), new Async.Done<Oploverz.Episode>() {
            @Override public void ok(Oploverz.Episode ep) {
                if (!ep.title.isEmpty()) {
                    epTitle = ep.title;
                    syncTitle();
                    current.epTitle = epTitle;
                }
                if (!ep.thumb.isEmpty() && thumb.isEmpty()) {
                    thumb = ep.thumb;
                    current.thumb = thumb;
                }
                if (!ep.seriesUrl.isEmpty() && seriesUrl.isEmpty()) {
                    seriesUrl = ep.seriesUrl;
                    current.seriesUrl = seriesUrl;
                    fetchEpisodeList();
                }
                if (ep.mirrors.isEmpty()) {
                    web.loadUrl(epUrl);          // cadangan: buka halaman episode penuh
                } else {
                    mirrorList.clear();
                    mirrorList.addAll(ep.mirrors);
                    mirrorIdx = 0;
                    web.loadUrl(ep.mirrors.get(0));
                }
                updateServerButton();
            }

            @Override public void err(Throwable t) {
                if (web != null) web.loadUrl(epUrl);
            }
        });
    }

    // ------------------------------------------------------------------ exoplayer

    private void startExo(String url) {
        if (exoStarted || isFinishing() || isDestroyed()) return;
        exoStarted = true;
        tick.removeCallbacks(fallbackRun);
        // Exo mengambil alih suara — bungkam WebView agar tidak dobel.
        silenceWeb();
        // Halaman mirror (video di iframe Blogger) tidak bisa dibungkam dari
        // JS dokumen atas — navigasi pergi agar audionya mati total. Bila Exo
        // gagal, halaman dimuat ulang di onPlayerError.
        try { web.loadUrl("about:blank"); } catch (Throwable ignored) {}

        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://www.blogger.com/");
        headers.put("User-Agent", Net.UA);

        DefaultHttpDataSource.Factory dsf = new DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(headers)
                .setUserAgent(Net.UA)
                .setConnectTimeoutMs(20000)
                .setReadTimeoutMs(25000);

        try {
            MediaSource ms = new DefaultMediaSourceFactory(dsf)
                    .createMediaSource(MediaItem.fromUri(url));

            player = new ExoPlayer.Builder(this)
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(dsf))
                    .build();

            player.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY && !seekDone) {
                        seekDone = true;
                        if (savedPos > 0) player.seekTo(savedPos);
                        overlay.setVisibility(View.GONE);
                        syncQualityLabel();
                        // Penyembunyian bilah atas diserahkan pada listener
                        // visibilitas kontrol (lihat onCreate).
                    }
                    if (state == Player.STATE_ENDED) onEpisodeFinished();
                }

                @Override public void onPlayerError(androidx.media3.common.PlaybackException e) {
                    // Kembalikan ke WebView bila URL langsung ditolak.
                    releasePlayer();
                    exoStarted = false;
                    if (qualityBadge != null) qualityBadge.setVisibility(View.GONE);
                    modeBadge.setText(R.string.web_mode);
                    modeBadge.setVisibility(View.VISIBLE);
                    web.setVisibility(View.VISIBLE);
                    playerView.setVisibility(View.GONE);
                    topbar.setVisibility(View.VISIBLE);
                    overlay.setVisibility(View.GONE);
                    attempts = 0;
                    // WebView sempat di-pause saat Exo ambil alih — hidupkan lagi
                    // agar suntikan klik-play JS tetap berjalan.
                    try { web.onResume(); } catch (Throwable ignored) {}
                    // Halaman sudah about:blank — muat ulang mirror/episode.
                    try {
                        web.loadUrl(mirrorList.isEmpty() ? epUrl
                                : mirrorList.get(Math.min(mirrorIdx,
                                        Math.max(0, mirrorList.size() - 1))));
                    } catch (Throwable ignored) {}
                    scheduleClick();
                }
            });

            playerView.setPlayer(player);
            player.setMediaSource(ms);
            player.setPlaybackSpeed(Prefs.playerSpeed(this));
            player.prepare();
            player.play();

            web.setVisibility(View.GONE);
            playerView.setVisibility(View.VISIBLE);
            overlay.setVisibility(View.VISIBLE);
            status.setText(R.string.loading_player);
        } catch (Throwable t) {
            exoStarted = false;
            fallbackToWeb();
        }
    }

    /** Selesai ditonton: simpan, lalu lanjut ke episode berikutnya bila autoplay aktif. */
    private void onEpisodeFinished() {
        current.posMs = current.durMs > 0 ? current.durMs : current.posMs;
        persist();
        if (!Prefs.playerAutoplay(this)) return;    // setelan Player → Autoplay OFF
        final int next = epIndex + 1;
        if (epIndex >= 0 && next < epUrls.size()) {
            tick.postDelayed(() -> gotoEpisode(next), 1500);
        }
    }

    private void fallbackToWeb() {
        if (exoStarted || isFinishing() || isDestroyed()) return;
        overlay.setVisibility(View.GONE);
        if (qualityBadge != null) qualityBadge.setVisibility(View.GONE);
        if (mediaUrl == null) {
            modeBadge.setText(R.string.web_mode);
            modeBadge.setVisibility(View.VISIBLE);
        }
    }

    // ------------------------------------------------------------------ navigasi

    /** Pindah ke episode lain tanpa membuat ulang Activity. */
    private void gotoEpisode(int idx) {
        if (idx < 0 || idx >= epUrls.size()) return;
        if (idx == epIndex && exoStarted) return;
        if (isFinishing() || isDestroyed()) return;

        // Simpan episode yang sedang ditonton.
        capture();
        persist();

        releasePlayer();
        exoStarted = false;
        seekDone = false;
        mediaUrl = null;
        attempts = 0;
        tick.removeCallbacks(fallbackRun);
        modeBadge.setVisibility(View.GONE);
        if (qualityBadge != null) qualityBadge.setVisibility(View.GONE);
        // Bungkam suara episode lama sebelum memuat yang baru.
        silenceWeb();
        try { web.onResume(); } catch (Throwable ignored) {}

        epIndex = idx;
        epUrl = nz(epUrls.get(idx));
        epTitle = idx < epTitles.size() ? nz(epTitles.get(idx)) : epTitle;
        savedPos = findResumePos(epUrl);
        webElapsed = savedPos;

        newEpisodeState();
        syncTitle();
        updateRail();

        web.setVisibility(View.VISIBLE);
        playerView.setPlayer(null);
        playerView.setVisibility(View.GONE);
        overlay.setVisibility(View.VISIBLE);
        topbar.setVisibility(View.VISIBLE);
        status.setText(R.string.loading_player);

        attempts = 0;
        loadSource();
        tick.postDelayed(fallbackRun, 12000);
    }

    /** Posisi awal: lanjutkan dari catatan, kecuali sudah hampir selesai. */
    private long findResumePos(String url) {
        for (HistoryItem h : store.all()) {
            if (url != null && url.equals(h.epUrl)) return h.finished() ? 0L : h.posMs;
        }
        return 0L;
    }

    private void newEpisodeState() {
        current = new HistoryItem();
        current.title = title;
        current.epTitle = epTitle;
        current.epUrl = epUrl;
        current.seriesUrl = seriesUrl;
        current.thumb = thumb;
        current.posMs = savedPos;
        current.watchedAt = System.currentTimeMillis();
    }

    private void updateRail() {
        railReady = epIndex >= 0 && epUrls.size() > 1;
        if (!railReady || sideRail == null) {
            if (sideRail != null) sideRail.setVisibility(View.GONE);
            return;
        }

        epCounter.setText(getString(R.string.ep_counter, epIndex + 1, epUrls.size()));

        boolean canPrev = epIndex > 0;
        boolean canNext = epIndex < epUrls.size() - 1;
        btnPrev.setEnabled(canPrev);
        btnNext.setEnabled(canNext);
        btnPrev.setAlpha(canPrev ? 1f : 0.3f);
        btnNext.setAlpha(canNext ? 1f : 0.3f);

        if (btnEps != null) btnEps.setVisibility(View.VISIBLE);
        updateServerButton();

        // Ala YouTube: rel hanya tampil saat kontrol player terlihat.
        // Sebelum Exo jalan (mode WebView) kontrol tak ada — tampilkan langsung.
        boolean controls = !exoStarted
                || (playerView != null && playerView.isControllerFullyVisible());
        sideRail.setVisibility(controls ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------- kecepatan / server / daftar

    /** Label tombol kecepatan ("1×", "1,25×", …). */
    private void syncSpeedLabel() {
        if (btnSpeed == null) return;
        btnSpeed.setText(SPEED_LABELS[speedIndex(Prefs.playerSpeed(this))]);
    }

    /** Indeks kecepatan terdekat dengan nilai tersimpan. */
    private static int speedIndex(float cur) {
        int best = 2;
        float gap = Float.MAX_VALUE;
        for (int i = 0; i < SPEEDS.length; i++) {
            float d = Math.abs(SPEEDS[i] - cur);
            if (d < gap) { gap = d; best = i; }
        }
        return best;
    }

    /** Putar pilihan kecepatan 0,5x–2x; tersimpan dan langsung berlaku. */
    private void cycleSpeed() {
        int next = (speedIndex(Prefs.playerSpeed(this)) + 1) % SPEEDS.length;
        Prefs.setPlayerSpeed(this, SPEEDS[next]);
        if (player != null && exoStarted) player.setPlaybackSpeed(SPEEDS[next]);
        syncSpeedLabel();
    }

    /** Tombol server hanya ada bila episode punya >1 mirror. */
    private void updateServerButton() {
        if (btnServer == null) return;
        boolean multi = mirrorList.size() > 1;
        btnServer.setVisibility(multi ? View.VISIBLE : View.GONE);
        if (multi) btnServer.setText("S" + (mirrorIdx + 1));
    }

    /** Ganti server: muat ulang mirror berikutnya lalu tangkap ulang media. */
    private void switchServer() {
        if (mirrorList.size() <= 1 || isFinishing() || isDestroyed()) return;
        mirrorIdx = (mirrorIdx + 1) % mirrorList.size();

        capture();
        persist();
        releasePlayer();
        exoStarted = false;
        seekDone = false;
        mediaUrl = null;
        attempts = 0;
        tick.removeCallbacks(fallbackRun);
        modeBadge.setVisibility(View.GONE);
        if (qualityBadge != null) qualityBadge.setVisibility(View.GONE);
        // Bungkam suara server lama sebelum memuat yang baru.
        silenceWeb();
        try { web.onResume(); } catch (Throwable ignored) {}

        updateServerButton();
        web.setVisibility(View.VISIBLE);
        playerView.setPlayer(null);
        playerView.setVisibility(View.GONE);
        overlay.setVisibility(View.VISIBLE);
        topbar.setVisibility(View.VISIBLE);
        status.setText(getString(R.string.server_fmt, mirrorIdx + 1));
        updateRail();

        web.loadUrl(mirrorList.get(mirrorIdx));
        tick.postDelayed(fallbackRun, 12000);
    }

    /** Daftar episode ala AL: pilih = pindah episode. */
    private void showEpisodePicker() {
        if (epUrls.isEmpty()) return;
        String[] names = new String[epTitles.size()];
        for (int i = 0; i < epTitles.size(); i++) {
            String t = epTitles.get(i);
            names[i] = (t == null || t.isEmpty()) ? ("Episode " + (i + 1)) : t;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.episode_list)
                .setItems(names, (d, which) -> gotoEpisode(which))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Judul = nama anime; subjudul = episode. Info tampil di bilah atas. */
    private void syncTitle() {
        if (titleBar != null) titleBar.setText(title.isEmpty() ? epTitle : title);
        if (subtitleBar != null) {
            subtitleBar.setText(epTitle);
            subtitleBar.setVisibility(epTitle.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    /** Sembunyikan tombol prev/next bawaan kontrol Exo (tengah) — rel kanan
     *  sudah mewakilinya sehingga tidak dobel. */
    private void hideDefaultPrevNext() {
        if (playerView == null) return;
        try {
            View prev = playerView.findViewById(androidx.media3.ui.R.id.exo_prev);
            View next = playerView.findViewById(androidx.media3.ui.R.id.exo_next);
            if (prev != null) prev.setVisibility(View.GONE);
            if (next != null) next.setVisibility(View.GONE);
        } catch (Throwable ignored) {
            // Id internal berubah di versi Media3 lain — bukan fatal.
        }
    }

    // ------------------------------------------------------------------ chrome

    // ------------------------------------------------------------------ riwayat

    private void persist() {
        if (current == null || current.epUrl.isEmpty()) return;
        if (current.posMs < 1000) return;    // jangan simpan kunjungan sesaat
        final HistoryItem snapshot = current;
        Async.go(() -> { store.save(snapshot); return null; }, new Async.Done<Void>() {
            @Override public void ok(Void r) {}
            @Override public void err(Throwable t) {}
        });
    }

    // ------------------------------------------------------------------ lifecycle

    /**
     * Bungkam WebView: hentikan media (video/audio) yang sedang berbunyi.
     * `stopLoading()` saja TIDAK menghentikan media yang sudah berputar —
     * tanpa ini suara WebView dobel dengan ExoPlayer saat pindah
     * episode/server/menu.
     */
    private void silenceWeb() {
        if (web == null) return;
        try {
            web.loadUrl("javascript:(function(){"
                    + "try{document.querySelectorAll('video,audio')"
                    + ".forEach(function(m){m.pause();});}catch(e){}})()");
        } catch (Throwable ignored) {
        }
        try { web.onPause(); } catch (Throwable ignored) {}
        try { web.stopLoading(); } catch (Throwable ignored) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        capture();
        persist();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Hidupkan lagi timer WebView hanya dalam mode WebView (belum Exo).
        // Dalam mode Exo, WebView tetap pause agar tidak bunyi dobel.
        if (!exoStarted && web != null) {
            try { web.onResume(); } catch (Throwable ignored) {}
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        capture();
        persist();
        if (player != null) player.pause();
        // Keluar player/menu lain: pastikan tidak ada suara tertinggal.
        silenceWeb();
    }

    private void capture() {
        if (current == null) return;
        if (player != null) {
            long p = player.getCurrentPosition();
            long d = player.getDuration();
            if (p > 0) current.posMs = p;
            if (d > 0 && d != androidx.media3.common.C.TIME_UNSET) current.durMs = d;
        }
        current.watchedAt = System.currentTimeMillis();
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.release(); } catch (Throwable ignored) {}
            player = null;
        }
        if (playerView != null) playerView.setPlayer(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        tick.removeCallbacksAndMessages(null);
        releasePlayer();
        if (web != null) {
            try {
                silenceWeb();
                web.loadUrl("about:blank");
                android.view.ViewGroup p =
                        (android.view.ViewGroup) web.getParent();
                if (p != null) p.removeView(web);
                web.destroy();
            } catch (Throwable ignored) {}
            web = null;
        }
    }
}
