package com.anistream.app;

/** Baris riwayat tonton, disimpan di SQLite. */
public class HistoryItem {
    public long id;
    public String title = "";
    public String epTitle = "";
    public String epUrl = "";
    public String seriesUrl = "";
    public String thumb = "";
    public long posMs;
    public long durMs;
    public long watchedAt;

    /** Sisa menit yang sudah ditonton. */
    public long watchedMinutes() { return posMs / 60000L; }

    /** Total menit episode. */
    public long totalMinutes() { return durMs / 60000L; }

    /** Persentase tontonan 0-100. */
    public int percent() {
        if (durMs <= 0) return 0;
        long p = posMs * 100L / durMs;
        return (int) Math.max(0, Math.min(100, p));
    }

    /** Apakah sudah hampir selesai (>=95%). */
    public boolean finished() { return durMs > 0 && percent() >= 95; }
}
