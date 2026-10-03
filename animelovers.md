# Analisis & Rencana Refactor — Gaya AnimeLovers v3 untuk AniStream

> Dokumen Tahap 1. Acuan gaya: aplikasi **AnimeLovers V3** (`com.aniverseid.animelovers`,
> v1.2.5) yang dipelajari langsung di emulator (disclaimer, onboarding, home, profil).
> AniStream meniru **tata letak & rasa**-nya saja — **tanpa login, tanpa VIP, tanpa
> EXP/level, tanpa chat** — dengan identitas sendiri (nama, logo, aksen indigo,
> sumber `oploverz.ch`, kredit `nansoffc`).

---

## 1. Hasil Bedah AnimeLovers v3 (rujukan)

### 1.1 Alur buka aplikasi
1. **Disclaimer** — kartu teks Inggris + tombol `Decline` (merah outline) / `Accept`
   (gradasi ungu-biru). Latar gelap + pola titik bintang.
2. **Onboarding carousel** (±5 halaman, indikator dot) — maskot anime, badge pil
   (`Sub Indo • Tanpa iklan`), judul besar tebal, tombol pil penuh `Lanjut` /
   `Mulai sekarang`, tautan `Lewati` kanan-atas.
3. **Login Google** (wajib di mereka) — **tidak ditiru**. Kita langsung masuk home
   (tamu permanen).

### 1.2 Home (acuan utama)
| Komponen | Ciri visual |
|---|---|
| Header | Avatar + sapaan (`Selamat Pagi` + nama) + badge `Lv.1`/`F-Rank`; kanan: tombol persegi membulat (lonceng, kaca pembesar) |
| Chip bar | Pil outline horizontal: `Daily Login`, `Pesan`, `Daftar A-Z`, `Customer Service` |
| Kartu pengumuman | Kartu info + ikon `i`, teks giveaway/tema |
| Ticker chat global | Bar pil berisi cuplikan chat berjalan |
| Riwayat | Judul seksi + `Lihat Semua >`; kartu horizontal (thumb 16:9, badge `Eps N` kiri-atas, tombol play tengah) + judul di bawah |
| Promo VIP | Kartu `Flash Sale` + timer + 3 tier harga — **DIBUANG** |
| Leaderboard | `Top Leveling`, avatar + EXP mingguan — **DIBUANG** (sistem EXP tidak ditiru) |
| Ongoing Update | Judul + `Lihat Jadwal >`; filter pil `Semua / Anime / Donghua`; grid poster |
| Bottom nav | Bar **mengambang membulat**; item aktif = **pil penuh berisi warna aksen** + ikon putih; 4 item (home, list, bookmark, profil) |

### 1.3 Profil (di kita = Setelan, tanpa login)
Grid 2 kolom kartu + ikon persegi warna: Personalisasi Komentar, Border Avatar,
Teman, Pesan, Level & Rank, Giveaway VIP, tombol `Log Out` merah. **Yang dipakai
polanya saja** (grid kartu + ikon warna). Seluruh isi sosial/VIP/login **dibuang**;
diganti menu lokal: Tampilan, Warna Aksen, Player, Penyimpanan, Tentang, Data.

### 1.4 Bahasa visual (disimpulkan)
- Latar gelap pekat + pola titik bintang halus; kartu surface sedikit lebih terang,
  radius besar (±18–24dp), outline tipis (bukan bayangan tebal).
- Aksen ungu-periwinkle; teks putih + abu; judul seksi **bold ±20sp**.
- Pil/chip fully-rounded; tombol primer pil penuh; badge episode pil kecil.

---

## 2. Component Mapping (yang dibangun di AniStream)

### 2.1 Home (`fragment_home` + `HomeFragment`)
| # | Komponen | Sumber data |
|---|---|---|
| H-1 | Header: logo persegi membulat + `AniStream` + tagline; kanan: tombol search + tombol gear persegi membulat | statis |
| H-2 | Banner carousel unggulan (auto-scroll, indikator dot) | 5 item pertama `Oploverz.latest()` (pakai thumb) |
| H-3 | Chip akses cepat: `Semua / Anime / Donghua` (filter client-side dari field `meta`) | `AnimeItem.meta` |
| H-4 | Seksi `Rilis Terbaru` + hint kanan; grid 3 kolom (5 kolom lanskap) kartu poster + scrim gradasi + badge `Ep N` + judul 2 baris + meta | `Oploverz.latest()` |
| H-5 | Seksi `Sedang Tayang` (horizontal) — item yang statusnya ongoing | `loadSeries().status` berisi `ongoing` (best-effort; fallback = sembunyikan seksi) |
| H-6 | Seksi `Top Rating` (horizontal) — disusun dari data yang ada | best-effort dari `latest()` (urutan situs); label seksi tetap, tanpa angka rating palsu |
| H-7 | Empty/loading state berikon | statis |

