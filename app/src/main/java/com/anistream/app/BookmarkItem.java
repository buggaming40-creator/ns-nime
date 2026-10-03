package com.anistream.app;

/**
 * Satu judul yang disimpan pengguna (bookmark). Disimpan di `bookmark.db`
 * lihat {@link BookmarkStore}.
 *
 * `lastSeenEp` = jumlah episode situs terakhir yang sudah dilihat pengguna;
 * dipakai untuk lencana "Ada Episode Baru!" (jumlah episode situs >
 * last_seen_ep).
 */
public class BookmarkItem {
    public long id;
    public String seriesUrl = "";
    public String title = "";
    public String thumb = "";
    public String status = "";
    public int lastSeenEp;
    public long addedAt;
    public long updatedAt;

    /**
     * Jumlah episode yang terbaca dari kolom `status` (hasil parse, bukan
     * kolom terpisah — lihat {@link BookmarkStore#parseCount(String)}).
     * Dipakai kartu untuk memutus lencana episode baru.
     */
    public int siteEp;

    /** Lencana "Ada Episode Baru!" aktif bila situs punya lebih banyak episode. */
    public boolean hasNewEpisode() {
        return siteEp > 0 && siteEp > lastSeenEp;
    }
}
