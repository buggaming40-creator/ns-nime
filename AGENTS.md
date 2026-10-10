# AGENTS.md — NsNime (`/home/nansoffc/AniStream`)

Petunjuk kerja untuk agen coding di repo ini. Baca sebelum mengubah kode.

## Perintah

```bash
cd /home/nansoffc/AniStream && ./gradlew assembleDebug   # gradlew TIDAK di PATH
/usr/bin/adb -s emulator-5554 uninstall com.anistream.app   # uninstall dulu (saran operator)
/usr/bin/adb -s emulator-5554 install app/build/outputs/apk/debug/NsNime-debug.apk
/usr/bin/adb -s emulator-5554 shell "am start -n com.anistream.app/.MainActivity"
```

- Package: `com.anistream.app`. APK: `NsNime-debug.apk` (`archivesName`).
- Jangan jalankan `./gradlew` dan `adb` dari dua agen paralel (build/emulator
  milik satu pihak agar tidak bentrok).
- Bahasa UI: Indonesia kasual. Komentar kode: Indonesia, ringkas.

## Arsitektur

- `MainActivity`: ViewPager2 (5 tab: Terbaru/Cari/Tersimpan/Riwayat/Setelan) +
  BottomNavigationView, sinkron dua arah. Tab aktif disimpan manual (`KEY_TAB`
  + `onRestoreInstanceState`) — state bawaan ViewPager2 tidak dipercaya.
- Tema: `AniApp.onCreate` → `Prefs.applyThemeMode`. `ThemeUtils.apply(this)`
  WAJIB baris pertama `onCreate` tiap Activity (sebelum `setContentView`).
  Palet di `values/colors.xml` + `values-night/colors.xml` — nama resource
  warna lama JANGAN dihapus/di-rename (banyak layout memakai).
- Aksen: overlay `Theme.AniStream.Accent.*` + `Prefs.accentIndex`; ganti =
  simpan + `recreate()`. Referensi warna aksen di layout/drawable pakai
  `?attr/colorPrimary|colorOnPrimary|colorSecondary|colorAccent`.
- Radio tema: JANGAN pakai `OnCheckedChangeListener` dan JANGAN campur
  `setChecked()` manual + `group.check()` (titik radio bisa tidak tergambar).
  Pola benar: klik langsung + `syncThemeUi()` via `group.post{ check(); jumpDrawablesToCurrentState() }`.
- Player: WebView tangkap URL → ExoPlayer (`sensorLandscape`). `setThemeMode`
  no-op bila nilai sama; hanya klik user yang boleh menulis tema.
- Data lokal: `Prefs` (tema, aksen, recents, player_*), `HistoryStore`
  (`history.db`, unik `ep_url`), `BookmarkStore` (`bookmark.db`).
  `pm clear` menghapus semuanya (dipakai untuk uji default).

## Scraper (`Oploverz.java`, situs `oploverz.ch`, tema `dramastream`)

- **Dua sumber**: `Sources.java` = perute; `Oploverz` (utama) + `Otakudesu`
  (`otakudesu.blog`, cadangan — punya judul lama yang tak dimiliki Oploverz,
  mis. Black Clover S1). Semua pemanggilan scraper (search/series/episode)
  WAJIB lewat `Sources.*` — sumber dipilih otomatis dari domain URL.
  `Otakudesu` memakai model `Oploverz.Series`/`Episode`.
- `latest()` = halaman depan; `search(q)` = `/?s=`; `loadSeries` (+follow ke
  halaman series bila dibuka dari URL episode); `loadEpisode` = mirror Blogger
  (base64 `<option>`) + tautan GoFile.
- **Paginasi episode Oploverz**: daftar episode panjang dipotong situs per
  ~12 item/halaman + tautan `?ep_page=N` di dalam `.pagination` (masih di
  dalam `div.eplister` — hati-hati jangan ikut tersaring jadi item). `loadSeries`
  mengikuti semua halaman (`fillEpRemainingPages`); `loadSeriesLite` tetap
  1 fetch. Jangan kembali ke parse halaman 1 saja (One Piece jadi cuma ~17).
- **Merge sumber lain (daftar episode terlengkap)**: `Sources.searchExcept(url,q)`
  mencari di sumber lawan; `SeriesActivity.maybeMergeOtherSource` jalan di latar
  setelah daftar utama tampil. Kecocokan judul WAJIB persis setelah normalisasi
  (buang "subtitle indonesia" & tanda kurung) — "Black Clover 2nd Season" tidak
  boleh merge ke "Black Clover". `mergeEpisodes` dedupe per `epInt`, lalu
  `buildRangeChips()` + `sortEpisodes()` (sortEpisodes TIDAK rebuild chip).
  Hasil: One Piece 104 → 483 (Oploverz 1080–1180 ∪ Otakudesu 1–200 & 901–1180;
  201–900 sudah dihapus di kedua situs) → 480 setelah filter Pembatas +
  `dedupeByNum` (v2.9). Daftar gabungan ikut ke player via
  intent `epUrlList`/`epTitleList` — player tidak perlu merge sendiri.
