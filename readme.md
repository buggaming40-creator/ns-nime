# NsNime — Nonton Anime Subtitle Indonesia

Aplikasi Android untuk streaming anime subtitle Indonesia dengan sumber
`oploverz.ch`. Tanpa login, tanpa iklan, tanpa VIP. Dibuat oleh **nansoffc**.

## Fitur

- **Terbaru** — banner carousel + grid Rilis Terbaru, Sedang Tayang, Top Rating.
- **Cari** — live search + jumlah hasil + Jelajahi Genre + chip pencarian terakhir (bisa dihapus).
- **Tersimpan** — bookmark lokal (SQLite) + lencana "Ada Episode Baru!" + sortir
  abjad / ditambahkan / diperbarui + kategori (Simpan ke + filter).
- **Riwayat** — progress menit (`mm:ss / mm:ss`), total menit, Lanjutkan
  (one-tap resume), hapus satuan / bersihkan semua.
- **Detail anime** — poster, judul, baris meta (studio | tipe | status | tahun),
  chip genre (ketuk = cari), sinopsis + buka/tutup, kartu Lanjutkan Menonton,
  "Episode (N)" + grid/list + urut + chip rentang, ketuk episode =
  Putar Sekarang / Unduh (GoFile).
- **Player** — WebView menangkap URL media → ExoPlayer (resume, menit
  tersimpan); lanskap; mini-player PiP (bisa dimatikan); kontrol muncul saat diketuk (ala YouTube, tanpa tombol
  ganda); rel kanan: kecepatan 0,5x–2x, prev/next, penghitung, daftar episode,
  ganti server (bila >1 mirror); info 2 baris (anime + episode).
- **Setelan** — tema Gelap/Terang/Ikuti sistem (bawaan Gelap), 8 warna aksen,
  preferensi player (kualitas, autoplay, aspect ratio), penyimpanan (clear
  cache, hapus riwayat pencarian, status), tentang + changelog + disclaimer.

## Kebutuhan

- Android Studio (AGP 9.x) atau command line + JDK 17
- Android SDK 36 (`sdk.dir` di `local.properties`)
- minSdk 26, targetSdk 36, bahasa Java, Material3 + Media3 + Jsoup

## Build & install

```bash
cd /home/nansoffc/AniStream
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/NsNime-debug.apk
/usr/bin/adb -s emulator-5554 install -r app/build/outputs/apk/debug/NsNime-debug.apk
```

## Struktur

```
app/src/main/
  java/com/anistream/app/   # Activity, Fragment, adapter, Oploverz (scraper),
                            # Prefs, HistoryStore/BookmarkStore (SQLite), ImageLoader
  res/layout/               # activity_*, fragment_*, item_*, ui_item_*
  res/values[-night]/       # colors.xml (Slate/Indigo), themes.xml, accents.xml, strings.xml
animelovers.md              # analisis gaya AL v3 + rencana (Tahap 1–3)
```

## Sumber data

Scrape `https://oploverz.ch` (rilis, `?s=` cari, halaman series, halaman
episode + mirror Blogger + tautan GoFile). UA Android Chrome diterima situs.
Riwayat dedup per `ep_url`. Tidak ada akun/backend — semua lokal.

## Catatan

Proyek pribadi untuk pemakaian sendiri. Tidak berafiliasi dengan AnimeLovers
maupun oploverz.ch. Hormati hak cipta pemilik konten.
