/**
 * main.controller.js
 * Sumber: NontonAnimeID (https://s13.nontonanimeid.boats/)
 */
const cheerio = require("cheerio");
const Axios = require("../helpers/axios-instance");
const errors = require("../helpers/errors");

const BASE_URL = "https://s13.nontonanimeid.boats/";

/* ──────────────────────────────────────────
   HOME — episode terbaru + series terbaru
────────────────────────────────────────── */
exports.home = async (req, res) => {
  try {
    const response = await Axios.get(BASE_URL);
    const $ = cheerio.load(response.data);

    // Episode terbaru → on_going
    const on_going = [];
    $("#postbaru .misha_posts_wrap article.animeseries").each((i, el) => {
      const link = $(el).find("a").first().attr("href") || "";
      const titleSpan = $(el).find("h3.title span").first();
      const title = titleSpan.attr("data-title-default") || titleSpan.text().trim();
      const thumb = $(el).find("img").attr("src") || "";
      const episodeRaw = $(el).find(".types.episodes").text().trim();
      const id = link
        .replace(BASE_URL + "anime/", "")
        .replace(BASE_URL, "")
        .replace(/\/+$/, "");
      if (title) on_going.push({ title, id, thumb, episode: episodeRaw, link, uploaded_on: "", day_updated: "" });
    });

    // Series terbaru (tab Movie & TV)
    const complete = [];
    $(".tab-content1 .animeseries").each((i, el) => {
      const link = $(el).find("a").first().attr("href") || "";
      const titleSpan = $(el).find(".title span").first();
      const title = titleSpan.attr("data-title-default") || titleSpan.text().trim();
      const thumb = $(el).find("img").attr("src") || "";
      const score = parseFloat($(el).find(".kotakscore").text().trim()) || null;
      const id = link.replace(BASE_URL + "anime/", "").replace(/\/+$/, "");
      if (title) complete.push({ title, id, thumb, score, link, uploaded_on: "" });
    });

    res.status(200).json({
      status: "success",
      baseUrl: BASE_URL,
      home: { on_going, complete, completed: complete },
    });
  } catch (err) {
    console.error("[NontonAnimeID] home error:", err.message);
    errors.requestFailed(req, res, err);
  }
};

/* ──────────────────────────────────────────
   ONGOING LIST
────────────────────────────────────────── */
exports.onGoingAnimeList = async (req, res) => {
  try {
    const response = await Axios.get(BASE_URL + "ongoing-list/");
    const $ = cheerio.load(response.data);

    const animeList = [];
    $(".gacha-grid a.gacha-card").each((i, el) => {
      const link = $(el).attr("href") || "";
      const title = $(el).find("h3.title").text().trim();
      const thumb = $(el).find("img").attr("src") || "";
      const currentEp = $(el).find(".current-ep").text().trim();
      const totalEp = $(el).find(".total-ep").text().trim();
      const score = parseFloat($(el).find(".skor-angka").text().replace(/[()]/g, "")) || null;
      const isHot = $(el).find(".hot-tag").length > 0;
      const id = link.replace(BASE_URL + "anime/", "").replace(/\/+$/, "");
      const episode = totalEp && totalEp !== "?" ? `${currentEp} / ${totalEp}` : `${currentEp} / ??`;
      if (title) animeList.push({ title, id, thumb, episode, score, is_hot: isHot, link, day_updated: "", uploaded_on: "" });
    });

    res.status(200).json({ status: "success", baseUrl: BASE_URL + "ongoing-list/", animeList });
  } catch (err) {
    console.error("[NontonAnimeID] ongoing error:", err.message);
    errors.requestFailed(req, res, err);
  }
};

/* ──────────────────────────────────────────
   COMPLETE ANIME LIST (popular series)
────────────────────────────────────────── */
exports.completeAnimeList = async (req, res) => {
  const params = req.params.page;
  const page = !params || params === "1" ? "" : `page/${params}/`;
  const fullUrl = `${BASE_URL}anime/${page}`;
  try {
    const response = await Axios.get(fullUrl);
    const $ = cheerio.load(response.data);

    const animeList = [];
    $(".as-anime-grid a.as-anime-card, article.animeseries").each((i, el) => {
      const link = $(el).attr("href") || $(el).find("a").first().attr("href") || "";
      const titleEl = $(el).find("h3.as-anime-title, h3.title span").first();
      const title = titleEl.attr("data-title-default") || titleEl.text().trim();
      const thumb = $(el).find("img").attr("src") || "";
      const score = parseFloat($(el).find(".as-rating, .kotakscore").text().replace("⭐", "")) || null;
      const id = link.replace(BASE_URL + "anime/", "").replace(/\/+$/, "");
      if (title) animeList.push({ title, id, thumb, score, link, uploaded_on: "" });
    });

    res.status(200).json({ status: "success", baseUrl: fullUrl, animeList });
  } catch (err) {
    console.error("[NontonAnimeID] completeAnimeList error:", err.message);
    errors.requestFailed(req, res, err);
  }
};

