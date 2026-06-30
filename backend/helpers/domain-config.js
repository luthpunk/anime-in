/**
 * DomainConfig — Remote Config Domain Resolver
 *
 * Mengambil domain aktif Otakudesu dari file JSON di GitHub.
 * Jika domain Otakudesu berubah, cukup update config/domain.json di repo
 * tanpa perlu rebuild APK maupun redeploy backend.
 *
 * URL Config:
 * https://raw.githubusercontent.com/luthpunk/anime-in/main/config/domain.json
 *
 * Pola yang dipakai:
 * - Saat module dimuat, langsung fetch domain di latar belakang
 * - getDomain() bersifat sinkron, mengembalikan nilai cache atau default
 * - Cache di-refresh setiap 1 jam sekali di latar belakang
 * - Controller tidak perlu diubah (tidak ada async/await di controller)
 */

const axios = require('axios')

const REMOTE_CONFIG_URL =
  'https://raw.githubusercontent.com/luthpunk/anime-in/main/config/domain.json'

const DEFAULT_BASE_URL = 'https://otakudesu.blog/'
const FALLBACK_DOMAINS = ['https://otakudesu.cloud/', 'https://otakudesu.lol/']
const CACHE_TTL_MS = 60 * 60 * 1000 // 1 jam

// State internal
let _cachedDomain = DEFAULT_BASE_URL
let _lastFetched = 0
let _isFetching = false

/**
 * Fetch domain dari remote config GitHub.
 * Jika gagal, coba fallback domain satu per satu.
 * Hasil disimpan di cache.
 */
async function _refreshDomain() {
  if (_isFetching) return
  _isFetching = true

  try {
    const res = await axios.get(REMOTE_CONFIG_URL, { timeout: 6000 })
    const domain = res.data?.otakudesu?.baseUrl

    if (domain && domain.startsWith('http')) {
      _cachedDomain = domain
      _lastFetched = Date.now()
      console.log(`[DomainConfig] Domain aktif dari remote config: ${domain}`)
      _isFetching = false
      return
    }
  } catch (err) {
    console.warn(`[DomainConfig] Gagal fetch remote config: ${err.message}`)
  }

  // Coba fallback domain satu per satu
  for (const candidate of [DEFAULT_BASE_URL, ...FALLBACK_DOMAINS]) {
    try {
      await axios.get(candidate, { timeout: 5000 })
      _cachedDomain = candidate
      _lastFetched = Date.now()
      console.log(`[DomainConfig] Menggunakan fallback domain: ${candidate}`)
      _isFetching = false
      return
    } catch {
      console.warn(`[DomainConfig] Domain tidak dapat dijangkau: ${candidate}`)
    }
  }

  // Semua gagal — tetap pakai default agar app tidak crash
  _lastFetched = Date.now()
  _isFetching = false
  console.warn('[DomainConfig] Semua domain gagal, tetap menggunakan default.')
}

/**
 * Getter sinkron untuk domain aktif.
 * Mengembalikan nilai cache saat ini (atau default jika belum di-fetch).
 * Otomatis memicu refresh di latar belakang jika cache sudah kedaluwarsa.
 *
 * @returns {string} baseUrl aktif
 */
function getDomain() {
  const now = Date.now()
  const cacheExpired = now - _lastFetched > CACHE_TTL_MS

  if (cacheExpired && !_isFetching) {
    // Refresh di latar belakang tanpa memblokir response
    _refreshDomain().catch(() => {})
  }

  return _cachedDomain
}

/**
 * Paksa invalidate cache dan fetch ulang.
 * Berguna jika ada endpoint admin untuk trigger refresh manual.
 */
function invalidateCache() {
  _lastFetched = 0
  _refreshDomain().catch(() => {})
}

// Auto-fetch saat module pertama kali dimuat
_refreshDomain().catch(() => {})

module.exports = { getDomain, invalidateCache }
