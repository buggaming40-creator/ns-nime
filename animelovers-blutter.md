# AnimeLovers — Spesifikasi UI Asli (Hasil Dekompilasi Blutter)

> Tanggal: 8 Okt 2026 · Metode: **Blutter** (`worawit/blutter`) terhadap `libapp.so` ARM64
> AnimeLovers v3.x · Dart 3.11.5 · snapshot `78da37fed6bf1489361a312568249f3f`
> Artefak: `/tmp/opencode/blutter_out/` (pp.txt, objs.txt, asm/) · Blueprint: `/tmp/opencode/blueprints/`

Dokumen ini berisi **angka asli dari dalam APK** (bukan hasil ukur screenshot): palet warna,
tipografi, spacing, radius, struktur file source, dan blueprint pohon widget per komponen.

---

## 1. Cara membangun ulang tooling

```bash
# dependensi (Fedora): dnf download capstone-devel libicu-devel -> rpm2cpio extract ke prefix lokal,
# perbaiki symlink .so putus -> export PKG_CONFIG_PATH/CPATH/CMAKE_LIBRARY_PATH
git clone --depth 1 https://github.com/worawit/blutter
cd blutter && python3 blutter.py <folder berisi libapp.so+libflutter.so> <outdir>
# auto: clone Dart SDK 3.11.5, compile parser (~5 menit, gcc>=13), lalu dump
```

Artefak keluaran:
- `pp.txt` — ObjectPool lengkap: semua konstanta (EdgeInsets, Radius, Color, TextStyle, string UI)
- `asm/<paket>/…` — **seluruh source tree** dengan simbol: nama file, class, method `build()`,
  alokasi widget high-level (`r0 = Container()`), dan referensi ke pool
- `objs.txt` — dump object & field

Blueprint generator: `/tmp/opencode/uiblueprint.py <file asm> [limit]`
(memecah `build()` jadi alur widget + konstanta yang di-resolve jadi ekspresi Flutter).

---

## 2. Struktur source (163 file `.dart` pulih)

```
animelovers/
├── main.dart, config/, models/, providers/, services/, utils/
├── pages/
│   ├── home/home_page.dart, animelist_page.dart, search_page.dart, global_chat_page.dart
│   │   └── homescreen/home_screen_first…fifth.dart   (5 bagian scroll home)
│   │   └── screen_support/ (notifikasi, chat, dm, friends, profile, leveling, cs, giveaway…)
│   ├── series/ series_page.dart, watch_page.dart, landscape_player.dart, glass_widget.dart
│   ├── payment/ vip_page.dart, qris_page.dart
│   ├── login_page.dart, splashscreen.dart, welcome/, nobar_page.dart, movie_list_page.dart…
└── widgets/
    ├── home/ glass_nav_bar.dart, continue_watching_section.dart, genre_section.dart,
    │         headers.dart, stars_background.dart, globalChatTicker.dart, popup*.dart
    ├── series/ chapter_card.dart · series_card.dart · series_complex_card.dart · expandable_text.dart
    ├── anim/ (appear_in, press_bounce, skeleton, swipe_delete_tile…)
    ├── chat/ leveling/ vip/ splash/ komentar_card.dart, mini_player_overlay.dart…
```

---

## 3. Design token asli

### 3.1 Palet (dari `Obj!Color` — field a,r,g,b)

| Peran | Hex | Catatan |
|---|---|---|
| Background utama | `#14121A` `#1B1824` `#1F1C2C` `#0F0D17` `#121019` | gelap keunguan |
| Surface / card | `#2A2735` `#282434` `#3A3D4A` | nav bar = `#282434` |
| Aksen utama (indigo) | `#6C63FF` `#6969F4` `#8C8CFF` `#9B95FF` `#8B85FF` `#9495FF` `#9FA8FF` | tombol/glow/active |
| Aksen ungu tua | `#6D33FF` | |
| Merah (badge/live) | `#E23B3B` `#FF7A9E` `#7C1F1F` | badge notification = `#E23B3B` |
| Hijau (sukses/VIP) | `#2ECC71` `#34D399` `#1F7A4D` | |
| Kuning/emas | `#FFC857` `#CD7F32` | emas VIP/badge |
| Biru | `#6FB6FF` | |
| Teks utama | `#FFFFFF` | |
| Teks sekunder | `#FFFFFF` @ **70% · 60% · 54% · 40%** | alpha via `withOpacity` |
| Teks redup | `#FFFFFF` @ 24% · 12% · 10% · 5% · `#CBD5E1` `#ADADAD` | |
| Overlay gelap | `#FFFFFF` @ 24% (`0x3DFFFFFF`) | overlay tombol play |
| Shadow aksen | `#6C63FF` @ 45% & 28% | glow bawah nav bar |

