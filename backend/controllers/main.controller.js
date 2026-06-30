const cheerio = require("cheerio");
const url = require("../helpers/base-url");
const Axios = require("../helpers/axios-instance");
const baseUrl = url.baseUrl;
const completeAnime = url.completeAnime;
const onGoingAnime = url.onGoingAnime;
const errors = require("../helpers/errors");
const ImageList = require("../helpers/image_genre").ImageList;
const e = require("express");

exports.home = (req, res) => {
  let home = {};
  let on_going = [];
  let complete = [];
  Axios.get(baseUrl)
    .then((response) => {
      const $ = cheerio.load(response.data);
      const element = $(".venz");
      let episode, uploaded_on, day_updated, thumb, title, link, id;
      element
        .children()
        .eq(0)
        .find("ul > li")
        .each(function () {
          $(this)
            .find(".thumb > a")
            .filter(function () {
              title = $(this).find(".thumbz > h2").text();
              thumb = $(this).find(".thumbz > img").attr("src");
              link = $(this).attr("href");
              id = link.replace(`${baseUrl}anime/`, "");
            });
          uploaded_on = $(this).find(".newnime").text();
          episode = $(this).find(".epz").text().replace(" ", "");
          day_updated = $(this).find(".epztipe").text().replace(" ", "");
          on_going.push({
            title,
            id,
            thumb,
            episode,
            uploaded_on,
            day_updated,
            link,
          });
        });
      home.on_going = on_going;
      return response;
    })
    .then((response) => {
      const $ = cheerio.load(response.data);
      const element = $(".venz");
      let episode, uploaded_on, score, thumb, title, link, id;
      element
        .children()
        .eq(1)
        .find("ul > li")
        .each(function () {
          $(this)
            .find(".thumb > a")
            .filter(function () {
              title = $(this).find(".thumbz > h2").text();
              thumb = $(this).find(".thumbz > img").attr("src");
              link = $(this).attr("href");
              id = link.replace(`${baseUrl}anime/`, "");
            });
          uploaded_on = $(this).find(".newnime").text();
          episode = $(this).find(".epz").text().replace(" ", "");
          score = parseFloat($(this).find(".epztipe").text().replace(" ", ""));
          complete.push({
            title,
            id,
            thumb,
            episode,
            uploaded_on,
            score,
            link,
          });
        });
      home.complete = complete;
      home.completed = complete;
      res.status(200).json({
        status: "success",
        baseUrl: baseUrl,
        home,
      });
    })
    .catch((e) => {
      console.log(e.message);
      buildSearchBasedHome()
        .then((home) => {
          res.status(200).json({
            status: "success",
            fallback: true,
            message: "Homepage upstream blocked; using search fallback.",
            baseUrl: baseUrl,
            home,
          });
        })
        .catch((fallbackErr) => {
          console.log(fallbackErr.message);
          errors.requestFailed(req, res, e);
        });
    });
};
exports.completeAnimeList = (req, res) => {
  const params = req.params.page;
  const page =
    typeof params === "undefined" ? "" : params === "1" ? "" : `page/${params}`;
  const fullUrl = `${baseUrl}${completeAnime}${page}`;
  console.log(fullUrl);
  Axios.get(fullUrl)
    .then((response) => {
      const $ = cheerio.load(response.data);
      const element = $(".venz");
      let animeList = [];
      let episode, uploaded_on, score, thumb, title, link, id;
      element
        .children()
        .eq(0)
        .find("ul > li")
        .each(function () {
          $(this)
            .find(".thumb > a")
            .filter(function () {
              title = $(this).find(".thumbz > h2").text();
              thumb = $(this).find(".thumbz > img").attr("src");
              link = $(this).attr("href");
              id = link.replace(`${baseUrl}anime/`, "");
            });
          uploaded_on = $(this).find(".newnime").text();
          episode = $(this).find(".epz").text().replace(" ", "");
          score = parseFloat($(this).find(".epztipe").text().replace(" ", ""));
          animeList.push({
            title,
            id,
            thumb,
            episode,
            uploaded_on,
            score,
            link,
          });
        });
      res.status(200).json({
        status: "success",
        baseUrl: fullUrl,
        animeList,
      });
    })
    .catch((err) => {
      console.log(err.message);
      errors.requestFailed(req, res, err);
    });
};
exports.onGoingAnimeList = (req, res) => {
  const params = req.params.page;
  const page = typeof params === "undefined" ? "" : params === "1" ? "" : `page/${params}`;
  const fullUrl = `${baseUrl}${onGoingAnime}${page}`;
  // const url = `${baseUrl}${onGoingAnime}`;
  // console.log(url);
  Axios.get(fullUrl)
    .then((response) => {
      const $ = cheerio.load(response.data);
      const element = $(".venz");
      let animeList = [];
      let episode, uploaded_on, day_updated, thumb, title, link, id;
      element
        .children()
        .eq(0)
        .find("ul > li")
        .each(function () {
          $(this)
            .find(".thumb > a")
            .filter(function () {
              title = $(this).find(".thumbz > h2").text();
              thumb = $(this).find(".thumbz > img").attr("src");
              link = $(this).attr("href");
              id = link.replace(`${baseUrl}anime/`, "");
            });
          uploaded_on = $(this).find(".newnime").text();
          episode = $(this).find(".epz").text().replace(" ", "");
          day_updated = $(this).find(".epztipe").text().replace(" ", "");
          animeList.push({
            title,
            id,
            thumb,
            episode,
            uploaded_on,
            day_updated,
            link,
          });
        });
      res.status(200).json({
        status: "success",
        baseUrl: baseUrl,
        animeList,
      });
    })
    .catch((err) => {
      console.log(err.message);
      errors.requestFailed(req, res, err);
    });
};
exports.schedule = (req, res) => {
  Axios.get(baseUrl + url.schedule).then((response) => {
    const $ = cheerio.load(response.data);
    const element = $(".kgjdwl321");
    let animeList = [];
    let scheduleList = [];
    let day;
    let anime_name, link, id;
    element.find(".kglist321").each(function () {
      day = $(this).find("h2").text();
      animeList = [];
      $(this)
        .find("ul > li")
        .each(function () {
          anime_name = $(this).find("a").text();
          link = $(this).find("a").attr("href");
          id = link.replace(baseUrl + "anime/", "");
          animeList.push({ anime_name, id, link });
        });
      scheduleList.push({ day, animeList });
    });
    res.json({ scheduleList });
  });
};
exports.genre = (req, res) => {
  const fullUrl = baseUrl + url.genreList;
  Axios.get(fullUrl)
    .then((response) => {
      const $ = cheerio.load(response.data);
      const element = $(".genres");
      let genreList = [];
      element.find("li > a").each(function (i, el) {
        let object = {};
        object.genre_name = $(el).text();
        object.id = $(el).attr("href").replace("/genres/", "");
        object.link = baseUrl + $(el).attr("href");
        object.image_link = ImageList[i];
        genreList.push(object);
      });
      res.json({ genreList });
    })
    .catch((err) => {
      console.log(err.message);
      errors.requestFailed(req, res, err);
    });
};
exports.animeByGenre = (req, res) => {
  const pageNumber = req.params.pageNumber;
  const id = req.params.id;
  const fullUrl = baseUrl + `genres/${id}/page/${pageNumber}`;
  console.log(fullUrl);
  Axios.get(fullUrl)
    .then((response) => {
      const $ = cheerio.load(response.data);
      const element = $(".page");
      let animeList = [];
      let genreList = [];
      let object = {};
      let genre_name, genre_link, genre_id;
      element.find(".col-md-4").each(function () {
        object = {};
        object.anime_name = $(this).find(".col-anime-title").text();
        object.thumb = $(this).find('div.col-anime-cover > img').attr('src')
        object.link = $(this).find(".col-anime-title > a").attr("href");
        object.id = $(this)
          .find(".col-anime-title > a")
          .attr("href")
          .replace(`${baseUrl}anime/`, "");
        object.studio = $(this).find(".col-anime-studio").text();
        object.episode = $(this).find(".col-anime-eps").text();
        object.score = parseFloat($(this).find(".col-anime-rating").text());
        object.release_date = $(this).find(".col-anime-date").text();
        genreList = [];
        $(this)
          .find(".col-anime-genre > a")
          .each(function () {
            genre_name = $(this).text();
            genre_link = $(this).attr("href");
            genre_id = genre_link.replace(`${baseUrl}genres/`, "");
            genreList.push({ genre_name, genre_link, genre_id });
            object.genre_list = genreList;
          });
        animeList.push(object);
      });
      res.send({
        status: "success",
        baseUrl: fullUrl,
        animeList,
      });
    })
    .catch((err) => {
      errors.requestFailed(req, res, err);
    });
};
exports.search = (req, res) => {
  const query = req.params.query;
  const fullUrl = `${baseUrl}?s=${encodeURIComponent(query)}&post_type=anime`;
  Axios.get(fullUrl).then((response) => {
    const $ = cheerio.load(response.data);
    const element = $(".page");
    let obj = {};
    let anime_list = [];
    (obj.status = "success"), (obj.baseUrl = fullUrl);
    if(element.find("ul > li").length === 0){
      obj.search_results = [];
    }else {
      element.find("ul > li").each(function () {
        const genre_list = [];
        $(this).find(".set").find("a").each(function () {
            const genreLink = $(this).attr("href") || "";
            const genreName = $(this).text();
            const genre_result = {
              genre_title: genreName,
              genre_name: genreName,
              genre_link: genreLink,
              genre_id: genreLink.replace(`${baseUrl}genres/`, ""),
            };
            genre_list.push(genre_result);
          });
        const link = $(this).find("h2 > a").attr("href") || "";
        const results = {
          thumb: $(this).find("img").attr("src"),
          title: $(this).find("h2").text(),
          link,
          id: normalizeAnimeId(link),
          status: $(this).find(".set").eq(1).text().replace("Status : ", ""),
          score: parseFloat(
            $(this).find(".set").eq(2).text().replace("Rating : ", "")
          ),
          genre_list,
        };
        anime_list.push(results);
        obj.search_results = anime_list;
      });
    }
    res.send(obj);
  }).catch((err) => {
    console.log(err.message);
    res.status(200).json({
      status: "success",
      fallback: true,
      message: "Search upstream blocked; returning empty results.",
      baseUrl: fullUrl,
      search_results: [],
    });
  });
};

