package com.anistream.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Perute sumber konten: Oploverz (utama) + Otakudesu (cadangan, aktif untuk
 * judul yang tidak dimiliki Oploverz — mis. Black Clover S1).
 * Semua pemanggilan scraper (search/series/episode) lewat sini; penentuan
 * sumber cukup dari domain URL — tanpa extra/flag baru di UI.
 */
public final class Sources {

    private Sources() {}

    private static boolean isOtakudesu(String url) {
        return url != null && url.contains("otakudesu.");
    }

    /** Hasil search digabung dua sumber; gagal satu sumber tidak menjatuhkan yang lain. */
    public static List<AnimeItem> search(String q) throws Exception {
        List<AnimeItem> out = new ArrayList<>();
        Exception err = null;
        try {
            out.addAll(Oploverz.search(q));
        } catch (Exception e) {
            err = e;
        }
        try {
            out.addAll(Otakudesu.search(q));
        } catch (Exception e) {
            if (err == null) err = e;
        }
        if (out.isEmpty() && err != null) throw err;
        return out;
    }

    /** Cari hanya di sumber LAIN (bukan pemilik `url`) — untuk merge daftar episode. */
    public static List<AnimeItem> searchExcept(String url, String q) throws Exception {
        return isOtakudesu(url) ? Oploverz.search(q) : Otakudesu.search(q);
    }

    public static Oploverz.Series loadSeries(String url) throws Exception {
        Oploverz.Series s = isOtakudesu(url) ? Otakudesu.loadSeries(url)
                : Oploverz.loadSeries(url);
        dedupeByNum(s);
        return s;
    }

    public static Oploverz.Series loadSeriesLite(String url) throws Exception {
        Oploverz.Series s = isOtakudesu(url) ? Otakudesu.loadSeriesLite(url)
                : Oploverz.loadSeriesLite(url);
        dedupeByNum(s);
        return s;
    }

    /**
     * Buang episode kembar per nomor — situs bisa menautkan ep yang sama dua
     * kali dengan URL beda (`?p=12790` vs slug). Bila bentrok, URL slug yang
     * dipilih (lebih kanonikal daripada `?p=`).
     */
    private static void dedupeByNum(Oploverz.Series s) {
        if (s == null || s.episodes.size() < 2) return;
        LinkedHashMap<Integer, EpisodeItem> uniq = new LinkedHashMap<>();
        for (EpisodeItem e : s.episodes) {
            int n;
            try { n = Integer.parseInt(e.num.trim()); } catch (Exception x) { n = -1; }
            if (n <= 0) continue;               // tanpa nomor sah — buang saja
            EpisodeItem old = uniq.get(n);
            if (old == null) {
                uniq.put(n, e);
            } else if (old.url.contains("?p=") && !e.url.contains("?p=")) {
                uniq.put(n, e);                 // ganti varian ?p= dgn slug
            }
        }
        s.episodes.clear();
        s.episodes.addAll(uniq.values());
    }

    public static Oploverz.Episode loadEpisode(String url) throws Exception {
        return isOtakudesu(url) ? Otakudesu.loadEpisode(url) : Oploverz.loadEpisode(url);
    }
}
