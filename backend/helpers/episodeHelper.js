const Axios = require("./axios-instance");
const cheerio = require("cheerio");

// Dean Edwards Packer Unpacker
const unpack = (packed) => {
  try {
    // A robust regex to find packed blocks
    const packerRegex = /eval\s*\(\s*function\s*\(\s*p\s*,\s*a\s*,\s*c\s*,\s*k\s*,\s*e\s*,\s*[rd]\s*\)\s*\{.*?return\s+p.*?\}(?:\s*\(.*?\))?\s*\(\s*'(.*?)'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*'(.*?)'\.split\('\|'\)/gs;
    
    let unpacked = "";
    let match;
    // reset regex state
    packerRegex.lastIndex = 0;
    while ((match = packerRegex.exec(packed)) !== null) {
      let [_, p, a, c, k] = match;
      a = parseInt(a, 10);
      c = parseInt(c, 10);
      k = k.split('|');

      const base62Decode = (c, a) => {
        const alphabet = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ';
        if (c < a) return alphabet[c] || '';
        return base62Decode(Math.floor(c / a), a) + alphabet[c % a];
      };

      const d = {};
      for (let i = 0; i < k.length; i++) {
        if (k[i]) {
          d[base62Decode(i, a)] = k[i];
        }
      }

      const unpackedBlock = p.replace(/\b(\w+)\b/g, (token) => {
        return d[token] !== undefined ? d[token] : token;
      });
      
      unpacked = unpacked + "\n" + unpackedBlock;
    }
    return unpacked;
  } catch (e) {
    return "";
  }
};

const decodeBase64IfNeeded = (str) => {
  if (str && (str.startsWith("aHR0cHM") || str.startsWith("aHR0cD"))) {
    try {
      return Buffer.from(str, 'base64').toString('utf-8');
    } catch (e) {
      // ignore
    }
  }
  return str;
};

const get = async (url) => {
  try {
    // Add headers to request to avoid bot protection if possible
    const response = await Axios.get(url, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
        'Referer': 'https://otakudesu.blog/'
      }
    });
    
    let html = response.data;
    if (typeof html !== "string") {
      html = JSON.stringify(html);
    }
    
    // 1. Unpack any Dean Edwards packer blocks to search inside them
    const unpackedCode = unpack(html);
    const fullSource = html + "\n" + unpackedCode;
    
    // 2. Try to find direct video source via Cheerio
    const $ = cheerio.load(fullSource);
    let cheerioSrc = $("video source").attr("src") || $("video").attr("src");
    if (cheerioSrc && (cheerioSrc.startsWith("http://") || cheerioSrc.startsWith("https://"))) {
      return decodeBase64IfNeeded(cheerioSrc);
    }
    
    // 3. Try to find the file source using regex in both clean/unpacked HTML
    // Match "file":"..." or 'file':'...' or file: '...'
    const fileRegexes = [
      /["']?file["']?\s*:\s*["']([^"']+)["']/g,
      /["']?src["']?\s*:\s*["']([^"']+)["']/g,
      /source\s*:\s*["']([^"']+)["']/g
    ];
    
    for (const regex of fileRegexes) {
      regex.lastIndex = 0;
      let match;
      while ((match = regex.exec(fullSource)) !== null) {
        let streamUrl = match[1];
        streamUrl = decodeBase64IfNeeded(streamUrl);
        if (streamUrl && (streamUrl.startsWith("http://") || streamUrl.startsWith("https://")) && (streamUrl.includes(".mp4") || streamUrl.includes(".m3u8") || streamUrl.includes("stream") || streamUrl.includes("video"))) {
          return streamUrl;
        }
      }
    }
    
    // 4. Fallback: search for any raw https link ending in .m3u8 or .mp4
    const rawLinkRegex = /(https?:\/\/[^\s"'`]+?\.(?:m3u8|mp4)(?:[^\s"'`]*))/gi;
    rawLinkRegex.lastIndex = 0;
    let rawMatch = rawLinkRegex.exec(fullSource);
    if (rawMatch) {
      return rawMatch[1];
    }
    
    return "-";
  } catch (error) {
    console.error("Error extracting stream URL:", error);
    return "-";
  }
};

module.exports = { get };
