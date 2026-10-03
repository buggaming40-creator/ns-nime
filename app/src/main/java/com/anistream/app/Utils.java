package com.anistream.app;

import java.util.Locale;

public final class Utils {

    private Utils() {}

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
}