### 3.2 Tipografi

Ukuran terpakai (px logical): **7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 45, 48, 50**

- Body dominan: **12–15 px** (paling sering 12, 13, 11)
- Caption/badge: **9–11 px** (badge nav bar = 9)
- Heading: 17–21 · Display: 45/48/50 (splash/hero)
- FontWeight: `w400` (body), `w500` (judul kartu), `w600` (judul kuat/badge); juga ada w100–w300

### 3.3 Spacing (EdgeInsets asli)

| Nilai | Pemakaian |
|---|---|
| `EdgeInsets.all(20)` | padding halaman |
| `symmetric(h: 12, v: 0)` | teks dalam baris kartu |
| `symmetric(h: 8, v: 4)` | chip label |
| `symmetric(h: 5, v: 1)` | badge nav bar |
| `symmetric(h: 12, v: 10)` | tap target ikon nav |
| `fromLTRB(4, 9, 4, 4)` | tombol pill kecil |
| `fromLTRB(16, 12, 16, 12)` / `(14, 12, 14, 12)` | padding list item |
| `(0, 8, 0, 8)`, `(24, 0, 24, 24)`, `(16, 0, 16, 0)` | separator/section |

### 3.4 Radius

`r=16` (card), `r=14`, `r=8` (chip), **`r=999` (pill — nav bar & badge)**, `r=2`, `r=1`

### 3.5 Bayangan (BoxShadow dari glass_nav_bar)

- Glow indigo: `#6C63FF` alpha **0.45** dan **0.28** (nav bar aktif)
- Overlay putih: alpha 0.15 / 0.06 / 0.7 (efek glass)

---

## 4. Blueprint per komponen (contoh: kartu seri)

```
[1]  AppImage (cover, Hero)  → ClipRRect (rounded)
     · BorderRadius radius 16
[4]  Text judul          · FontWeight.w500 · #FFFFFF
[6]  Container badge     · EdgeInsets.symmetric(h: 8, v: 4)
[9]  Text badge          · FontWeight.w600 · #FFFFFF
[11] Text sub-judul      · FontWeight.w400 · #FFFFFF @54%
[16] Column
[17]   InkWell → Material (overlay #FFFFFF@24%)
[18]   Padding (4, 9, 4, 4) → tombol pill "…"
[21]  → SeriesPage (navigate)
```

Contoh `glass_nav_bar.dart` (bottom nav):
- Container pill `Radius.circular(999)`, bg `#282434`
- Item aktif: glow `BoxShadow(#6C63FF, blur…)`, ikon putih, tap target `EdgeInsets(12, 10)`
- Badge angka: bg `#E23B3B`, teks `fontSize: 9`, `EdgeInsets(5, 1)`
- Ada `LayoutBuilder + AnimatedPositioned + AnimatedSwitcher + CustomPaint` (indikator mengambang)

Blueprint lengkap komponen lain: `/tmp/opencode/blueprints/`
(`glass_nav_bar`, `continue_watching_section`, `chapter_card`, `genre_section`, `headers`,
`watch_page` [215 widget], `series_card`)

---

## 5. Cara pakai untuk clone di AnimeClone/NsNime

1. Pilih layar target → buka `asm/animelovers/pages/<…>.dart`
2. Jalankan `python3 /tmp/opencode/uiblueprint.py <file>` → urutan widget + konstanta
3. Ambil token dari bagian 3 (warna/ukuran/spacing/radius **asli**)
4. Isi teks: cari `String: "…"` di `pp.txt` (copywriting bahasa Indonesia asli app)
5. Terjemahkan ke XML native: `layout` + `colors.xml` + `dimens` + `text appearances`
   — angka dipakai langsung, tidak perlu mengukur screenshot lagi