### 2.2 Cari (`fragment_search` + `SearchFragment`) — tetap ada (tidak ada di peta AL, milik kita)
Kolom kapsul + ikon + tombol clear + tombol `Cari`; chip `Pencarian Terakhir`
(dari `Prefs.recents`, bisa dihapus via Setelan); grid hasil; empty state berikon.

### 2.3 Riwayat (`fragment_history` + `HistoryFragment`)
Header judul + total menit (`history_total`) + `Bersihkan` (dialog konfirmasi);
kartu: poster, judul, label episode + **progress bar + teks `mm:ss / mm:ss`**,
waktu tonton, tombol **Lanjutkan Nonton** (one-tap resume via `pos_ms`) + `Hapus`
satuan; `Clear All`; empty state berikon.

### 2.4 Tersimpan / Bookmark (BARU — `fragment_bookmark` + `BookmarkFragment`)
Grid sampul; badge status **`Ada Episode Baru!`** (jumlah episode situs >
`last_seen_ep` saat dibuka); sortir `Abjad / Terakhir Ditambahkan / Terakhir
Diperbarui` (menu/spinner); hapus cepat per kartu + `Bersihkan`; empty state.
Tambah/hapus bookmark dari `SeriesActivity` (tombol ikon di header).

### 2.5 Setelan (`fragment_settings` + `SettingsFragment`)
Kartu identitas (logo, nama, tagline, badge versi dari PackageManager) +
**Tampilan** (Gelap/Terang/Ikuti sistem, default Gelap) + **Warna Aksen**
(swatch, sudah ada — re-based ke palet Indigo §4) + **Player** (kualitas default
360p/480p/720p/1080p, Autoplay episode berikutnya ON/OFF, Aspect Ratio
Fit/Fill/Zoom) + **Penyimpanan** (Clear Cache, Hapus riwayat pencarian, status
`X episode · Y bookmark · Z KB`) + **Tentang** (Dibuat oleh `nansoffc`, sumber
`oploverz.ch`, engine `WebView + ExoPlayer (Media3)`, Versi, **Changelog**,
**Disclaimer/DMCA**) + **Data** ( Bersihkan riwayat). Tanpa: akun, password,
VIP, social login.

### 2.6 Series & Player
- `SeriesActivity`: header (poster, judul, status, sinopsis bila ada) + tombol
  bookmark + daftar episode (`item_episode` kartu membulat).
- `PlayerActivity`: WebView penangkap URL media → ExoPlayer; `sensorLandscape`;
  rel kanan (`btnPrev`/`epCounter`/`btnNext`) selalu tampil; auto-next sesuai
  pref; aspect ratio sesuai pref; topbar (kembali + judul) mengikuti visibilitas
  kontrol player.

---

## 3. Architecture Plan

### 3.1 Navigasi
`MainActivity` + `ViewPager2` (geser) + `BottomNavigationView`, sinkron dua arah,
`offscreenPageLimit = PAGE_COUNT`. Urutan tab: **Terbaru (0) → Cari (1) →
Tersimpan (2) → Riwayat (3) → Setelan (4)**. Tab aktif disimpan manual
(`KEY_TAB` + `onRestoreInstanceState`) karena state bawaan ViewPager2 bisa
salah saat recreate (ganti tema).
Menu (`bottom_menu`): `nav_latest`, `nav_search`, `nav_saved` (ikon bookmark,
BARU), `nav_history`, `nav_settings`. Judul: Terbaru, Cari, Tersimpan, Riwayat,
Setelan (`strings.xml`).

### 3.2 State management (tanpa login, tanpa backend user)
- Sumber jaringan: `Oploverz.latest()/search()/loadSeries()/loadEpisode()`
  (Jsoup, thread via `Async`), model `AnimeItem` (title/url/thumb/meta),
  `Oploverz.Series` (+episodes), `EpisodeItem` (Serializable via Intent).
