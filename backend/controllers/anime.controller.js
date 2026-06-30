/**
 * anime.controller.js
 * Sumber: NontonAnimeID (https://s13.nontonanimeid.boats/)
 */
const cheerio = require("cheerio");
const Axios = require("../helpers/axios-instance");
const errors = require("../helpers/errors");

const BASE_URL = "https://s13.nontonanimeid.boats/";

/* ─── Helper ─── */
function normalizeAnimeId(rawId) {
  let id = String(rawId || "");
  try { id = decodeURIComponent(id); } catch (_) {}
  return id
    .trim()
    .replace(/^https?:\/\/[^/]+\/anime\//, "")
    .replace(/^anime\//, "")
    .replace(/^\/+/, "")
    .replace(/\/+$/, "");
}

function normalizeEpsId(rawId) {
  let id = String(rawId || "");
  try { id = decodeURIComponent(id); } catch (_) {}
  return id
    .trim()
    .replace(/^https?:\/\/[^/]+\//, "")
    .replace(/^\/+/, "")
    .replace(/\/+$/, "");
}

/* ──────────────────────────────────────────
   DETAIL ANIME
   GET /api/anime/:id
────────────────────────────────────────── */
exports.detailAnime = async (req, res) => {
  const animeId = normalizeAnimeId(req.params.id || req.params[0]);
  const fullUrl = `${BASE_URL}anime/${animeId}/`;
  try {
    const response = await Axios.get(fullUrl);
    const $ = cheerio.load(response.data);

    const obj = {};
    obj.anime_id = animeId;
    obj.link = fullUrl;

    // Judul
    const titleSpan = $("h1.entry-title span").first();
    obj.title = titleSpan.attr("data-title-default")
      || $("h1.entry-title").text().replace(/Sub Indo/gi, "").trim();
    obj.japanese = titleSpan.attr("data-title-jp") || "";
    obj.english = titleSpan.attr("data-title-en") || obj.title;

    // Thumb & score
    obj.thumb = $(".anime-card__sidebar img").first().attr("src") || "";
    obj.score = parseFloat($(".anime-card__score .value").text().trim()) || null;
    obj.type = $(".anime-card__score .type").text().trim() || "";

    // Synopsis
    obj.synopsis = $(".synopsis-prose p").text().trim() || $(".synopsis-prose").text().trim();

    // Status & quick info
    obj.status = $(".info-item.status-airing").text().trim()
      || $(".info-item").first().text().trim();

    const infoItems = $(".anime-card__quick-info .info-item");
    obj.total_episode = infoItems.eq(1).text().replace(/Episodes?/i, "").trim();
    obj.duration = infoItems.eq(2).text().trim();
    const seasonEl = infoItems.filter(function () { return $(this).find("a").length > 0; }).first();
    obj.release_date = seasonEl.find("a").text().trim() || "";

    // Detail list
    $(".details-list li").each((i, el) => {
      const label = $(el).find(".detail-label").text().replace(":", "").trim().toLowerCase();
      const value = $(el).text().replace($(el).find(".detail-label").text(), "").trim();
      if (label === "studios") obj.studio = value;
      if (label === "aired") obj.aired = value;
      if (label === "rating") obj.content_rating = value;
      if (label === "synonyms") obj.synonyms = value;
    });

    // Genre list
    const genre_list = [];
    $(".anime-card__genres.in-tab a.genre-tag").each((i, el) => {
      const genre_link = $(el).attr("href") || "";
      const genre_name = $(el).text().trim();
      const genre_id = genre_link.replace(BASE_URL + "genres/", "").replace(/\/+$/, "");
      genre_list.push({ genre_name, genre_id, genre_link });
    });
    obj.genre_list = genre_list;

    // Daftar episode (page 1 — biasanya 20 ep terbaru)
    const episode_list = [];
    $(".episode-list-items a.episode-item").each((i, el) => {
      const link = $(el).attr("href") || "";
      const epTitle = $(el).find(".ep-title").text().trim();
      const uploaded_on = $(el).find(".ep-date").text().trim();
      const id = link.replace(BASE_URL, "").replace(/\/+$/, "");
      if (link) episode_list.push({ title: epTitle, id, link, uploaded_on });
    });
    obj.episode_list = episode_list.length > 0
      ? episode_list
      : [{ title: "Belum ada episode", id: "", link: "", uploaded_on: "" }];

    // Batch (tidak tersedia di NontonAnimeID)
    obj.batch_link = { id: "", link: "" };

    res.status(200).json(obj);
  } catch (err) {
    console.error("[NontonAnimeID] detailAnime error:", err.message);
    errors.requestFailed(req, res, err);
  }
};

/* ──────────────────────────────────────────
   BATCH ANIME (NontonAnimeID tidak punya batch)
   GET /api/batch/:id
────────────────────────────────────────── */
exports.batchAnime = async (req, res) => {
  res.status(200).json({
    status: "info",
    message: "Fitur batch tidak tersedia di sumber NontonAnimeID.",
    data: null,
  });
};

/* ──────────────────────────────────────────
   EPISODE
   GET /api/eps/:id
────────────────────────────────────────── */
exports.epsAnime = async (req, res) => {
  const epsId = normalizeEpsId(req.params.id || req.params[0]);
  const fullUrl = `${BASE_URL}${epsId}/`;
  try {
    const response = await Axios.get(fullUrl);
    const $ = cheerio.load(response.data);

    const obj = {};
    obj.id = epsId;
    obj.baseUrl = fullUrl;

    // Judul
    const titleSpan = $("h1.entry-title span").first();
    const titleBase = titleSpan.attr("data-title-default") || titleSpan.text().trim();
    const episodePart = $("h1.entry-title").text()
      .replace(titleBase, "")
      .replace(/Sub Indo/gi, "")
      .trim();
    obj.title = `${titleBase} ${episodePart}`.trim();

    // Stream embed (iframe default server pertama)
    obj.link_stream = $("#videoku iframe, #pembed iframe").attr("data-src")
      || $("#videoku iframe, #pembed iframe").attr("src")
      || "-";

    // Server list
    const server_list = [];
    $("ul.tabs1.player li.tab-link.serverplayer").each((i, el) => {
      server_list.push({
        server_name: $(el).find("span").text().trim(),
        data_post: $(el).attr("data-post") || "",
        data_type: $(el).attr("data-type") || "",
        data_nume: $(el).attr("data-nume") || "",
      });
    });
    obj.server_list = server_list;

    // Download links
    const download_links = [];
    $(".listlink a").each((i, el) => {
      download_links.push({
        host: $(el).text().trim(),
        link: $(el).attr("href") || "",
      });
    });
    obj.download_links = download_links;

    // Navigasi prev/next
    const navLinks = $(".naveps .nvs");
    obj.prev_episode = navLinks.first().find("a").attr("href") || null;
    obj.next_episode = navLinks.last().find("a").attr("href") || null;
    obj.anime_link = $(".naveps .nvsc a").attr("href") || null;

    // Thumbnail episode
    obj.thumb = $(".featuredimgs img").attr("src") || "";

    res.status(200).json(obj);
  } catch (err) {
    console.error("[NontonAnimeID] epsAnime error:", err.message);
    errors.requestFailed(req, res, err);
  }
};

/* ──────────────────────────────────────────
   EPISODE MIRROR (server embed dinamis)
   POST /api/eps/:animeId/mirror/
────────────────────────────────────────── */
exports.epsMirror = async (req, res) => {
  // NontonAnimeID menggunakan WordPress AJAX untuk load embed
  // Server embed di-load lewat POST wp-admin/admin-ajax.php
  const { post, type, nume } = req.body || {};
  if (!post || !type || !nume) {
    return res.status(400).json({ status: "error", message: "Butuh body: post, type, nume" });
  }

  const ajaxUrl = `${BASE_URL}wp-admin/admin-ajax.php`;
  try {
    const form = new URLSearchParams();
    form.append("action", "player_ajax");
    form.append("post", post);
    form.append("nume", nume);
    form.append("type", type);

    const response = await Axios.post(ajaxUrl, form.toString(), {
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        "X-Requested-With": "XMLHttpRequest",
        "Referer": BASE_URL,
      },
    });

    const $ = cheerio.load(response.data || "");
    const embedUrl = $("iframe").attr("src") || $("iframe").attr("data-src") || response.data || "-";

    res.status(200).json({
      status: "success",
      embed_url: embedUrl,
      raw: response.data,
    });
  } catch (err) {
    console.error("[NontonAnimeID] epsMirror error:", err.message);
    res.status(200).json({ status: "error", message: err.message, embed_url: "-" });
  }
};