async function buildSearchBasedHome() {
  const seedQueries = [
    "one piece",
    "kimetsu",
    "jujutsu",
    "boruto",
    "horimiya",
    "isekai",
    "spy family",
    "tensura",
  ];
  const searchGroups = await Promise.all(
    seedQueries.map((query) => fetchSearchResults(query).catch(() => []))
  );
  const allResults = uniqueAnimeResults(searchGroups.flat());
  const ongoingResults = allResults.filter((item) => /ongoing/i.test(item.status));
  const completedResults = allResults.filter((item) => /completed/i.test(item.status));

  return {
    on_going: (ongoingResults.length ? ongoingResults : allResults).slice(0, 12).map((item) => ({
      title: item.title,
      id: item.id,
      thumb: item.thumb,
      episode: item.status || "Anime",
      uploaded_on: "",
      day_updated: "",
      link: item.link,
    })),
    complete: (completedResults.length ? completedResults : allResults).slice(0, 12).map((item) => ({
      title: item.title,
      id: item.id,
      thumb: item.thumb,
      episode: item.status || "Anime",
      uploaded_on: "",
      score: Number.isFinite(item.score) ? item.score : null,
      link: item.link,
    })),
    completed: (completedResults.length ? completedResults : allResults).slice(0, 12).map((item) => ({
      title: item.title,
      id: item.id,
      thumb: item.thumb,
      episode: item.status || "Anime",
      uploaded_on: "",
      score: Number.isFinite(item.score) ? item.score : null,
      link: item.link,
    })),
  };
}

