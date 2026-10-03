package com.anistream.app;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Menjalankan tugas berat di latar belakang, hasilnya dikirim ke main thread. */
public final class Async {

    public interface Done<T> {
        void ok(T result);
        void err(Throwable t);
    }

    private static final ExecutorService EX = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Async() {}

    public static <T> void go(final Callable<T> task, final Done<T> cb) {
        EX.execute(new Runnable() {
            @Override public void run() {
                try {
                    final T r = task.call();
                    MAIN.post(new Runnable() {
                        @Override public void run() { cb.ok(r); }
                    });
                } catch (final Throwable t) {
                    MAIN.post(new Runnable() {
                        @Override public void run() { cb.err(t); }
                    });
                }
            }
        });
    }

    /** Jadwalan tugas berulang di main thread (untuk penyimpanan posisi tonton). */
    public static Handler main() { return MAIN; }
}
