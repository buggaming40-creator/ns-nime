package com.anistream.app;

import java.util.ArrayList;
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
        return isOtakudesu(url) ? Otakudesu.loadSeries(url) : Oploverz.loadSeries(url);
    }

    public static Oploverz.Series loadSeriesLite(String url) throws Exception {
        return isOtakudesu(url) ? Otakudesu.loadSeriesLite(url)
                : Oploverz.loadSeriesLite(url);
    }

    public static Oploverz.Episode loadEpisode(String url) throws Exception {
        return isOtakudesu(url) ? Otakudesu.loadEpisode(url) : Oploverz.loadEpisode(url);
    }
}
