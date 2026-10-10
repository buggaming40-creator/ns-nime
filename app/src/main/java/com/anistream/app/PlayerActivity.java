package com.anistream.app;

import android.annotation.SuppressLint;
import android.app.PictureInPictureParams;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.util.Rational;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import java.util.List;
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
    private View overlay, topbar;
    private View videoBox, portraitScroll;
    private EpisodeAdapter epAdapter;
    private com.google.android.material.button.MaterialButton
            btnSpeedP, btnServerP, btnDownloadP;
    /** Kunci orientasi via tombol fullscreen (kembali sensor saat dilepas). */
    private int lastOrientationReq =
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR;
    private TextView status, titleBar, subtitleBar, modeBadge;
    private TextView infoTitle, infoSub, epListTitle;
    /** Rel episode siap (lebih dari 1 episode) — tampil hanya saat kontrol terlihat. */
    private boolean railReady;
    /** Kunci layar aktif — sentuhan video diabaikan, kontrol disembunyikan. */
    private boolean locked;
    /** Upaya auto-pindah server setelah error (tiap mirror dicoba sekali). */
    private int autoServerTries;

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
        hideSystemBars();

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
        videoBox = findViewById(R.id.videoBox);
        portraitScroll = findViewById(R.id.portraitScroll);
        status = findViewById(R.id.status);
        titleBar = findViewById(R.id.title);
        subtitleBar = findViewById(R.id.subtitle);
        infoTitle = findViewById(R.id.infoTitle);
        infoSub = findViewById(R.id.infoSub);
        epListTitle = findViewById(R.id.epListTitle);
        modeBadge = findViewById(R.id.modeBadge);

        syncTitle();
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Pil potret berlabel jelas (tap = dialog pilihan, ala Animok).
        btnSpeedP = findViewById(R.id.btnSpeedP);
        btnServerP = findViewById(R.id.btnServerP);
        btnDownloadP = findViewById(R.id.btnDownloadP);
        if (btnSpeedP != null) {
            syncSpeedLabel();
            btnSpeedP.setOnClickListener(v -> showSpeedSheet());
        }
        if (btnServerP != null) btnServerP.setOnClickListener(v -> showServerSheet());
        if (btnDownloadP != null) {
            btnDownloadP.setOnClickListener(v -> downloadCurrent());
        }
        // Fullscreen kini menempel di ujung kanan bar durasi (player_controls).
        View fsPill = findViewById(R.id.btnFsPill);
        if (fsPill != null) fsPill.setOnClickListener(v -> toggleFullscreen());
        View lockBtn = findViewById(R.id.btnLock);
        if (lockBtn != null) lockBtn.setOnClickListener(v -> setLocked(!locked));

        // Daftar episode di bawah video (pengganti komen).
        RecyclerView epList = findViewById(R.id.epList);
        if (epList != null) {
            epAdapter = new EpisodeAdapter(item ->
                    gotoEpisode(epUrls.indexOf(item.url)));
            epList.setLayoutManager(new LinearLayoutManager(this));
            epList.setAdapter(epAdapter);
        }
        watchTopInset();
        applyOrientation(isLandscape());

        // Preferensi pemutar: aspect ratio (Setelan → Player).
        applyRatio();

        // Bilah atas mengikuti kontrol player (muncul saat video diketuk).
        // Tombol prev/next bawaan kontrol dipakai untuk pindah episode
        // (disambung di wireControllerPrevNext saat kontrol tampil).
        playerView.setControllerVisibilityListener(
                new PlayerView.ControllerVisibilityListener() {
                    @Override
                    public void onVisibilityChanged(int visibility) {
                        if (topbar != null && exoStarted) {
                            // Terkunci: topbar tetap tampil agar gembok terjangkau.
                            topbar.setVisibility(locked ? View.VISIBLE : visibility);
                        }
                        if (visibility == View.VISIBLE) wireControllerPrevNext();
                    }
                });

        // Ketuk ganda kiri/kanan = mundur/maju 10 detik (ala YouTube).
        // Kontroler bawaan tetap untuk tombolnya; area video ditangani di sini.
        // hideOnTouch dimatikan agar toggle kontrol hanya dari GestureDetector
        // (kalau dobel, tap sekali bisa batal sendiri: bawaan + manual).
        playerView.setControllerHideOnTouch(false);
        // Tanpa fade: animasi controller pernah terputus (mis. saat rotasi
        // berubah di tengah animasi) sehingga state "visible" membekuk dan
        // kontrol tak pernah dirender ulang. Muncul/hilang langsung = stabil.
        playerView.setControllerAnimationEnabled(false);
        final android.view.GestureDetector taps =
                new android.view.GestureDetector(this,
                        new android.view.GestureDetector.SimpleOnGestureListener() {
                            @Override
                            public boolean onDown(android.view.MotionEvent e) {
                                return true;
                            }

                            @Override
                            public boolean onSingleTapConfirmed(android.view.MotionEvent e) {
                                if (locked) return true;
                                if (playerView != null && exoStarted) {
                                    // Toggle tunggal: tampil ↔ sembunyi.
                                    if (playerView.isControllerFullyVisible()) {
                                        playerView.hideController();
                                    } else {
                                        playerView.showController();
                                    }
                                } else if (topbar != null) {
                                    // Mode web (belum Exo): tap tetap buka/tutup bilah atas.
                                    topbar.setVisibility(topbar.getVisibility() == View.VISIBLE
                                            ? View.GONE : View.VISIBLE);
                                }
                                return true;
                            }

                            @Override
                            public boolean onDoubleTap(android.view.MotionEvent e) {
                                if (locked) return true;
                                if (player == null || !exoStarted
                                        || playerView == null) {
                                    return true;
                                }
                                boolean back = playerView.getWidth() > 0
                                        && e.getX() < playerView.getWidth() / 2f;
                                long pos = player.getCurrentPosition()
                                        + (back ? -10000 : 10000);
                                long dur = player.getDuration();
                                if (dur > 0) pos = Math.min(pos, dur);
                                player.seekTo(Math.max(0, pos));
                                flashSkip(back ? -10 : 10);
                                if (!player.isPlaying()) player.play();
                                playerView.showController();
                                return true;
                            }
                        });
        // JANGAN performClick() di sini: PlayerView.performClick memanggil
        // toggleControllerVisibility() yang — dengan hideOnTouch=false — hanya
        // menampilkan, sehingga bentrok dengan toggle di onSingleTapConfirmed
        // (tiap tap jadi double-toggle dan tak pernah menyembunyikan).
        playerView.setOnTouchListener((v, ev) -> {
            taps.onTouchEvent(ev);
            return true;
        });

        store = new HistoryStore(this);
        newEpisodeState();
        registerBackHandler();

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

    /** Tampilkan kualitas yang dipilih di Setelan sebagai label preferensi.
     * Sumber video menentukan kualitas akhir — label ini tidak mengaku
     * melakukan transcode ulang.
     */

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
        Async.go(() -> Sources.loadSeries(seriesUrl), new Async.Done<Oploverz.Series>() {
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
        Async.go(() -> Sources.loadEpisode(epUrl), new Async.Done<Oploverz.Episode>() {
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
                    // Halaman tanpa player (mis. baris Pembatas) — beri tanda
                    // dulu sebelum dibuka apa adanya.
                    status.setText(R.string.ep_unavailable);
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
                        // Sukses muter: anggaran auto-pindah server diisi ulang.
                        autoServerTries = 0;
                        if (savedPos > 0) player.seekTo(savedPos);
                        overlay.setVisibility(View.GONE);
                        // Penyembunyian bilah atas diserahkan pada listener
                        // visibilitas kontrol (lihat onCreate).
                    }
                    if (state == Player.STATE_ENDED) onEpisodeFinished();
                }

                @Override public void onPlayerError(androidx.media3.common.PlaybackException e) {
                    // Mirror ngadat: coba server lain dulu sebelum menyerah ke mode web.
                    if (!mirrorList.isEmpty() && mirrorList.size() > 1
                            && autoServerTries < mirrorList.size() - 1
                            && !isFinishing() && !isDestroyed()) {
                        autoServerTries++;
                        int next = (mirrorIdx + 1) % mirrorList.size();
                        String from = serverLabel(mirrorIdx);
                        switchToServer(next);
                        status.setText(getString(R.string.server_auto_fmt,
                                from, serverLabel(next)));
                        return;
                    }
                    // Semua server dicoba — kembalikan ke WebView.
                    releasePlayer();
                    exoStarted = false;
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
        autoServerTries = 0;
        if (locked) {
            locked = false;
            syncLockIcon();
        }
        if (playerView != null) playerView.setUseController(true);
        tick.removeCallbacks(fallbackRun);
        modeBadge.setVisibility(View.GONE);
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
        syncEpList();
        syncPillStates();
        wireControllerPrevNext();
    }

    /** Sinkronkan pil potret: label + aktif/rentang sesuai ketersediaan. */
    private void syncPillStates() {
        if (btnServerP != null) {
            btnServerP.setVisibility(mirrorList.size() > 1 ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Sambungkan tombol prev/next TENGAH bawaan kontrol Exo ke pindah episode
     * (ala YouTube playlist). Kontrol di-inflate malas — pasang ulang tiap
     * kontrol tampil. Tanpa daftar episode, biarkan perilaku bawaan.
     */
    private void wireControllerPrevNext() {
        if (playerView == null) return;
        try {
            // Bersihkan kontrol bawaan: tanpa putar-ulang/maju & tanpa gir.
            // (Catatan: id rewind/ffwd bawaan memakai akhiran _with_amount.)
            hideExoId("exo_rew_with_amount");
            hideExoId("exo_ffwd_with_amount");
            hideExo(androidx.media3.ui.R.id.exo_settings);
            if (!railReady) return;
            View prev = playerView.findViewById(
                    androidx.media3.ui.R.id.exo_prev);
            View next = playerView.findViewById(
                    androidx.media3.ui.R.id.exo_next);
            if (prev != null) {
                shrinkExoButton(prev);
                prev.setOnClickListener(v -> gotoEpisode(epIndex - 1));
                prev.setEnabled(epIndex > 0);
                prev.setAlpha(epIndex > 0 ? 1f : 0.3f);
            }
            if (next != null) {
                shrinkExoButton(next);
                next.setOnClickListener(v -> gotoEpisode(epIndex + 1));
                boolean can = epIndex < epUrls.size() - 1;
                next.setEnabled(can);
                next.setAlpha(can ? 1f : 0.3f);
            }
        } catch (Throwable ignored) {
            // Id internal berubah di versi Media3 lain — bukan fatal.
        }
    }

    /** Sembunyikan tombol kontrol bawaan (rew/ffwd/ gir setelan). */
    private void hideExo(int id) {
        try {
            View v = playerView.findViewById(id);
            if (v != null) v.setVisibility(View.GONE);
        } catch (Throwable ignored) {
        }
    }

    /** Sembunyikan berdasarkan NAMA id (untuk id dengan akhiran jumlah). */
    private void hideExoId(String name) {
        try {
            // Resource library sudah merge ke paket aplikasi saat runtime.
            int id = getResources().getIdentifier(name, "id", getPackageName());
            if (id != 0) {
                View v = playerView.findViewById(id);
                if (v != null) v.setVisibility(View.GONE);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Kecilkan tombol prev/next tengah agar tidak dominan. */
    private void shrinkExoButton(View v) {
        try {
            android.view.ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp != null) {
                int s = dp(40);
                lp.width = s;
                lp.height = s;
                v.setLayoutParams(lp);
            }
            if (v instanceof android.widget.ImageButton) {
                ((android.widget.ImageButton) v).setPadding(
                        dp(9), dp(9), dp(9), dp(9));
            }
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------- orientasi potret/lanskap

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyOrientation(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE);
    }

    /** Potret: info + aksi + daftar. Lanskap: video penuh. */
    private void applyOrientation(boolean landscape) {
        if (portraitScroll != null) {
            portraitScroll.setVisibility(landscape ? View.GONE : View.VISIBLE);
        }
        applyVideoBoxLp();
        updateRail();
        // Lebar berubah saat rotasi → tinggi daftar episode harus diukur ulang.
        if (!landscape) Utils.fitRecycler(findViewById(R.id.epList));
    }

    /** Tinggi status bar + poni untuk mode potret (diukur live via insets,
     * diperbarui otomatis bila berubah). */
    private int portraitTopPx = -1;

    /**
     * Ukur ruang atas presisi (status bar + poni) lewat listener insets resmi
     * — jalan di semua API dan ikut berubah bila konfigurasi berubah.
     * Dipanggil sekali; hasilnya dipakai applyOrientation().
     */
    private void watchTopInset() {
        if (videoBox == null) return;
        portraitTopPx = statusBarHeight() + dp(2);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
                videoBox, (v, insets) -> {
                    int cut = 0, sb = 0;
                    try {
                        cut = insets.getInsets(
                                androidx.core.view.WindowInsetsCompat.Type
                                        .displayCutout()).top;
                        sb = insets.getInsets(
                                androidx.core.view.WindowInsetsCompat.Type
                                        .statusBars()).top;
                    } catch (Throwable ignored) {
                    }
                    int want = Math.max(sb, cut) + dp(2);
                    if (want != portraitTopPx) {
                        portraitTopPx = want;
                        if (!isLandscape()) applyVideoBoxLp();
                    }
                    return insets;
                });
        androidx.core.view.ViewCompat.requestApplyInsets(videoBox);
    }

    /** Nilai ruang atas saat ini (fallback bila listener belum jalan). */
    private int portraitTop() {
        return portraitTopPx < 0 ? statusBarHeight() + dp(2) : portraitTopPx;
    }

    /** Terapkan ukuran videoBox sesuai orientasi (dipakai ulang listener). */
    private void applyVideoBoxLp() {
        if (videoBox == null) return;
        boolean landscape = isLandscape();
        android.view.ViewGroup.LayoutParams lp = videoBox.getLayoutParams();
        if (landscape) {
            lp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        } else {
            lp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        videoBox.setLayoutParams(lp);
        // Strip atas + root sewarna window_bg (nyatu sama bawah), video tetap hitam.
        if (lp instanceof android.widget.LinearLayout.LayoutParams) {
            ((android.widget.LinearLayout.LayoutParams) lp).topMargin = 0;
            videoBox.setLayoutParams(lp);
        }
        View rootFrame = findViewById(R.id.rootFrame);
        if (rootFrame != null) {
            rootFrame.setBackgroundColor(landscape ? 0xFF000000
                    : getResources().getColor(R.color.window_bg, getTheme()));
        }
        View contentRoot = findViewById(R.id.contentRoot);
        if (contentRoot != null) {
            contentRoot.setPadding(0, landscape ? 0 : portraitTop(), 0, 0);
        }
    }

    /** Tinggi status bar (tetap ada walau disembunyikan imersif). */
    private int statusBarHeight() {
        int id = getResources().getIdentifier(
                "status_bar_height", "dimen", "android");
        if (id > 0) {
            try {
                return getResources().getDimensionPixelSize(id);
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    /** Rel sisi menetap di root (hanya lanskap); pil potret statis di XML. */

    /** Kilatan "−10 detik" / "+10 detik" di tengah video. */
    private void flashSkip(int seconds) {
        TextView flash = findViewById(R.id.skipFlash);
        if (flash == null) return;
        flash.setText(seconds < 0 ? "−10 detik" : "+10 detik");
        flash.setVisibility(View.VISIBLE);
        flash.removeCallbacks(hideFlash);
        flash.postDelayed(hideFlash, 700);
    }

    private final Runnable hideFlash = new Runnable() {
        @Override public void run() {
            TextView flash = findViewById(R.id.skipFlash);
            if (flash != null) flash.setVisibility(View.GONE);
        }
    };

    /** Kunci/buka kunci layar: terkunci = tap video diabaikan, kontrol disembunyi. */
    private void setLocked(boolean v) {
        if (isFinishing() || isDestroyed()) return;
        locked = v;
        syncLockIcon();
        if (playerView != null) {
            if (locked) {
                playerView.hideController();
                playerView.setUseController(false);
            } else if (exoStarted) {
                playerView.setUseController(true);
                playerView.showController();
            }
        }
        if (locked && topbar != null && exoStarted) {
            topbar.setVisibility(View.VISIBLE);
        }
        Toast.makeText(this, locked ? R.string.screen_locked
                : R.string.screen_unlocked, Toast.LENGTH_SHORT).show();
    }

    /** Ikon gembok mengikuti status kunci. */
    private void syncLockIcon() {
        if (isFinishing() || isDestroyed()) return;
        ImageButton b = findViewById(R.id.btnLock);
        if (b == null) return;
        b.setImageResource(locked ? R.drawable.ic_lock : R.drawable.ic_lock_open);
        b.setContentDescription(getString(locked ? R.string.unlock : R.string.lock));
    }

    /** Tombol fullscreen: kunci lanskap / kembali mengikuti sensor. */
    private void toggleFullscreen() {
        boolean toLand = !isLandscape();
        lastOrientationReq = toLand
                ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR;
        setRequestedOrientation(lastOrientationReq);
    }

    @Override
    public void onBackPressed() {
        // JANGAN dipakai: targetSdk 33+ memakai predictive back sehingga
        // metode ini tidak dipanggil sistem. Lihat registerBackHandler().
        super.onBackPressed();
    }

    /**
     * Back modern (OnBackPressedDispatcher — wajib di targetSdk 33+ karena
     * onBackPressed() warisan tidak lagi dipanggil). Lanskap terkunci:
     * kembali ke potret dulu, bukan keluar.
     */
    private void registerBackHandler() {
        getOnBackPressedDispatcher().addCallback(
                this, new androidx.activity.OnBackPressedCallback(true) {
                    @Override public void handleOnBackPressed() {
                        if (isLandscape() && lastOrientationReq
                                != android.content.pm.ActivityInfo
                                        .SCREEN_ORIENTATION_SENSOR) {
                            lastOrientationReq = android.content.pm.ActivityInfo
                                    .SCREEN_ORIENTATION_SENSOR;
                            setRequestedOrientation(lastOrientationReq);
                            return;
                        }
                        setEnabled(false);
                        getOnBackPressedDispatcher().onBackPressed();
                    }
                });
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** Daftar episode di bawah video (pengganti komen ala AL). */
    private void syncEpList() {
        if (epListTitle != null) {
            epListTitle.setText(epUrls.isEmpty()
                    ? getString(R.string.all_episodes)
                    : getString(R.string.episode_count_fmt, epUrls.size()));
        }
        if (epAdapter == null) return;
        List<EpisodeItem> items = new ArrayList<>();
        for (int i = 0; i < epUrls.size(); i++) {
            String t = i < epTitles.size() ? epTitles.get(i) : "";
            EpisodeItem e = new EpisodeItem();
            e.url = epUrls.get(i);
            e.title = t == null ? "" : t;
            // Pil menampilkan NOMOR episode asli (bukan posisi daftar).
            e.num = epNumOf(e.title, i + 1);
            items.add(e);
        }
        epAdapter.submit(items);
        // Tinggi dipaksa = konten penuh (epList dalam NestedScrollView sering terpotong spek ukur).
        Utils.fitRecycler(findViewById(R.id.epList));
    }

    /** Ambil angka episode terakhir dari judul ("… Episode 12" → "12"). */
    private static String epNumOf(String title, int fallback) {
        if (title != null) {
            java.util.regex.Matcher m =
                    java.util.regex.Pattern.compile("(\\d+)").matcher(title);
            String last = null;
            while (m.find()) last = m.group(1);
            if (last != null) return last;
        }
        return String.valueOf(fallback);
    }

    // ------------------------------------------------- kecepatan / server / daftar

    /** Label tombol kecepatan ("1×", "1,25×", …). */
    private void syncSpeedLabel() {
        String label = SPEED_LABELS[speedIndex(Prefs.playerSpeed(this))];
        if (btnSpeedP != null) btnSpeedP.setText(label);
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

    /** Pilihan kecepatan 0,5x–2x ala Animok; tersimpan dan langsung berlaku. */
    private void showSpeedSheet() {
        if (isFinishing() || isDestroyed()) return;
        int cur = speedIndex(Prefs.playerSpeed(this));
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.speed_title)
                .setSingleChoiceItems(SPEED_LABELS, cur, (d, which) -> {
                    Prefs.setPlayerSpeed(this, SPEEDS[which]);
                    if (player != null && exoStarted) {
                        player.setPlaybackSpeed(SPEEDS[which]);
                    }
                    syncSpeedLabel();
                    d.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Putar pilihan kecepatan 0,5x–2x (rel lanskap: tap = ganti berikutnya). */

    /** Tombol server hanya ada bila episode punya >1 mirror. */
    private void updateServerButton() {
        boolean multi = mirrorList.size() > 1;
        if (btnServerP != null) {
            btnServerP.setVisibility(multi ? View.VISIBLE : View.GONE);
            if (multi) btnServerP.setText(serverLabel(mirrorIdx));
        }
    }

    /** Nama server dari host mirror ("Blogger", "Dood", …). */
    private String serverLabel(int idx) {
        if (idx < 0 || idx >= mirrorList.size()) return "S" + (idx + 1);
        String u = mirrorList.get(idx);
        try {
            String host = new java.net.URI(u).getHost();
            if (host == null) return "S" + (idx + 1);
            if (host.contains("blogger") || host.contains("blogspot")) return "Blogger";
            if (host.contains("dood")) return "Dood";
            if (host.contains("gofile")) return "GoFile";
            if (host.startsWith("www.")) host = host.substring(4);
            int dot = host.indexOf('.');
            String name = dot > 0 ? host.substring(0, dot) : host;
            if (name.isEmpty()) return "S" + (idx + 1);
            return name.substring(0, 1).toUpperCase() + name.substring(1);
        } catch (Throwable t) {
            return "S" + (idx + 1);
        }
    }

    /** Daftar server ala Animok ("Choose server"); pilih = muat ulang. */
    private void showServerSheet() {
        if (isFinishing() || isDestroyed() || mirrorList.size() <= 1) return;
        String[] names = new String[mirrorList.size()];
        for (int i = 0; i < names.length; i++) names[i] = serverLabel(i);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.server_title)
                .setSingleChoiceItems(names, mirrorIdx, (d, which) -> {
                    d.dismiss();
                    if (which != mirrorIdx) {
                        autoServerTries = 0;
                        if (locked) {
                            locked = false;
                            syncLockIcon();
                        }
                        if (playerView != null) playerView.setUseController(true);
                        switchToServer(which);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Info kualitas jujur: preferensi + sumber tunggal (tanpa transcode). */

    /** Ganti server: muat ulang mirror berikutnya lalu tangkap ulang media. */
    private void switchServer() {
        if (mirrorList.size() <= 1 || isFinishing() || isDestroyed()) return;
        autoServerTries = 0;
        if (locked) {
            locked = false;
            syncLockIcon();
        }
        if (playerView != null) playerView.setUseController(true);
        switchToServer((mirrorIdx + 1) % mirrorList.size());
    }

    /** Muat ulang mirror ke-idx lalu tangkap ulang media. */
    private void switchToServer(int idx) {
        if (idx < 0 || idx >= mirrorList.size()
                || isFinishing() || isDestroyed()) return;
        mirrorIdx = idx;

        capture();
        persist();
        releasePlayer();
        exoStarted = false;
        seekDone = false;
        mediaUrl = null;
        attempts = 0;
        tick.removeCallbacks(fallbackRun);
        modeBadge.setVisibility(View.GONE);
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

    /** Unduh episode ini via tautan GoFile situs (dibuka di peramban). */
    private void downloadCurrent() {
        if (epUrl == null || epUrl.isEmpty() || isFinishing() || isDestroyed()) return;
        Toast.makeText(this, R.string.loading, Toast.LENGTH_SHORT).show();
        Async.go(() -> Sources.loadEpisode(epUrl),
                new Async.Done<Oploverz.Episode>() {
                    @Override public void ok(Oploverz.Episode ep) {
                        if (isFinishing() || isDestroyed()) return;
                        if (ep != null && !ep.downloadUrl.isEmpty()) {
                            try {
                                startActivity(new android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(ep.downloadUrl)));
                            } catch (Throwable t) {
                                Toast.makeText(PlayerActivity.this,
                                        R.string.err_net, Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(PlayerActivity.this,
                                    R.string.no_download, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override public void err(Throwable t) {
                        if (isFinishing() || isDestroyed()) return;
                        Toast.makeText(PlayerActivity.this,
                                R.string.err_net, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** Daftar episode ala AL: pilih = pindah episode. */

    /** Judul = nama anime; subjudul = episode. Info tampil di bilah atas. */
    private void syncTitle() {
        String main = title.isEmpty() ? epTitle : title;
        if (titleBar != null) titleBar.setText(main);
        if (subtitleBar != null) {
            subtitleBar.setText(epTitle);
            subtitleBar.setVisibility(epTitle.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (infoTitle != null) infoTitle.setText(main);
        if (infoSub != null) {
            infoSub.setText(epTitle);
            infoSub.setVisibility(epTitle.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    /** Sembunyikan tombol prev/next bawaan kontrol Exo (tengah) — rel kanan
     *  sudah mewakilinya sehingga tidak dobel. */

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

    /**
     * Keluar player (Home / pindah app) selagi video berputar → masuk
     * mini-player PiP bila setelan menyala. Mode web tidak ikut (tanpa Exo).
     */
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        try {
            if (Prefs.playerPip(this) && exoStarted && player != null
                    && player.isPlaying()) {
                enterPictureInPictureMode(new PictureInPictureParams.Builder()
                        .setAspectRatio(new Rational(16, 9))
                        .build());
            }
        } catch (Throwable ignored) {
            // Perangkat tanpa PiP — tetap di player biasa.
        }
    }

    /**
     * Mode PiP: sembunyikan bilah atas + rel + lencana dan kunci kontrol;
     * keluar PiP = kembalikan semuanya seperti semula.
     */
    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode,
                                              Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            if (topbar != null) topbar.setVisibility(View.GONE);
            if (modeBadge != null) modeBadge.setVisibility(View.GONE);
            if (playerView != null) playerView.setUseController(false);
        } else {
            if (playerView != null) playerView.setUseController(true);
            if (topbar != null) topbar.setVisibility(View.VISIBLE);
            updateRail();
        }
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
