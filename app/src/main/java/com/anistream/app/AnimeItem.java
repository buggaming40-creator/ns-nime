package com.anistream.app;

/** Satu kartu anime di daftar terbaru / hasil pencarian. */
public class AnimeItem {
    public String title = "";
    public String url = "";
    public String thumb = "";
    public String meta = "";

    public AnimeItem() {}

    public AnimeItem(String title, String url, String thumb, String meta) {
        this.title = title; this.url = url; this.thumb = thumb; this.meta = meta;
    }
}
