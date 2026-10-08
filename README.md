# Vmusix

**Listen Without Limits** — pemutar musik berbasis YouTube Music untuk Android.

Vmusix adalah aplikasi musik open-source dengan antarmuka modern (Jetpack Compose · Material 3)
yang memutar katalog YouTube Music: cari lagu, album, artis, dan playlist; putar dengan mini player
dan layar penuh; lirik sinkron; unduh untuk didengarkan offline; scrobble Last.fm.

> Dikembangkan oleh **VeruProject**.

---

## Fitur

- **Beranda** — shelf editorial YouTube Music + riwayat "Baru Diputar".
- **Cari** — lagu, album, artis, playlist (debounce, tanpa tombol cari).
- **Tiga tab**: Beranda · Cari · Lainnya (Lainnya = unduhan, playlist, 50 teratas, cache, pengaturan).
- **Mini player + layar penuh** — background blur dari artwork, penggeser posisi,
  kontrol acak/ulang, tab **Kontrol / Lirik / Antrean**.
- **Lirik** — rantai fallback otomatis:
  `YouTube → LRCLIB → Better Lyrics → KuGou → Paxsenix`;
  lirik sinkron (LRC) di-highlight mengikuti posisi lagu, ketuk baris untuk melompat.
- **Unduh (offline)** — progres, **jeda / lanjut / coba-lagi / batal**, disimpan ke
  `Music/Vmusix` (MediaStore di Android 10+, file langsung di Android 8–9).
  Lagu yang sudah diunduh diputar dari penyimpanan lokal.
- **Playlist lokal** — buka, buat, hapus (tekan lama), tambah lagu dari menu lagu.
- **Disukai (Like)** — ikon hati di pemutar dan menu lagu.
- **Manajemen cache** — lihat ukuran & jumlah file, bersihkan; bersihkan otomatis saat aplikasi dibuka (opsional).
- **Scrobble Last.fm** — now-playing + scrobble (≥30 detik), opsi aktif/nonaktif.
- **Tema** — mode gelap AMOLED / terang, 5 warna aksen, kualitas audio & unduhan dapat dipilih.
- **Tanpa iklan, tanpa login** — pemutaran langsung dari InnerTube (API resmi YouTube Music).

## Screenshots

> Belum ada tangkapan layar di repositori ini. Tambahkan folder `screenshots/`
> dan tautkan gambarnya di sini.

| Beranda | Pemutar | Lirik |
| ------- | ------- | ----- |

---

## Instalasi

1. Buka tab **Actions** di repositori ini → pilih workflow **Build Release APK** → jalankan,
   atau ambil APK dari **Releases** (tag `v*`).
2. Unduh artefak `Vmusix-release-apk`, salin ke perangkat, lalu buka untuk menginstal
   (izinkan instalasi dari sumber tidak dikenal).
3. Selesai — buka **Vmusix**.

> APK release ditandatangani dengan kunci debug agar langsung terpasang tanpa keystore.
> Untuk distribusi resmi, ganti dengan kunci milik VeruProject (lihat `app/build.gradle.kts`).

## Cara build dari sumber

Prasyarat: JDK 17, Android SDK (compileSdk 35), koneksi internet.

```bash
git clone https://github.com/VeruProject/Vmusix.git
cd Vmusix
./gradlew assembleRelease
# hasil: app/build/outputs/apk/release/app-release.apk
```

Debug (untuk pengembangan):

```bash
./gradlew assembleDebug
```

### Kunci API Last.fm (opsional)

Tanpa kunci, fitur scrobble dinonaktifkan (otomatis no-op). Dengan kunci:

1. Buat aplikasi di <https://www.last.fm/api/account/create>.
2. Isi `local.properties` (file lokal, **jangan** di-commit):

```properties
LASTFM_API_KEY=kunci_anda
LASTFM_API_SECRET=rahasia_anda
```

3. Build ulang. Di aplikasi: **Pengaturan → Integrasi → Hubungkan Last.fm**.

---

## Teknologi

| Komponen | Pilihan |
| --- | --- |
| Bahasa | Kotlin 2.0 |
| UI | Jetpack Compose, Material 3 |
| Arsitektur | MVVM + Clean (satu `AppViewModel`, layer data terpisah) |
| Pemutar | Media3 / ExoPlayer + MediaSession (layanan foreground) |
| Dependency | Hilt |
| Penyimpanan | Room (lagu disukai, riwayat, playlist, unduhan) + DataStore (pengaturan) |
| Jaringan | OkHttp + org.json (klien InnerTube buatan sendiri) |
| Gambar | Coil |
| Lirik | LRCLIB, Better Lyrics, KuGou, Paxsenix (rantai fallback) |
| Rilis | GitHub Actions (`build-release.yml`) |

## FAQ / Troubleshooting

**Lagu tidak bisa diputar.**
YouTube kadang mengubah respons InnerTube. Coba lagi nanti; struktur parsing terpusat di
`data/remote/youtube/YoutubeParser.kt`.

**Unduhan gagal / jeda tidak lanjut.**
Unduhan menyimpan posisi byte; tekan **coba-lagi** untuk memulai dari awal, atau **lanjut**
untuk melanjutkan dari posisi terakhir. File parsial yang tidak valid otomatis diulang.

**Di mana file unduhan?**
`Music/Vmusix` (terligal di aplikasi galeri/file manager).

**Lirik tidak muncul.**
Beberapa lagu memang tidak punya lirik. Coba ubah sumber lirik di Pengaturan.

**Kenapa tidak ada Shazam / pengenalan lagu?**
ShazamKit hanya tersedia di ekosistem Apple (iOS/macOS), tidak bisa dipakai di Android.

**Kenapa tidak ada kartu "Uploads / Diunggah"?**
Fitur itu membutuhkan login akun Google ke YouTube. Vmusix sengaja dibuat tanpa login.

**Mode gelap terang / salah warna.**
Atur di Pengaturan → Tampilan; warna aksen juga dipilih di sana.

## Kontribusi

Issue dan pull request dipersilakan. Jaga agar `BrandConfig.kt` tetap menjadi satu-satunya
sumber identitas (nama, warna, tagline).

## Lisensi

MIT — lihat [LICENSE](LICENSE).

---

Dibuat dengan ❤️ oleh **VeruProject** · *Listen Without Limits*