async function fetchSearchResults(query) {
  const fullUrl = `${baseUrl}?s=${encodeURIComponent(query)}&post_type=anime`;
  const response = await Axios.get(fullUrl);
  const $ = cheerio.load(response.data);
  const results = [];

  $(".page").find("ul > li").each(function () {
    const link = $(this).find("h2 > a").attr("href") || "";
    if (!link) return;

    results.push({
      thumb: $(this).find("img").attr("src") || "",
      title: $(this).find("h2").text().trim(),
      link,
      id: normalizeAnimeId(link),
      status: $(this).find(".set").eq(1).text().replace("Status : ", "").trim(),
      score: parseFloat($(this).find(".set").eq(2).text().replace("Rating : ", "")),
    });
  });

  return results;
}

function uniqueAnimeResults(results) {
  const seen = new Set();
  return results.filter((item) => {
    if (!item.id || seen.has(item.id)) return false;
    seen.add(item.id);
    return true;
  });
}

function normalizeAnimeId(rawId) {
  let decodedId = String(rawId || "");
  try {
    decodedId = decodeURIComponent(decodedId);
  } catch (e) {
    // Keep the original value when it is not a valid encoded component.
  }

  const id = decodedId
    .trim()
    .split("?")[0]
    .split("#")[0]
    .replace(/^https?:\/\/otakudesu\.(blog|cloud)\/anime\//, "")
    .replace(/^anime\//, "")
    .replace(/^\/+/, "")
    .replace(/\/+$/, "");

  return id ? `${id}/` : "";
}
