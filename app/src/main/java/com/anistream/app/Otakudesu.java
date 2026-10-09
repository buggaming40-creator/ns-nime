package com.anistream.app;

import android.util.Base64;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scraper Otakudesu (otakudesu.blog) — sumber kedua app.
 * Dipakai bila Oploverz tidak punya judul yang dicari (mis. Black Clover S1).
 * Hasilnya memakai model Oploverz.Series / Oploverz.Episode milik app.
 *
 * Struktur situs (per 10-2026):
 *   search    : /?s=q  -> h2 > a[href*=/anime/] dalam <li> (img poster di li yang sama)
 *   series    : /anime/{slug}/ -> h1, meta og:image, .infozingle (b:Label: nilai),
 *               .sinopc (sinopsis), .episodelist li > a[href*=/episode/]
 *   episode   : /episode/{slug}/ -> h1.posttl, #embed_holder iframe (bawaan),
 *               .mirrorstream a[data-content] -> AJAX admin-ajax.php (kualitas)
 *
 * Kualitas: iframe bawaan halaman = 360p/480p. Pilihan lebih tinggi (720p)
 * diambil lewat 2 POST admin-ajax (nonce lalu fetch, aksi MD5 dibaca dari
 * halaman — jangan di-hardcode). Gagal = pakai iframe bawaan.
 */
public final class Otakudesu {

    public static final String BASE = "https://otakudesu.blog";

    private static final Pattern EPISODE_NUM =
            Pattern.compile("(?i)episode[-\\s]*0*(\\d+)");
    private static final Pattern AJAX_NONCE =
            Pattern.compile("\\{action:\\s*\"([0-9a-f]{16,})\"\\}");
    private static final Pattern AJAX_FETCH =
            Pattern.compile("nonce:\\s*window\\.__x__nonce,\\s*action:\\s*\"([0-9a-f]{16,})\"");
    private static final Pattern IFRAME_SRC =
            Pattern.compile("src\\s*=\\s*\"([^\"]+)\"");

    private Otakudesu() {}

    // ------------------------------------------------------------------ search