/* ──────────────────────────────────────────
   SCHEDULE (Jadwal Rilis)
────────────────────────────────────────── */
exports.schedule = async (req, res) => {
  try {
    const response = await Axios.get(BASE_URL + "jadwal-rilis/");
    const $ = cheerio.load(response.data);

    const scheduleList = [];
    $(".schedule-day, .jadwal-hari, .kglist321, .schedule-table tr").each((i, dayEl) => {
      const day = $(dayEl).find("h2, h3, .day-name, th").first().text().trim();
      const animeList = [];
      $(dayEl).find("a, li a, td a").each((j, a) => {
        const anime_name = $(a).text().trim();
        const link = $(a).attr("href") || "";
        const id = link.replace(BASE_URL + "anime/", "").replace(/\/+$/, "");
        if (anime_name && link) animeList.push({ anime_name, id, link });
      });
      if (day) scheduleList.push({ day, animeList });
    });

    res.status(200).json({ status: "success", baseUrl: BASE_URL + "jadwal-rilis/", scheduleList });
  } catch (err) {
    console.error("[NontonAnimeID] schedule error:", err.message);
    errors.requestFailed(req, res, err);
  }
};

/* ──────────────────────────────────────────
   GENRE LIST
────────────────────────────────────────── */
exports.genre = async (req, res) => {
  try {
    const response = await Axios.get(BASE_URL + "genres/");
    const $ = cheerio.load(response.data);

    const genreList = [];
    $(".genre-grid-container a.genre-grid-card").each((i, el) => {
      const link = $(el).attr("href") || "";
      const genre_name = $(el).find("h3.genre-name").text().trim();
      const thumb = $(el).find("img").attr("src") || "";
      const id = link.replace(BASE_URL + "genres/", "").replace(/\/+$/, "");
      const countText = $(el).find(".detail-item.count span").last().text().trim();
      const ongoingText = $(el).find(".detail-item.ongoing span").last().text().trim();
      if (genre_name) genreList.push({ genre_name, id, link, image_link: thumb, count: countText, ongoing: ongoingText });
    });

    res.status(200).json({ status: "success", genreList });
  } catch (err) {
    console.error("[NontonAnimeID] genre error:", err.message);
    errors.requestFailed(req, res, err);
  }
};

/* ──────────────────────────────────────────
   ANIME BY GENRE
────────────────────────────────────────── */
exports.animeByGenre = async (req, res) => {
  const { id, pageNumber } = req.params;
  const page = !pageNumber || pageNumber === "1" ? "" : `page/${pageNumber}/`;
  const fullUrl = `${BASE_URL}genres/${id}/${page}`;
  try {
    const response = await Axios.get(fullUrl);
    const $ = cheerio.load(response.data);

    const animeList = [];
    // Halaman genre biasanya memakai .as-anime-grid
    $(".as-anime-grid a.as-anime-card").each((i, el) => {
      const link = $(el).attr("href") || "";
      const titleEl = $(el).find("h3.as-anime-title");
      const title = titleEl.attr("data-title-default") || titleEl.text().trim();
      const thumb = $(el).find("img").attr("src") || "";
      const score = parseFloat($(el).find(".as-rating").text().replace("⭐", "")) || null;
      const type = $(el).find(".as-type").text().replace("📺", "").trim();
      const animeId = link.replace(BASE_URL + "anime/", "").replace(/\/+$/, "");
      if (title) animeList.push({ anime_name: title, id: animeId, thumb, score, type, link });
    });

    // Fallback: article.animeseries
    if (animeList.length === 0) {
      $("article.animeseries").each((i, el) => {
        const link = $(el).find("a").first().attr("href") || "";
        const titleSpan = $(el).find("h3.title span").first();
        const title = titleSpan.attr("data-title-default") || titleSpan.text().trim();
        const thumb = $(el).find("img").attr("src") || "";
        const animeId = link.replace(BASE_URL + "anime/", "").replace(/\/+$/, "");
        if (title) animeList.push({ anime_name: title, id: animeId, thumb, link });
      });
    }

    res.status(200).json({ status: "success", baseUrl: fullUrl, animeList });
  } catch (err) {
    console.error("[NontonAnimeID] animeByGenre error:", err.message);
    errors.requestFailed(req, res, err);
  }
};

/* ──────────────────────────────────────────
   SEARCH
────────────────────────────────────────── */
exports.search = async (req, res) => {
  const query = req.params.query;
  const fullUrl = `${BASE_URL}?s=${encodeURIComponent(query)}`;
  try {
    const response = await Axios.get(fullUrl);
    const $ = cheerio.load(response.data);

    const search_results = [];
    $(".as-anime-grid a.as-anime-card").each((i, el) => {
      const link = $(el).attr("href") || "";
      const titleEl = $(el).find("h3.as-anime-title");
      const title = titleEl.attr("data-title-default") || titleEl.text().trim();
      const thumb = $(el).find("img").attr("src") || "";
      const score = parseFloat($(el).find(".as-rating").text().replace("⭐", "")) || null;
      const type = $(el).find(".as-type").text().replace("📺", "").trim();
      const id = link.replace(BASE_URL + "anime/", "").replace(BASE_URL, "").replace(/\/+$/, "");
      if (link) search_results.push({ title, id, thumb, score, type, link });
    });

    res.status(200).json({ status: "success", baseUrl: fullUrl, search_results });
  } catch (err) {
    console.error("[NontonAnimeID] search error:", err.message);
    res.status(200).json({
      status: "success",
      fallback: true,
      message: "Search upstream blocked; returning empty results.",
      baseUrl: fullUrl,
      search_results: [],
    });
  }
};
