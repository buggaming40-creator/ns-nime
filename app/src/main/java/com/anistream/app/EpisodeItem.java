package com.anistream.app;

import java.io.Serializable;

/**
 * Satu baris pada daftar episode. Diimplementasikan Serializable agar bisa
 * dikirim ke PlayerActivity lewat Intent (dipakai tombol episode sebelumnya
 * dan berikutnya).
 */
public class EpisodeItem implements Serializable {

    private static final long serialVersionUID = 1L;

    public String title = "";
    public String url = "";
    public String num = "";
    public String date = "";

    public EpisodeItem() {}

    public EpisodeItem(String title, String url, String num, String date) {
        this.title = title;
        this.url = url;
        this.num = num;
        this.date = date;
    }
}