    public static List<AnimeItem> search(String q) throws Exception {
        Document doc = Jsoup.connect(BASE + "/?s=" + URLEncoder.encode(q, "UTF-8"))
                .userAgent(Net.UA).timeout(20000).get();
        List<AnimeItem> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Element a : doc.select("h2 a[href]")) {
            String href = a.attr("href").trim();
            if (!href.contains("/anime/")) continue;   // hasil /episode/ dilewati
            href = abs(href);
            if (!seen.add(href)) continue;
            String title = stripSub(a.text());
            if (title.isEmpty()) continue;
            String thumb = "";
            Element li = parentLi(a);
            if (li != null) {
                Element img = li.selectFirst("img");
                if (img != null) thumb = imgAttr(img);
            }
            out.add(new AnimeItem(title, href, thumb, "Otakudesu"));
        }
        return out;
    }

    // ------------------------------------------------------------------ series

    public static Oploverz.Series loadSeries(String url) throws Exception {
        Document doc = connect(url).get();
        String seriesUrl = url;
        if (url.contains("/episode/")) {
            Element sa = doc.selectFirst(".flir a[href*=/anime/], a[href*=/anime/]");
            if (sa != null) {
                seriesUrl = abs(sa.attr("href"));
                doc = connect(seriesUrl).get();
            }
        }
        return fillSeries(doc, seriesUrl);
    }

    /** Versi ringan — halaman series sudah memuat semua yang dibutuhkan. */
    public static Oploverz.Series loadSeriesLite(String url) throws Exception {
        return loadSeries(url);
    }

    private static Oploverz.Series fillSeries(Document doc, String pageUrl) {
        Oploverz.Series s = new Oploverz.Series();
        s.seriesUrl = pageUrl;

        // ---- baris info (.infozingle: <p><span><b>Label</b>: nilai</span></p>) ----
        Map<String, String> info = new LinkedHashMap<>();
        for (Element p : doc.select(".infozingle p")) {
            String t = p.text().replaceAll("\\s+", " ").trim();
            int i = t.indexOf(':');
            if (i <= 0) continue;
            String key = t.substring(0, i).trim().toLowerCase();
            String val = t.substring(i + 1).trim();
            if (!key.isEmpty() && !val.isEmpty() && !info.containsKey(key)) {
                info.put(key, val);
            }
        }

        // ---- judul: "Judul" lebih bersih dari h1 ("... (Episode 1 – 170)") ----
        String judul = nz(info.get("judul"));
        if (judul.isEmpty()) {
            Element h1 = doc.selectFirst("h1");
            if (h1 != null) judul = stripSub(h1.text());
        }
        s.title = judul;

        // ---- sampul ----
        Element og = doc.selectFirst("meta[property=og:image]");
        if (og != null) s.thumb = og.attr("content").trim();
        if (s.thumb.isEmpty()) {
            Element img = doc.selectFirst(".wp-post-image");
            if (img != null) s.thumb = imgAttr(img);
        }

        // ---- status / tipe / studio / rilis ----
        s.statusWord = nz(info.get("status"));
        s.type = nz(info.get("tipe"));
        s.studio = nz(info.get("studio"));
        s.released = nz(info.get("tanggal rilis"));
        String count = nz(info.get("total episode"));
        if (!count.isEmpty()) count = count + " Episode";
        if (!s.statusWord.isEmpty() && !count.isEmpty()) {
            s.status = s.statusWord + " · " + count;
        } else {
            s.status = !s.statusWord.isEmpty() ? s.statusWord : count;
        }

        // ---- genre (tautan /genres/) ----
        Set<String> seenGenre = new LinkedHashSet<>();
        for (Element g : doc.select("a[href*=/genres/]")) {
            String t = g.text().replaceAll("\\s+", " ").trim();
            if (!t.isEmpty() && seenGenre.add(t) && s.genres.size() < 8) {
                s.genres.add(t);
            }
        }

        // ---- sinopsis (.sinopc) ----
        Element syn = doc.selectFirst(".sinopc");
        if (syn != null) {
            StringBuilder sb = new StringBuilder();
            for (Element p : syn.select("p")) {
                String t = p.text().replaceAll("\\s+", " ").trim();
                if (t.isEmpty()) continue;
                if (sb.length() > 0) sb.append("\n\n");
                sb.append(t);
            }
            s.synopsis = sb.toString().trim();
        }

        // ---- daftar episode (.episodelist li > a) ----
        Set<String> seen = new LinkedHashSet<>();
        for (Element li : doc.select(".episodelist li")) {
            Element a = li.selectFirst("a[href]");
            if (a == null) continue;
            EpisodeItem e = new EpisodeItem();
            e.url = abs(a.attr("href"));
            // Daftar juga berisi tautan Batch/kumpulan — bukan episode.
            if (!e.url.contains("/episode/")) continue;
            if (e.url.isEmpty() || !seen.add(e.url)) continue;
            e.title = a.text().replaceAll("\\s+", " ").trim();
            Element dat = li.selectFirst(".zeebr");
            e.date = dat != null ? dat.text().trim() : "";
            e.num = episodeNum(e.title, e.url);
            s.episodes.add(e);
        }

        // Episode terbaru di atas (konsisten dengan Oploverz).
        s.episodes.sort((x, y) -> Integer.compare(numOf(y), numOf(x)));
        return s;
    }

    // ------------------------------------------------------------------ episode

    public static Oploverz.Episode loadEpisode(String url) throws Exception {
        Document doc = connect(url).get();
        Oploverz.Episode ep = new Oploverz.Episode();

        Element h1 = doc.selectFirst("h1.posttl, h1");
        if (h1 != null) ep.title = stripSub(h1.text());

        Element og = doc.selectFirst("meta[property=og:image]");
        if (og != null) ep.thumb = og.attr("content").trim();

        Element sa = doc.selectFirst(".flir a[href*=/anime/], a[href*=/anime/]");
        if (sa != null) ep.seriesUrl = abs(sa.attr("href"));

        // 1) Kualitas lebih tinggi via AJAX (720p -> 480p); gagal = bawaan.
        String hq = fetchQuality(doc, url, "720p");
        if (hq == null) hq = fetchQuality(doc, url, "480p");
        if (hq != null) ep.mirrors.add(hq);

        // 2) Iframe bawaan halaman (selalu ada — penyelamat).
        Element ifr = doc.selectFirst("#embed_holder iframe, .player-embed iframe");
        if (ifr != null) {
            String src = abs(ifr.attr("src"));
            if (!src.isEmpty() && !ep.mirrors.contains(src)) ep.mirrors.add(src);
        }
        return ep;
    }

    /**
     * Ambil src iframe untuk kualitas q lewat 2 POST admin-ajax:
     * aksi nonce -> aksi fetch + {id,i,q} dari data-content -> base64 -> iframe.
     * Semua dianggap best-effort: gagal di mana pun = null (pakai bawaan).
     */
    private static String fetchQuality(Document doc, String pageUrl, String q) {
        try {
            String nonceAction = null, fetchAction = null;
            for (Element sc : doc.select("script")) {
                String js = sc.data();
                if (!js.contains("admin-ajax.php") || !js.contains("__x__nonce")) continue;
                Matcher m1 = AJAX_NONCE.matcher(js);
                if (m1.find()) nonceAction = m1.group(1);
                Matcher m2 = AJAX_FETCH.matcher(js);
                if (m2.find()) fetchAction = m2.group(1);
                break;
            }
            if (nonceAction == null || fetchAction == null) return null;

            // Anchor server dengan q yang diminta (i=0 = host utama).
            String id = null, i = null;
            for (Element a : doc.select(".mirrorstream a[data-content]")) {
                try {
                    JSONObject j = json(a.attr("data-content"));
                    if (q.equals(j.optString("q")) && "0".equals(j.optString("i"))) {
                        id = j.optString("id");
                        i = j.optString("i");
                        break;
                    }
                } catch (Exception ignored) {
                    // data-content bukan base64/JSON valid — lewati.
                }
            }
            if (id == null || id.isEmpty()) return null;

            String body1 = Jsoup.connect(BASE + "/wp-admin/admin-ajax.php")
                    .userAgent(Net.UA).header("Referer", pageUrl).timeout(15000)
                    .data("action", nonceAction)
                    .ignoreContentType(true).post().body().html();
            String nonce = new JSONObject(body1).optString("data", "");
            if (nonce.isEmpty()) return null;

            String body2 = Jsoup.connect(BASE + "/wp-admin/admin-ajax.php")
                    .userAgent(Net.UA).header("Referer", pageUrl).timeout(15000)
                    .data("action", fetchAction)
                    .data("id", id).data("i", i).data("q", q)
                    .data("nonce", nonce)
                    .ignoreContentType(true).post().body().html();
            String html = new String(
                    Base64.decode(new JSONObject(body2).optString("data", ""), Base64.DEFAULT),
                    "UTF-8");
            Matcher m = IFRAME_SRC.matcher(html);
            return m.find() ? m.group(1).trim() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------ util

    private static org.jsoup.Connection connect(String url) {
        return Jsoup.connect(url).userAgent(Net.UA).timeout(20000)
                .header("Referer", BASE + "/");
    }

    private static JSONObject json(String base64) throws Exception {
        return new JSONObject(new String(
                Base64.decode(base64.trim(), Base64.DEFAULT), "UTF-8"));
    }

    private static String stripSub(String t) {
        if (t == null) return "";
        return t.replaceAll("(?i)\\s*Subtitle Indonesia\\s*$", "")
                .replaceAll("\\s+", " ").trim();
    }

    private static String episodeNum(String title, String url) {
        Matcher m = EPISODE_NUM.matcher(title == null ? "" : title);
        if (m.find()) return m.group(1);
        m = EPISODE_NUM.matcher(url == null ? "" : url);
        return m.find() ? m.group(1) : "";
    }

    private static int numOf(EpisodeItem e) {
        try {
            return Integer.parseInt(e.num);
        } catch (Exception x) {
            return -1;
        }
    }

    private static Element parentLi(Element a) {
        Element p = a;
        for (int i = 0; p != null && i < 6; i++, p = p.parent()) {
            if ("li".equals(p.tagName())) return p;
        }
        return null;
    }

    private static String imgAttr(Element img) {
        if (img == null) return "";
        String u = img.hasAttr("data-src") ? img.attr("data-src")
                : img.hasAttr("data-lazy-src") ? img.attr("data-lazy-src")
                : img.attr("src");
        return u.trim().isEmpty() ? "" : abs(u);
    }

    private static String abs(String u) {
        if (u == null) return "";
        u = u.trim();
        if (u.isEmpty()) return "";
        if (u.startsWith("http")) return u;
        if (u.startsWith("//")) return "https:" + u;
        return BASE + (u.startsWith("/") ? u : "/" + u);
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}
