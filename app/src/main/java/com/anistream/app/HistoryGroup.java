package com.anistream.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Satu judul anime di Riwayat: kumpulan baris episode + tontonan terakhir
 * sebagai representatif (untuk Lanjutkan). Dikelompokkan per series_url
 * (jatuh balik ke ep_url bila series kosong).
 */
public class HistoryGroup {

    public final String key;
    public String title = "";
    public String thumb = "";
    public final List<HistoryItem> rows = new ArrayList<>();

    public HistoryGroup(String key) {
        this.key = key == null ? "" : key;
    }

    /** Baris dengan watched_at terbesar (daftar sudah urut DESC). */
    public HistoryItem latest() {
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int episodeCount() { return rows.size(); }

    /** Cari baris tontonan berdasarkan URL episode (untuk posisi resume). */
    public HistoryItem findByUrl(String epUrl) {
        if (epUrl == null) return null;
        for (HistoryItem h : rows) {
            if (epUrl.equals(h.epUrl)) return h;
        }
        return null;
    }
}