- State lokal: `Prefs` (tema, aksen, recents pencarian, pref player) +
  `HistoryStore` (SQLite `history.db`) + `BookmarkStore` (BARU, SQLite
  `bookmark.db`, §3.3). Tidak ada sesi/token; tidak ada state auth di mana pun.
- Player: posisi/durasi ditulis ke `HistoryStore` berkala + saat pause/ended;
  `findResumePos()` = 0 bila episode finished (cegah loop auto-next).

### 3.3 Model data lokal
```sql
-- history.db (ADA, VER 1 — jangan diubah)
history(id, title, ep_title, ep_url UNIQUE, series_url, thumb,
        pos_ms, dur_ms, watched_at);  -- idx watched_at DESC

-- bookmark.db (BARU, VER 1)
bookmark(id INTEGER PK, series_url TEXT UNIQUE, title TEXT, thumb TEXT,
         status TEXT DEFAULT '', last_seen_ep INTEGER DEFAULT 0,
         added_at INTEGER, updated_at INTEGER);
-- idx updated_at DESC
```
`BookmarkStore` meniru pola `HistoryStore` (SQLiteOpenHelper): `add/toggle`,
`remove`, `clear`, `all(SortMode)`, `has(url)`, `touchEpisodes(url, count)`.
Sort: `ALFABET (title COLLATE NOCASE)`, `DITAMBAHKAN (added_at DESC)`,
`DIPERBARUI (updated_at DESC)`.

### 3.4 Preferensi player (`Prefs`, kunci baru)
`player_quality` (0=360p,1=480p,2=720p,3=1080p; default 720p — dipakai sebagai
label preferensi + `DefaultTrackSelector` bila trek tersedia),
`player_autoplay` (boolean, default true), `player_ratio` (0=Fit,1=Fill,2=Zoom →
`AspectRatioFrameLayout` RESIZE_MODE_FIT/FILL/ZOOM).

---

## 4. Design Tokens (Indigo Palette — final)

| Token | Dark | Light |
|---|---|---|
| `primary` (600) | `#818CF8`* | `#4F46E5` |
| `primary_dark` (hover 800) | `#A5B4FC` | `#3730A3` |
| `accent` (400) | `#818CF8` | `#4F46E5` |
| `accent_soft` (100) | `#312E81` | `#E0E7FF` |
| `window_bg` (Slate 900) | `#0F172A` | `#F1F5F9` |
| `card_bg` (Slate 800) | `#1E293B` | `#FFFFFF` |
| `nav_bg` | `#0F172A` | `#FFFFFF` |
| `border` (Slate 700) | `#334155` | `#E2E8F0` |
| `text_primary` | `#F8FAFC` | `#0F172A` |
| `text_secondary` | `#94A3B8` | `#64748B` |
| `on_primary` | `#0F172A` | `#FFFFFF` |

\* Di mode gelap, primer memakai Indigo 400 agar kontras di atas Slate
(stack: teks `on_primary` gelap di atasnya).
Radius: kartu 16dp, pil/chip/nav 24–28dp, badge 8dp, tombol 14dp.
Border kartu/nav/field: 1dp `@color/border`. Elevasi: 0–2dp (andalkan outline).
Font: sistem, judul seksi bold 20sp, judul kartu 14sp semibold max-2-baris,
meta 12sp secondary.
Status/nav bar: `window_bg` / `nav_bg`. Player tetap hitam penuh (tema player).

> Catatan migrasi: token lama (`window_bg #000000`, `card_bg #101018`) diganti
> Slate di atas. **Warna Aksen (8 swatch, bawaan Indigo) tetap dipertahankan**
> — cukup re-based: opsi `Indigo` memakai nilai tabel ini; 7 opsi lain tetap
> sebagai alternatif (Ungu/Biru/Toska/Hijau/Merah/Oranye/Pink).

---

## 5. Removal Checklist (wajib TIDAK ada di aplikasi)

- [ ] Login / daftar / Google Sign-In / social login / tombol profil-akun
- [ ] Ubah password, menu Akun, Log Out
- [ ] VIP: Flash Sale, tier harga, badge VIP, filter Top VIP, Giveaway VIP
- [ ] EXP / Level / Rank / border avatar / misi / leaderboard
- [ ] Global chat, komentar, like/dislike, teman/pesan
- [ ] Banner promo berbayar / iklan
- [ ] Rating palsu (jangan tampilkan angka rating yang bukan dari data)
- [ ] Download episode (di luar spek; tidak dijanjikan)

