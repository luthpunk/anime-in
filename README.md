# AnimeIn 🎥

AnimeIn adalah aplikasi Android modern untuk streaming anime dengan subtitle Bahasa Indonesia. Aplikasi ini dibangun menggunakan tumpukan teknologi modern Android dan didukung oleh backend proxy serverless yang dideploy ke Vercel untuk melakukan *scraping* dan *caching* data secara stabil.

## 🚀 Fitur Utama
*   **Halaman Utama (Home):** Banner anime terpopuler, daftar anime Ongoing terbaru, dan daftar anime Completed.
*   **Pencarian (Search) & Genre:** Cari anime berdasarkan judul atau jelajahi berdasarkan kategori genre populer.
*   **Detail Anime:** Menampilkan sinopsis, info detail (skor, studio, rilis, produser), dan daftar seluruh episode yang siap diputar.
*   **Video Player Premium:** Didukung oleh **AndroidX Media3 (ExoPlayer)** untuk pemutaran video HLS (`.m3u8`) atau MP4 secara mulus, lengkap dengan cadangan **WebPlayer (WebView)** otomatis jika pemutar native mengalami kendala.
*   **Lanjut Menonton (Continue Watching):** Menyimpan progres menonton terakhir per episode (sampai ke milidetik) dan menampilkannya di halaman koleksi agar pengguna bisa langsung melanjutkan tontonan.
*   **Favorit & Riwayat:** Bookmark anime favorit Anda dan lihat riwayat tontonan sebelumnya secara offline.
*   **Mode Gelap (Dark Mode):** Desain premium gelap (streaming-first UI) untuk kenyamanan menonton maksimal.
*   **Cache Metadata Offline:** Metadata halaman utama di-cache ke DataStore (JSON) sehingga aplikasi tetap dapat dibuka saat offline.

---

## 🛠️ Tumpukan Teknologi (Tech Stack)

### **Android (Client)**
*   **Bahasa:** Kotlin
*   **UI Framework:** Jetpack Compose (Material 3)
*   **Architecture:** MVVM (Model-View-ViewModel) dengan Clean Architecture
*   **Dependency Injection:** Hilt
*   **Database:** Room (untuk Favorit, Riwayat, dan Progres Nonton)
*   **Preferences:** DataStore (untuk Tema dan Cache Metadata)
*   **Image Loading:** Coil (dengan format caching offline)
*   **Video Playback:** AndroidX Media3 (ExoPlayer) & Android WebView
*   **Network:** Retrofit & OkHttp (dengan logging body)

### **Backend (Proxy & Cache)**
*   **Runtime:** Node.js (Express.js)
*   **Scraper:** Cheerio & Axios (menargetkan situs anime terpopuler *Otakudesu.blog*)
*   **Deployment:** Vercel Serverless Functions

---

## ⚡ Deployment & Setup

### **1. Backend (Vercel)**
Backend berfungsi sebagai API proxy untuk menyembunyikan logika *scraping* dan mem-bypass pembatasan CORS.

Anda dapat mendeploy backend ini dengan sekali klik menggunakan tombol di bawah ini:

[![Deploy with Vercel](https://vercel.com/button)](https://vercel.com/new/clone?repository-url=https://github.com/luthpunk/anime-in&root-directory=backend)

Atau jika Anda sudah login ke Vercel CLI di terminal, jalankan:
```bash
cd backend
npm install
vercel --prod
```

### **2. Android (Client)**
1.  Setelah backend berhasil dideploy ke Vercel, salin URL domain yang diberikan (contoh: `https://animein-api-luthpunk.vercel.app/`).
2.  Buka file `android/app/src/main/java/com/animein/app/Config.kt`.
3.  Ubah konstanta `BASE_URL` ke URL backend Vercel Anda:
    ```kotlin
    object Config {
        const val BASE_URL = "https://YOUR_VERCEL_APP_URL.vercel.app/"
    }
    ```
4.  Buka proyek menggunakan Android Studio, sync Gradle, dan jalankan di perangkat Anda.

---

## 🤖 CI/CD (GitHub Actions)
Repository ini telah dikonfigurasi dengan GitHub Actions (`.github/workflows/android.yml`). Setiap kali ada push atau pull request ke branch `main`, workflow akan otomatis berjalan untuk:
1.  Melakukan cek kualitas kode (**Lint**).
2.  Menjalankan unit test (**Test**).
3.  Mengkompilasi aplikasi ke APK Release (**Build APK**).
4.  Mengunggah file APK Release sebagai **Artifact** yang siap diunduh secara langsung dari tab Actions di GitHub.
