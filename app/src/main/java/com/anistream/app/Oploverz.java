package com.anistream.app;

import android.util.Base64;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser situs Oploverz (theme dramastream / themesia).
 *
 * Pemetaan selector — hasil verifikasi langsung terhadap situs:
 *   - Daftar kartu : <article> > div.bsx > a[href] + img + h2 + span.epx + div.typez + span.sb
 *   - Pencarian    : {BASE}/?s={query}  -> struktur kartu yang sama
 *   - Halaman      : div.headlist (thumb/judul/status) + div.episodelist ul li a (daftar episode)
 *   - Mirror       : <select class="mirror"><option value="{base64 dari tag iframe}">
 *                    dan/atau <div id="embed_holder"><div class="player-embed"><iframe src>
 */
public final class Oploverz {

    public static final String BASE = "https://oploverz.ch";

    private static final Pattern IFRAME_SRC = Pattern.compile("src\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern EPISODE_NUM = Pattern.compile("(?i)episode\\s*0*(\\d+)");

    private Oploverz() {}

    // ------------------------------------------------------------------ model

    /** Hasil parse halaman series: judul, sampul, status, dan daftar episode. */
    public static class Series {
        public String title = "";
        public String thumb = "";
        public String status = "";
        public String statusWord = "";
        public String studio = "";
        public String type = "";
        public String released = "";
        public String synopsis = "";
        public final List<String> genres = new ArrayList<>();
        public String seriesUrl = "";
        public List<EpisodeItem> episodes = new ArrayList<>();
    }

    /** Hasil parse halaman episode: metadata + daftar URL mirror player. */
    public static class Episode {
        public String title = "";
        public String thumb = "";
        public String seriesUrl = "";
        public String downloadUrl = "";
        public final List<String> mirrors = new ArrayList<>();
    }

    // ------------------------------------------------------------------ kartu

    /** Rilis terbaru (halaman depan). */
    public static List<AnimeItem> latest() throws Exception {
        return parseCards(Jsoup.connect(BASE + "/")
                .userAgent(Net.UA).timeout(20000).get());
    }

    /** Pencarian judul. */
    public static List<AnimeItem> search(String q) throws Exception {
        String url = BASE + "/?s=" + URLEncoder.encode(q == null ? "" : q.trim(), "UTF-8");
        return parseCards(Jsoup.connect(url).userAgent(Net.UA).timeout(20000).get());
    }

    private static List<AnimeItem> parseCards(Document doc) {
        List<AnimeItem> out = new ArrayList<>();
        Elements arts = doc.select("article");
        for (Element art : arts) {
            Element box = art.selectFirst("div.bsx");
            if (box == null) box = art;

            Element a = box.selectFirst("a[href]");
            if (a == null) continue;

            String url = abs(a.attr("href"));
            if (url.isEmpty()) continue;

            String title = a.hasAttr("title") ? a.attr("title").trim() : "";
            if (title.isEmpty()) {
                Element h = box.selectFirst("h2, h3, .tt, .title");
                if (h != null) title = h.text().trim();
            }
            if (title.isEmpty()) continue;

            String thumb = imgSrc(box);

            StringBuilder meta = new StringBuilder();
            Element type = box.selectFirst(".typez, .type");
            if (type != null && !type.text().trim().isEmpty()) meta.append(type.text().trim());
            Element sb = box.selectFirst("span.sb, .sb");
            if (sb != null && !sb.text().trim().isEmpty()) {
                if (meta.length() > 0) meta.append(" · ");
                meta.append(sb.text().trim());
            }
            Element epx = box.selectFirst("span.epx, .epx, span.episode");
            if (epx != null && !epx.text().trim().isEmpty()) {
                if (meta.length() > 0) meta.append(" · ");
                meta.append(epx.text().trim());
            }

            out.add(new AnimeItem(title, url, thumb, meta.toString()));
        }
        return out;
    }

    // ------------------------------------------------------------------ series

    /** Daftar episode untuk sebuah judul. `url` boleh halaman series maupun halaman episode. */
    public static Series loadSeries(String url) throws Exception {
        Document doc = Jsoup.connect(url).userAgent(Net.UA).timeout(20000).get();
        Series s = parseSeries(doc, url);
        Document seriesDoc = doc;

        // Halaman episode tidak memuat info lengkap (sinopsis/genre) — ikuti
        // tautan series-nya lalu isi semua field yang masih kosong.
        if (!s.seriesUrl.isEmpty() && !samePage(s.seriesUrl, url)) {
            seriesDoc = Jsoup.connect(s.seriesUrl).userAgent(Net.UA).timeout(20000).get();
            Series full = parseSeries(seriesDoc, s.seriesUrl);
            if (s.episodes.isEmpty() && !full.episodes.isEmpty()) {
                s.episodes = full.episodes;
            }
            if (s.title.isEmpty()) s.title = full.title;
            if (s.thumb.isEmpty()) s.thumb = full.thumb;
            if (s.status.isEmpty()) s.status = full.status;
            if (s.statusWord.isEmpty()) s.statusWord = full.statusWord;
            if (s.studio.isEmpty()) s.studio = full.studio;
            if (s.type.isEmpty()) s.type = full.type;
            if (s.released.isEmpty()) s.released = full.released;
            if (s.synopsis.isEmpty()) s.synopsis = full.synopsis;
            if (s.genres.isEmpty()) s.genres.addAll(full.genres);
        }
        fillEpRemainingPages(s, seriesDoc);
        return s;
    }

    /** Versi ringan tanpa follow (cukup untuk cek status ongoing). */
    public static Series loadSeriesLite(String url) throws Exception {
        return parseSeries(Jsoup.connect(url).userAgent(Net.UA).timeout(20000).get(), url);
    }

    /**
     * Item episode: halaman series memakai div.eplister (diprioritaskan agar
     * daftar sidebar tak ikut kecampur), halaman episode memakai div.episodelist
     * / .listeps. Hasil ditambahkan ke `out`; `seen` untuk dedup antar halaman.
     */
    private static void parseEpisodeItems(Document doc, List<EpisodeItem> out, Set<String> seen) {
        Elements lis = doc.select("div.eplister li");
        if (lis.isEmpty()) lis = doc.select("div.episodelist li, .listeps li");
        for (Element li : lis) {
            // Tautan paginasi (?ep_page=N) ikut tersangkut di dalam eplister.
            if (li.parent() != null && li.parent().hasClass("page-numbers")) continue;
            Element a = li.selectFirst("a[href]");
            if (a == null) continue;
            EpisodeItem e = new EpisodeItem();
            e.url = abs(a.attr("href"));
            if (e.url.isEmpty() || e.url.contains("ep_page") || !seen.add(e.url)) continue;

            Element num = li.selectFirst(".epl-num, .epnum");
            Element ttl = li.selectFirst(".epl-title, .playinfo h3, h3");
            Element dat = li.selectFirst(".epl-date, .playinfo span");

            e.num = num != null ? num.text().trim() : "";
            e.title = ttl != null ? ttl.text().trim() : a.attr("title");
            if (e.title.isEmpty()) e.title = a.attr("title");
            e.date = dat != null ? dat.text().trim() : "";
            if (e.num.isEmpty()) e.num = extractEpisodeNumber(e.title, e.url);
            if (e.num.isEmpty() && e.title.isEmpty()) continue; // bukan item episode

            out.add(e);
        }
    }

    /**
     * Oploverz memaginasi daftar episode panjang: halaman 1 hanya ~12 episode
     * terbaru, sisanya lewat `?ep_page=N` (lihat .pagination .page-numbers).
     * Semua halaman diikut agar daftar lengkap (mis. One Piece). Gagal mengambil
     * halaman lanjutan → daftar halaman 1 tetap dipakai.
     */
    private static void fillEpRemainingPages(Series s, Document firstDoc) {
        if (s.seriesUrl.isEmpty() || firstDoc == null) return;
        int max = 1;
        for (Element a : firstDoc.select(".pagination a[href*=ep_page]")) {
            String h = a.attr("href");
            int i = h.indexOf("ep_page=");
            if (i < 0) continue;
            String n = h.substring(i + "ep_page=".length()).replaceAll("[^0-9].*$", "");
            if (n.isEmpty()) continue;
            try {
                max = Math.max(max, Integer.parseInt(n));
            } catch (NumberFormatException ignored) {
            }
        }
        if (max <= 1) return;
        max = Math.min(max, 40); // pengaman bila tautan tak wajar
        String sep = s.seriesUrl.contains("?") ? "&" : "?";
        Set<String> seen = new LinkedHashSet<>();
        for (EpisodeItem e : s.episodes) seen.add(e.url);
        try {
            for (int p = 2; p <= max; p++) {
                Document d = Jsoup.connect(s.seriesUrl + sep + "ep_page=" + p)
                        .userAgent(Net.UA).timeout(20000).get();
                int before = s.episodes.size();
                parseEpisodeItems(d, s.episodes, seen);
                if (s.episodes.size() == before) break; // halaman kosong → berhenti
            }
        } catch (Exception ignored) {
            // sisakan daftar halaman 1
        }
        s.episodes.sort((x, y) -> compareEpisodeDesc(x.num, y.num));
    }

    private static Series parseSeries(Document doc, String pageUrl) {
        Series s = new Series();
        s.seriesUrl = pageUrl;

        // ---- judul --------------------------------------------------------
        Element h1 = doc.selectFirst("h1.entry-title, h1");
        if (h1 != null) s.title = h1.text().replaceAll("(?i)\\s*Subtitle Indonesia\\s*$", "").trim();

        // Tautan series dipakai ketika yang dibuka adalah halaman episode.
        Element h2a = doc.selectFirst("#singlepisode .headlist h2 a[href], .headlist h2 a[href], .headlist a[href*=/series/]");
        if (h2a != null && !h2a.attr("href").isEmpty()) {
            s.seriesUrl = abs(h2a.attr("href"));
            String seriesTitle = h2a.text().trim();
            if (!seriesTitle.isEmpty()) s.title = seriesTitle;
        }
        if (s.seriesUrl.isEmpty() || samePage(s.seriesUrl, pageUrl)) {
            Element sl = doc.selectFirst(".infox h1, a[href*=/series/]");
            if (sl != null && sl.tagName().equals("a")) s.seriesUrl = abs(sl.attr("href"));
        }

        // ---- sampul -------------------------------------------------------
        Element poster = doc.selectFirst("div.thumbook img, #singlepisode .headlist img, div.headlist img");
        if (poster != null) s.thumb = imgAttr(poster);
        if (s.thumb.isEmpty()) {
            Element hr = doc.selectFirst("div.thumbook, #singlepisode .headlist, div.headlist");
            if (hr != null) s.thumb = imgSrc(hr);
        }

        // ---- status / studio / tipe / tahun (div.spe pada halaman series) ----
        Element spe = doc.selectFirst("div.infox div.spe, div.spe");
        if (spe != null) {
            String status = "", statusWord = "", studio = "", type = "",
                    released = "", count = "";
            for (Element sp : spe.select("span")) {
                String t = sp.text().replaceAll("\\s+", " ").trim();
                if (t.startsWith("Status:")) {
                    statusWord = t.substring("Status:".length()).trim();
                    status = statusWord;
                } else if (t.startsWith("Studio:")) {
                    studio = t.substring("Studio:".length()).trim();
                } else if (t.startsWith("Type:")) {
                    type = t.substring("Type:".length()).trim();
                } else if (t.startsWith("Released:")) {
                    released = t.substring("Released:".length()).trim();
                } else if (t.startsWith("Episodes:")) {
                    count = t.substring("Episodes:".length()).trim();
                }
            }
            if (!count.isEmpty()) count = count + " Episode";
            s.statusWord = statusWord;
            s.studio = studio;
            s.type = type;
            s.released = released;
            s.status = status.isEmpty() ? count : (count.isEmpty() ? status : status + " · " + count);
        }
        if (s.status.isEmpty()) {
            Element det = doc.selectFirst(".headlist .det");
            if (det != null) s.status = det.text().replaceAll("\\s+", " ").trim();
        }

        // ---- genre (tautan /genres/) ----------------------------------------
        Set<String> seenGenre = new LinkedHashSet<>();
        for (Element g : doc.select("a[href*=/genres/]")) {
            String t = g.text().replaceAll("\\s+", " ").trim();
            if (!t.isEmpty() && seenGenre.add(t) && s.genres.size() < 8) {
                s.genres.add(t);
            }
        }

        // ---- sinopsis (teks asli situs, bahasa Inggris dari MAL) ---------------
        Element syn = doc.selectFirst("div.synp div.entry-content, div.synp .entry-content");
        if (syn != null) {
            StringBuilder sb = new StringBuilder();
            for (Element p : syn.select("p")) {
                String t = p.text().replaceAll("\\s+", " ").trim();
                if (t.isEmpty() || t.startsWith("(Source")) continue;
                if (sb.length() > 0) sb.append("\n\n");
                sb.append(t);
            }
            s.synopsis = sb.toString().trim();
        }

        // ---- daftar episode ------------------------------------------------
        // Halaman series  : div.eplister ul li  -> .epl-num / .epl-title / .epl-date
        // Halaman episode : div.episodelist li  -> h3 / span
        parseEpisodeItems(doc, s.episodes, new LinkedHashSet<>());

        // Episode terbaru di atas.
        s.episodes.sort((x, y) -> compareEpisodeDesc(x.num, y.num));

        // Halaman episode memakai judul "... Episode N" — buang agar tersisa nama judulnya saja.
        if (!s.episodes.isEmpty() && pageUrl != null && pageUrl.contains("-episode-")) {
            String t = s.title.replaceAll("(?i)\\s+episode\\s+\\d+.*$", "").trim();
            if (!t.isEmpty()) s.title = t;
        }
        return s;
    }

    private static String imgAttr(Element img) {
        if (img == null) return "";
        for (String attr : new String[]{"src", "data-src", "data-lazy-src", "data-original"}) {
            String v = img.attr(attr).trim();
            if (!v.isEmpty() && !v.startsWith("data:")) return abs(v);
        }
        return "";
    }

    // ------------------------------------------------------------------ episode

    /** Ambil metadata halaman episode beserta URL mirror player. */
    public static Episode loadEpisode(String url) throws Exception {
        return parseEpisode(Jsoup.connect(url).userAgent(Net.UA).timeout(20000).get(), url);
    }

    private static Episode parseEpisode(Document doc, String pageUrl) {
        Episode ep = new Episode();

        Element h1 = doc.selectFirst("h1.entry-title, h1");
        if (h1 != null) ep.title = h1.text().replaceAll("(?i)\\s*Subtitle Indonesia\\s*$", "").trim();

        Element head = doc.selectFirst("#singlepisode .headlist, .headlist");
        if (head != null) {
            ep.thumb = imgSrc(head);
            Element h2a = head.selectFirst("h2 a[href]");
            if (h2a != null) ep.seriesUrl = abs(h2a.attr("href"));
        }
        if (ep.seriesUrl.isEmpty()) {
            Element sl = doc.selectFirst("a[href*=/series/]");
            if (sl != null) ep.seriesUrl = abs(sl.attr("href"));
        }

        // 1) Default iframe yang sudah tersemat di halaman.
        Element ifr = doc.selectFirst("#embed_holder iframe, .player-embed iframe, div.video-content iframe");
        if (ifr != null) {
            String src = abs(ifr.attr("src"));
            if (!src.isEmpty()) ep.mirrors.add(src);
        }

        // 2) Pilihan server: tiap <option value> berisi tag iframe dalam base64.
        Elements opts = doc.select("select.mirror option[value]");
        for (Element o : opts) {
            String v = o.attr("value").trim();
            if (v.isEmpty()) continue;
            try {
                String html = new String(Base64.decode(v, Base64.DEFAULT), "UTF-8");
                Matcher m = IFRAME_SRC.matcher(html);
                if (m.find()) {
                    String src = m.group(1).trim();
                    if (!src.isEmpty() && !ep.mirrors.contains(src)) ep.mirrors.add(src);
                }
            } catch (Exception ignored) {
                // Bukan base64 valid — lewati opsi ini.
            }
        }

        // 3) Tautan unduhan (GoFile) bila tersedia.
        Element dl = doc.selectFirst("a[href*=gofile.io]");
        if (dl != null) ep.downloadUrl = abs(dl.attr("href"));
        return ep;
    }

    // ------------------------------------------------------------------ util

    private static String extractEpisodeNumber(String title, String url) {
        Matcher m = EPISODE_NUM.matcher(title != null ? title : "");
        if (m.find()) return m.group(1);
        m = EPISODE_NUM.matcher(url != null ? url : "");
        if (m.find()) return m.group(1);
        return "";
    }

    private static int compareEpisodeDesc(String a, String b) {
        Integer ia = toInt(a), ib = toInt(b);
        if (ia != null && ib != null) return ib.compareTo(ia);
        if (ia != null) return -1;
        if (ib != null) return 1;
        return (b == null ? "" : b).compareTo(a == null ? "" : a);
    }

    private static Integer toInt(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return null; }
    }

    private static boolean samePage(String a, String b) {
        return norm(a).equals(norm(b));
    }

    private static String norm(String u) {
        String x = u == null ? "" : u.trim();
        if (x.endsWith("/")) x = x.substring(0, x.length() - 1);
        return x;
    }

    private static String imgSrc(Element root) {
        if (root == null) return "";
        return imgAttr(root.selectFirst("img"));
    }

    private static String abs(String u) {
        if (u == null) return "";
        u = u.trim();
        if (u.isEmpty()) return "";
        if (u.startsWith("//")) return "https:" + u;
        if (u.startsWith("http")) return u;
        if (u.startsWith("/")) return BASE + u;
        return BASE + "/" + u;
    }
}