---

## 6. Risiko & Batasan Jujur (sumber: `oploverz.ch` via scraping)

1. **Genre/Ongoing/Completed/Top**: situs tidak menjanjikan endpoint rapi;
   H-3/H-5/H-6 bersifat best-effort (filter client-side dari `meta`/`status`,
   seksi disembunyikan bila data kosong). Jangan memalsukan data.
2. **Kualitas 360p–1080p**: URL media ditangkap dari WebView; pilihan kualitas
   adalah preferensi (label + track selector bila trek tersedia), bukan
   transcode.
3. **`?attr/` di drawable `<shape>`**: dipakai untuk badge/logo; verifikasi
   runtime saat build (sudah dipakai di `bg_badge`, `app_logo_bg`).
4. **Banner carousel**: memakai thumb poster (bukan backdrop 16:9 sinematik);
   diterima sebagai diferensiasi.
5. Deduplikasi riwayat per `ep_url` UNIQUE tetap dipertahankan.

---

## 7. Urutan Eksekusi Tahap 2 (untuk pelaksana kode)

1. Token Slate/Indigo (§4) → `colors.xml` / `values-night/colors.xml`
   (nama resource lama dipertahankan, nilai diganti; tambah `border`,
   `primary_dark`, `accent_soft` bila belum ada) + re-based overlay aksen.
2. `BookmarkStore` + `BookmarkFragment`/`fragment_bookmark` + item bookmark
   (pakai ulang pola kartu) + tombol bookmark di `SeriesActivity`.
3. `PagerAdapter` + `MainActivity` + `bottom_menu` + strings → 5 tab (§3.1).
4. Home H-1…H-7 (§2.1); Riwayat §2.3 (pertahankan id & logika resume);
   Cari tetap + tambah `Hapus riwayat pencarian` (pakai `Prefs` recents).
5. Setelan §2.5 (Player + Penyimpanan + Changelog + Disclaimer/DMCA);
   `PlayerActivity` terapkan pref (§3.4); `ImageLoader` tambah `clearMemory()`;
   Clear Cache = `cacheDir.deleteRecursively()` + `clearMemory()`.
6. Build `assembleDebug` oleh koordinator → install → verifikasi visual +
   checklist §5 (grep `vip|login|auth|password|sign.?in|level|exp|chat` pada
   `res/` + `java/` harus nihil kecuali kata `experience` biasa).

---

## 8. Tahap 3 — Paritas Fungsional (diluar tampilan) — SELESAI, terverifikasi

Bedah langsung AnimeLovers v3 di emulator (detail + watch page) menghasilkan
fitur berikut — semua nyata (bukan pajangan), tanpa login/komentar:

1. **Detail**: baris meta `Studio | Tipe | Status | Tahun` (`div.spe`),
   chip genre situs (`/genres/`, ketuk = cari), sinopsis asli
   (`div.synp .entry-content`, tanpa baris "Source"), kartu + buka/tutup,
   `Episode (N)` + tombol urut (terbaru-dulu / episode-1-dulu).
2. **Ketuk episode**: sheet `Putar Sekarang / Unduh / Batal`; Unduh membuka
   tautan GoFile situs di peramban (teruji: berkas 720p tersedia).
3. **Player**: tombol kecepatan 0,5x–2x (tersimpan, label `1,25×`),
   tombol daftar episode (`Pilih Episode` → loncat), tombol server `S1…Sn`
   (muncul bila mirror >1; muat ulang + tangkap ulang media).
4. **Parser**: `Series.synopsis/genres/studio/type/released/statusWord`,
   `Episode.downloadUrl`; `loadSeries` selalu follow + merge field kosong;
   `loadSeriesLite` untuk probe ongoing (hemat fetch).
5. **AnimeClone saja**: beranda AL penuh (sapaan, lonceng, chip genre,
   pengumuman, ticker, seksi Riwayat horizontal + resume, Genre Pilihan,
   Ongoing + filter), header profil tamu di Setelan, palet malam ungu.
6. Yang SENGAJA tidak ditiru: login/Akun, VIP/tiket, EXP/level/rank/border,
   chat/komentar/like, rating situs (tidak ada datanya — tidak dikarang),
   download langsung dalam aplikasi (pakai peramban via GoFile).
