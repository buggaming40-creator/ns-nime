package com.anistream.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Pemuat gambar ringkas: memori-cache + unduh paralel. */
public final class ImageLoader {

    private static final LruCache<String, Bitmap> MEM;
    private static final ExecutorService EX = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static {
        int max = (int) (Runtime.getRuntime().maxMemory() / 8);
        MEM = new LruCache<String, Bitmap>(Math.max(max, 4 * 1024 * 1024)) {
            @Override protected int sizeOf(String k, Bitmap v) { return v.getByteCount(); }
        };
    }

    private ImageLoader() {}

    /** Kosongkan seluruh cache memori (dipakai tombol "Hapus cache gambar"). */
    public static void clearMemory() {
        MEM.evictAll();
    }

    public static void load(final String url, final ImageView target) {
        if (target == null) return;
        if (url == null || url.isEmpty()) {
            target.setTag(null);
            target.setImageDrawable(null);
            return;
        }
        Bitmap hit = MEM.get(url);
        if (hit != null) {
            target.setTag(url);
            target.setImageBitmap(hit);
            return;
        }
        target.setTag(url);
        target.setImageDrawable(null);

        EX.execute(new Runnable() {
            @Override public void run() {
                final Bitmap bmp = fetch(url);
                if (bmp != null) MEM.put(url, bmp);
                MAIN.post(new Runnable() {
                    @Override public void run() {
                        if (bmp != null && url.equals(target.getTag())) target.setImageBitmap(bmp);
                    }
                });
            }
        });
    }

    private static Bitmap fetch(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", Net.UA);

            BitmapFactory.Options bo = new BitmapFactory.Options();
            bo.inJustDecodeBounds = true;
            InputStream probe = c.getInputStream();
            ByteArrayOutputStream tmp = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = probe.read(buf)) != -1) tmp.write(buf, 0, n);
            probe.close();
            byte[] data = tmp.toByteArray();

            BitmapFactory.decodeByteArray(data, 0, data.length, bo);
            int sample = 1;
            int w = bo.outWidth, h = bo.outHeight;
            while (w / 2 >= 400 || h / 2 >= 600) { w /= 2; h /= 2; sample *= 2; }

            BitmapFactory.Options dec = new BitmapFactory.Options();
            dec.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(data, 0, data.length, dec);
        } catch (Throwable t) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
