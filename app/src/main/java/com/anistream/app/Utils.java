package com.anistream.app;

import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public final class Utils {

    private Utils() {}

    /**
     * Paksa tinggi RecyclerView = seluruh isi konten. Untuk RV berwrap_content
     * di dalam ScrollView/NestedScrollView, spek ukur induk bisa membatasi
     * tinggi sehingga hanya 1–2 baris terbaca dan halaman tak bisa digulir ke
     * daftar lengkap (daftar episode terpotong). Ukur ulang dengan spek
     * UNSPECIFIED lalu tulis tinggi hasilnya ke layout params.
     */
    public static void fitRecycler(final RecyclerView rv) {
        if (rv == null) return;
        rv.post(() -> {
            int w = rv.getWidth();
            if (w <= 0) return;                       // belum ter-layout; dipanggil ulang oleh pemanggil
            rv.measure(
                    View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int h = rv.getMeasuredHeight();
            ViewGroup.LayoutParams lp = rv.getLayoutParams();
            if (lp != null && lp.height != h) {
                lp.height = h;
                rv.setLayoutParams(lp);
            }
        });
    }

    /** "baru saja" / "5 menit lalu" / "3 jam lalu" / "2 hari lalu" / tanggal. */
    public static String timeAgo(long ms) {
        if (ms <= 0) return "";
        long diff = System.currentTimeMillis() - ms;
        if (diff < 0) diff = 0;
        long s = diff / 1000;
        if (s < 60) return "baru saja";
        long m = s / 60;
        if (m < 60) return m + " menit lalu";
        long h = m / 60;
        if (h < 24) return h + " jam lalu";
        long d = h / 24;
        if (d < 7) return d + " hari lalu";
        return String.format(Locale.getDefault(), "%,d hari lalu", d);
    }

    /** Menit dalam format ramah: "12 mnt" atau "1 j 05 mnt". */
    public static String minutes(long ms) {
        long total = ms / 60000;
        long h = total / 60, m = total % 60;
        if (h > 0) return String.format(Locale.getDefault(), "%d j %02d mnt", h, m);
        return total + " mnt";
    }

    /** Posisi/durasi dalam format jam video: "mm:ss" atau "h:mm:ss". */
    public static String clock(long ms) {
        long total = Math.max(0, ms) / 1000;
        long s = total % 60, m = (total / 60) % 60, h = total / 3600;
        if (h > 0) return String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s);
        return String.format(Locale.getDefault(), "%02d:%02d", m, s);
    }

    /**
     * Tanggal situs ("September 30, 2026") menjadi relatif ("3 hari lalu",
     * ala AL). Gagal urai = teks mentah (tanpa data karangan).
     */
    public static String relDate(String raw) {
        long ms = parseDate(raw);
        return ms <= 0 ? (raw == null ? "" : raw) : timeAgo(ms);
    }

    /** Nama hari Indonesia dari tanggal situs; "" bila tak terurai. */
    public static String weekdayOf(String raw) {
        long ms = parseDate(raw);
        if (ms <= 0) return "";
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        String[] days = {"Minggu", "Senin", "Selasa", "Rabu",
                "Kamis", "Jumat", "Sabtu"};
        return days[c.get(Calendar.DAY_OF_WEEK) - 1];
    }

    private static long parseDate(String raw) {
        if (raw == null) return 0;
        String t = raw.trim();
        if (t.isEmpty()) return 0;
        // Kadang tanggal menempel label ("Episode 2 - October 1, 2026") —
        // comot bagian tanggalnya dulu.
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "([A-Za-z]+ \\d{1,2}, \\d{4}|\\d{1,2} [A-Za-z]+ \\d{4})").matcher(t);
        if (m.find()) t = m.group(1);
        String[][] pats = {
                {"MMMM d, yyyy", "en"}, {"MMM d, yyyy", "en"},
                {"d MMMM yyyy", "in"}, {"d MMM yyyy", "in"},
        };
        for (String[] p : pats) {
            try {
                Locale loc = "in".equals(p[1]) ? new Locale("in", "ID") : Locale.US;
                java.util.Date d = new SimpleDateFormat(p[0], loc).parse(t);
                if (d != null) return d.getTime();
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }
}