- **Nomor episode kanonikal**: `.epl-num` Oploverz bisa typo (ep 1091 tertulis
  "1090" sementara judul & URL benar) → `parseEpisodeItems` SELALU pakai
  `extractEpisodeNumber(title,url)` bila ada, baru fallback `.epl-num`.
- **Baris "Pembatas" Otakudesu (v2.9)**: halaman seri panjang (mis. One Piece)
  punya baris penanda `"======== Pembatas Episode | Episode X – Y Dalam
  Proses ======"` (URL `/episode/pembatas-.../`) — halaman TANPA player sama
  sekali; saat dibuka, player jatuh ke WebView halaman itu dan user melihat
  banner judol ("iklan judol"). Parser Otakudesu wajib `continue` bila judul
  mengandung "Pembatas"/"Dalam Proses" ATAU URL mengandung "pembatas-".
  Tanpa filter ini ep asli (mis. One Piece ep 1) TERBUANG oleh dedupe merge
  (divider nomor 1 menang duluan) — feed ep1 asli justru valid (prolog
  Roger, 24:15).
- **Dedupe by nomor (v2.9)**: situs bisa menautkan ep yang sama 2× dengan URL
  beda (`?p=12790` vs slug — lolos dedupe-by-URL, jadi baris kembar "1153").
  `Sources.loadSeries`/`loadSeriesLite` memanggil `dedupeByNum` (per nomor
  int; saat bentrok pilih URL slug, buang varian `?p=`).
- Saat install APK ke emulator: **uninstall dulu**, jangan `install -r`
  (operator: `install -r` bisa meninggalkan bug sisa dari data lama).
- `loadSeriesLite` (tanpa follow) untuk cek status ongoing — hemat fetch.
- Jangan memalsukan data (rating, genre, server). Filter Anime/Donghua =
  client-side dari `meta` (NsNime: tidak ada UI filter).
- UA: `Mozilla/5.0 (Linux; Android 13) ... Chrome/126 Mobile Safari/537.36`.

## Jebakan emulator

- Screenshot: `shell screencap -p /sdcard/x.png` + `pull` (exec-out basi).
- `uiautomator dump` TIDAK menampilkan node GONE + lambat (kontrol player
  keburu hide) — andalkan screenshot untuk player.
- Kontrol player auto-hide; ketuk video dulu sebelum ketuk tombol rel.
- Koordinat bisa bergeser setelah layout berubah — ambil bounds dari dump.
- Proses app kadang STALE setelah `install -r` — `force-stop` dulu bila
  perilaku tidak sesuai kode baru.
- Jangan memasukkan kredensial Google/akun ke emulator.

## Pelajaran build
- Build incremental di sini TIDAK bisa dipercaya (pernah hasilkan Frankenstein:
  UI campur kode lama/baru). Untuk APK rilis/uji SELALU `clean assembleDebug
  --no-build-cache`, lalu verifikasi isi dex (`unzip -p ... classes*.dex |
  grep -c <simbol-baru>`) + `force-stop` sebelum install/uji.
- Setelah `install -r`, proses app kadang STALE — selalu `force-stop` dulu.
- Dump uiautomator hanya memuat baris RecyclerView yang menempel (tidak bisa
  dipakai membuktikan seksi tidak ada); screenshot lebih terpercaya. Dump juga
  melewatkan node GONE dan lambat (kontrol player keburu hide).

## Pelajaran build 2
- JANGAN percaya "BUILD SUCCESSFUL in 1s": up-to-date check sering bohong di
  sini. Rilis/uji SELALU `--rerun-tasks --no-build-cache`, lalu verifikasi
  simbol baru di dex + timestamp/size APK berubah.
- Multi-dex (5 file): simbol app ada di `classes3.dex` — verifikasi dengan
  `unzip -p apk "classes*.dex"` (semua file), bukan `classes.dex` saja.
- Player menampilkan banner iklan/banner situs + badge "Mode web" = WebView
  fallback (mirrors kosong / capture gagal / halaman tanpa player) — BUKAN
  video dari mirror. Diagnosis: `Log.i` sementara di `loadSource` ok/err,
  `onPageFinished`, `captureMedia`, `fallbackToWeb`, baca `logcat -s Flow`.

## Jebakan RecyclerView dalam ScrollView

- Halaman seri (`activity_series`) + halaman player (`activity_player`)
  membungkus RecyclerView `wrap_content` di dalam ScrollView/NestedScrollView:
  spek ukur induk bisa membatasi tinggi RV sehingga hanya 1–2 baris terbaca,
  halaman mentok (episode ke-bawah tak tampil — bug "daftar episode tak
  semua"). Solusi WAJIB: panggil `Utils.fitRecycler(rv)` setiap kali data/
  tampilan berubah (submit adapter, ganti mode list/grid, rotasi player) —
  mengukur ulang dengan spek UNSPECIFIED lalu menulis tinggi konten penuh ke
  layout params. Jangan andalkan wrap_content alami RV di dalam scroll.
