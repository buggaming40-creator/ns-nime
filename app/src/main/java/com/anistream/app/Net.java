package com.anistream.app;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

/** Helper HTTP ringkas tanpa dependensi eksternal. */
public final class Net {

    public static final String UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 8) AppleWebKit/537.36 "
          + "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";

    private Net() {}

    /** GET dan kembalikan isi body sebagai String. Melempar IOException bila gagal. */
    public static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(25000);
        c.setInstanceFollowRedirects(true);
        c.setRequestMethod("GET");
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        c.setRequestProperty("Accept-Language", "id-ID,id;q=0.9,en;q=0.8");

        int code = c.getResponseCode();
        InputStream in = (code >= 200 && code < 400) ? c.getInputStream() : c.getErrorStream();
        if (in == null) throw new IllegalStateException("HTTP " + code);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
        in.close();
        c.disconnect();

        String enc = c.getContentEncoding();
        if (enc == null) {
            String ct = c.getContentType();
            if (ct != null) {
                for (String part : ct.split(";")) {
                    part = part.trim();
                    if (part.startsWith("charset=")) enc = part.substring(8);
                }
            }
        }
        if (enc == null) enc = "UTF-8";
        return new String(bos.toByteArray(), Charset.forName(enc));
    }
}
