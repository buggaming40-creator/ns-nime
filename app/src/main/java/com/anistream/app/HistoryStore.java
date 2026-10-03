package com.anistream.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Riwayat tonton: posisi (ms), durasi (ms), dan waktu terakhir ditonton. */
public class HistoryStore extends SQLiteOpenHelper {

    private static final String DB = "history.db";
    private static final int VER = 1;

    public HistoryStore(Context c) {
        super(c.getApplicationContext(), DB, null, VER);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE history ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "title TEXT NOT NULL DEFAULT '',"
                + "ep_title TEXT NOT NULL DEFAULT '',"
                + "ep_url TEXT NOT NULL UNIQUE,"
                + "series_url TEXT NOT NULL DEFAULT '',"
                + "thumb TEXT NOT NULL DEFAULT '',"
                + "pos_ms INTEGER NOT NULL DEFAULT 0,"
                + "dur_ms INTEGER NOT NULL DEFAULT 0,"
                + "watched_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_history_at ON history(watched_at DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS history");
        onCreate(db);
    }

    /** Simpan / perbarui satu tontonan berdasarkan URL episode. */
    public void save(HistoryItem h) {
        if (h == null || h.epUrl == null || h.epUrl.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("title", h.title == null ? "" : h.title);
        v.put("ep_title", h.epTitle == null ? "" : h.epTitle);
        v.put("ep_url", h.epUrl);
        v.put("series_url", h.seriesUrl == null ? "" : h.seriesUrl);
        v.put("thumb", h.thumb == null ? "" : h.thumb);
        v.put("pos_ms", h.posMs);
        v.put("dur_ms", h.durMs);
        v.put("watched_at", h.watchedAt > 0 ? h.watchedAt : System.currentTimeMillis());
        db.insertWithOnConflict("history", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Ambil riwayat terbaru. */
    public List<HistoryItem> all() {
        List<HistoryItem> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT id,title,ep_title,ep_url,series_url,thumb,pos_ms,dur_ms,watched_at "
              + "FROM history ORDER BY watched_at DESC", null);
        try {
            while (c.moveToNext()) {
                HistoryItem h = new HistoryItem();
                h.id = c.getLong(0);
                h.title = c.getString(1);
                h.epTitle = c.getString(2);
                h.epUrl = c.getString(3);
                h.seriesUrl = c.getString(4);
                h.thumb = c.getString(5);
                h.posMs = c.getLong(6);
                h.durMs = c.getLong(7);
                h.watchedAt = c.getLong(8);
                out.add(h);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public void delete(long id) {
        getWritableDatabase().delete("history", "id=?", new String[]{String.valueOf(id)});
    }

    /** Hapus seluruh baris satu judul (cocok series_url maupun ep_url). */
    public void deleteSeries(String key) {
        if (key == null || key.isEmpty()) return;
        getWritableDatabase().delete("history", "series_url=? OR ep_url=?",
                new String[]{key, key});
    }

    public void clear() {
        getWritableDatabase().delete("history", null, null);
    }

    public int count() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM history", null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }
}
