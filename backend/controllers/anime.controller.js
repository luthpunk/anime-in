const url = require("../helpers/base-url");
const Axios = require("../helpers/axios-instance");
const cheerio = require("cheerio");
const errors = require("../helpers/errors");
const episodeHelper = require("../helpers/episodeHelper");
const {baseUrl} = require("../helpers/base-url");
const e = require("express");

exports.detailAnime = async (req, res) => {
  const id = normalizeAnimeId(req.params.id || req.params[0]);
  const fullUrl = url.baseUrl + `anime/${id}`;
  // console.log(fullUrl);
  try {
    const response = await Axios.get(fullUrl);

    const $ = cheerio.load(response.data);
    const detailElement = $(".venser").find(".fotoanime");
    const epsElement = $("#_epslist").html();
    let object = {};
    let episode_list = [];
    object.thumb = detailElement.find("img").attr("src");
    object.anime_id = id;
    let genre_name, genre_id, genre_link;
    let genreList = [];

    object.synopsis = $("#venkonten > div.venser > div.fotoanime > div.sinopc")
      .find("p")
      .text();

    detailElement.find(".infozin").filter(function () {
      object.title = $(this)
        .find("p")
        .children()
        .eq(0)
        .text()
        .replace("Judul: ", "");
      object.japanase = $(this)
        .find("p")
        .children()
        .eq(1)
        .text()
        .replace("Japanese: ", "");
      object.score = parseFloat(
        $(this).find("p").children().eq(2).text().replace("Skor: ", "")
      );
      object.producer = $(this)
        .find("p")
        .children()
        .eq(3)
        .text()
        .replace("Produser:  ", "");
      object.type = $(this)
        .find("p")
        .children()
        .eq(4)
        .text()
        .replace("Tipe: ", "");
      object.status = $(this)
        .find("p")
        .children()
        .eq(5)
        .text()
        .replace("Status: ", "");
      object.total_episode = parseInt(
        $(this).find("p").children().eq(6).text().replace("Total Episode: ", "")
      );
      object.duration = $(this)
        .find("p")
        .children()
        .eq(7)
        .text()
        .replace("Durasi: ", "");
      object.release_date = $(this)
        .find("p")
        .children()
        .eq(8)
        .text()
        .replace("Tanggal Rilis: ", "");
      object.studio = $(this)
        .find("p")
        .children()
        .eq(9)
        .text()
        .replace("Studio: ", "");
      $(this)
        .find("p")
        .children()
        .eq(10)
        .find("span > a")
        .each(function () {
          genre_name = $(this).text();
          genre_id = $(this)
            .attr("href")
            .replace(`${url.baseUrl}genres/`, "");
          genre_link = $(this).attr("href");
          genreList.push({ genre_name, genre_id, genre_link });
          object.genre_list = genreList;
        });
    });

    $("#venkonten > div.venser > div:nth-child(8) > ul > li").each(
      (i, element) => {
        const dataList = {
          title: $(element).find("span > a").text(),
          id: $(element)
            .find("span > a")
            .attr("href")
            .replace(url.baseUrl, ""),
          link: $(element).find("span > a").attr("href"),
          uploaded_on: $(element).find(".zeebr").text(),
        };
        episode_list.push(dataList);
      }
    );
    object.episode_list =
      episode_list.length === 0
        ? [
            {
              title: "Maaf, masih kosong bro",
              id: "Maaf, masih kosong bro",
              link: "Maaf, masih kosong bro",
              uploaded_on: "Maaf, masih kosong bro",
            },
          ]
        : episode_list;
    const batch_link = {
      id:
        $("div.venser > div:nth-child(6) > ul").text().length !== 0
          ? $("div.venser > div:nth-child(6) > ul > li > span:nth-child(1) > a")
              .attr("href")
              .replace(`${url.baseUrl}batch/`, "")
          : "Maaf, masih kosong bro",
      link:
        $("div.venser > div:nth-child(6) > ul").text().length !== 0
          ? $(
              "div.venser > div:nth-child(6) > ul > li > span:nth-child(1) > a"
            ).attr("href")
          : "Maaf, masih kosong bro",
    };
    const empty_link = {
      id: "Maaf, masih kosong bro",
      link: "Maaf, masih kosong bro",
    };
    object.batch_link = batch_link;
    //console.log(epsElement);
    res.json(object);
  } catch (err) {
    console.log(err);
    try {
      const fallbackDetail = await buildSearchBasedDetail(id);
      res.status(200).json({
        ...fallbackDetail,
        fallback: true,
        message: "Detail upstream blocked; using search fallback.",
      });
    } catch (fallbackErr) {
      console.log(fallbackErr);
      errors.requestFailed(req, res, err);
    }
  }
};
exports.batchAnime = async (req, res) => {
  const id = req.params.id;
  const fullUrl = `${baseUrl}batch/${id}`;
  console.log(fullUrl);
  Axios.get(fullUrl)
    .then((response) => {
      const $ = cheerio.load(response.data);
      const obj = {};
      obj.title = $(".batchlink > h4").text();
      obj.status = "success";
      obj.baseUrl = fullUrl;
      let low_quality = _batchQualityFunction(0, response.data);
      let medium_quality = _batchQualityFunction(1, response.data);
      let high_quality = _batchQualityFunction(2, response.data);
      obj.download_list = { low_quality, medium_quality, high_quality };
      res.send(obj);
    })
    .catch((err) => {
      errors.requestFailed(req, res, err);
    });
};
exports.epsAnime = async (req, res) => {
  const id = req.params.id;
  const fullUrl = `${url.baseUrl}${id}`;
  try {
    const response = await Axios.get(fullUrl);
    const $ = cheerio.load(response.data);
    const streamElement = $("#lightsVideo").find("#embed_holder");
    const obj = {};
    obj.title = $(".venutama > h1").text();
    obj.baseUrl = fullUrl;
    obj.id = fullUrl.replace(url.baseUrl, "");
    const streamLink = streamElement.find("iframe").attr("src");
    // const streamLinkResponse = await Axios.get(streamLink)
    // const stream$ = cheerio.load(streamLinkResponse.data)
    // const sl = stream$('body').find('script').html().search('sources')
    // const endIndex = stream$('body').find('script').eq(0).html().indexOf('}]',sl)
    // const val = stream$('body').find('script').eq(0).html().substr(sl,endIndex - sl+1).replace(`sources: [{'file':'`,'')
    // console.log(val);
    // console.log(val.replace(`','type':'video/mp4'}`,''));
    obj.link_stream = await episodeHelper.get(streamLink);
    console.log($('#pembed > div > iframe').attr('src'));
    let low_quality;
    let medium_quality;
    let high_quality;
    let mirror1 = [];
    let mirror2 = [];
    let mirror3 = [];

    $('#embed_holder > div.mirrorstream > ul.m360p > li').each((idx,el)=>{``
      mirror1.push({
        host:$(el).find('a').text().trim(),
        id:$(el).find('a').attr('href'),
      });
    });
    $('#embed_holder > div.mirrorstream > ul.m480p > li').each((idx,el)=>{
      mirror2.push({
        host:$(el).find('a').text().trim(),
        id:$(el).find('a').attr('href'),
      });
    });
    $('#embed_holder > div.mirrorstream > ul.m720p > li').each((idx,el)=>{
      mirror3.push({
        host:$(el).find('a').text().trim(),
        id:$(el).find('a').attr('href'),
      });
    });
    obj.mirror1 = {quality:'360p',mirrorList:mirror1}
    obj.mirror2 = {quality:'480p',mirrorList:mirror2}
    obj.mirror3 = {quality:'720p',mirrorList:mirror3}
    if($('#venkonten > div.venser > div.venutama > div.download > ul > li:nth-child(1)').text() === ''){
      console.log('ul is empty');
      low_quality = _notFoundQualityHandler(response.data,0)
      medium_quality = _notFoundQualityHandler(response.data,1)
      high_quality = _notFoundQualityHandler(response.data,2)
    }else{
      console.log('ul is not empty');
      low_quality = _epsQualityFunction(0, response.data);
      medium_quality = _epsQualityFunction(1, response.data);
      high_quality = _epsQualityFunction(2, response.data);
    }
    obj.quality = { low_quality, medium_quality, high_quality };
    res.send(obj);
  } catch (err) {
    console.log(err);
    errors.requestFailed(req, res, err);
  }
};

exports.epsMirror = async (req, res) => {
  const mirrorId = req.body.mirrorId;
  const animeId = req.params.animeId;
  const fullUrl = `${baseUrl}${animeId}/${mirrorId}`;
  try {
    const response = await Axios.get(fullUrl);
    const $ = cheerio.load(response.data);
    const obj = {};
    obj.title = $(".venutama > h1").text();
    obj.baseUrl = fullUrl;
    obj.id = fullUrl.replace(url.baseUrl, "");
    const streamLink = $('#pembed > div > iframe').attr('src')
    obj.streamLink = streamLink
    obj.link_stream = await episodeHelper.get(streamLink);
    res.send(obj);
  } catch (err) {
    console.log(err);
    errors.requestFailed(req, res, err);
  }
}

function _batchQualityFunction(num, res) {
  const $ = cheerio.load(res);
  const element = $(".download").find(".batchlink");
  const download_links = [];
  let response;
  element.find("ul").filter(function () {
    const quality = $(this).find("li").eq(num).find("strong").text();
    const size = $(this).find("li").eq(num).find("i").text();
    $(this)
      .find("li")
      .eq(num)
      .find("a")
      .each(function () {
        const _list = {
          host: $(this).text(),
          link: $(this).attr("href"),
        };
        download_links.push(_list);
        response = { quality, size, download_links };
      });
  });
  return response;
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

async function buildSearchBasedDetail(id) {
  const normalizedId = normalizeAnimeId(id);
  const query = animeIdToSearchQuery(normalizedId);
  const fullUrl = `${baseUrl}?s=${encodeURIComponent(query)}&post_type=anime`;
  const response = await Axios.get(fullUrl);
  const $ = cheerio.load(response.data);
  let matchedItem = null;

  $(".page").find("ul > li").each(function () {
    if (matchedItem) return;

    const link = $(this).find("h2 > a").attr("href") || "";
    const itemId = normalizeAnimeId(link);
    if (itemId !== normalizedId) return;

    const genre_list = [];
    $(this).find(".set").find("a").each(function () {
      const genre_link = $(this).attr("href") || "";
      const genre_name = $(this).text();
      genre_list.push({
        genre_name,
        genre_id: genre_link.replace(`${baseUrl}genres/`, ""),
        genre_link,
      });
    });

    matchedItem = {
      thumb: $(this).find("img").attr("src") || "",
      anime_id: normalizedId,
      synopsis: "",
      title: $(this).find("h2").text().replace("Subtitle Indonesia", "").trim(),
      japanase: "",
      score: parseFloat($(this).find(".set").eq(2).text().replace("Rating : ", "")) || null,
      producer: "",
      type: "",
      status: $(this).find(".set").eq(1).text().replace("Status : ", "").trim(),
      total_episode: null,
      duration: "",
      release_date: "",
      studio: "",
      genre_list,
      episode_list: [],
      batch_link: {
        id: "",
        link: "",
      },
    };
  });

  if (matchedItem) return matchedItem;
  throw new Error(`No fallback detail found for ${normalizedId}`);
}

function animeIdToSearchQuery(id) {
  const slug = normalizeAnimeId(id).replace(/\/$/, "");
  if (slug === "1piece-sub-indo") return "one piece";

  return slug
    .replace(/-sub-indo$/, "")
    .replace(/-/g, " ")
    .replace(/\bs(\d+)\b/g, "season $1")
    .trim();
}
function _epsQualityFunction(num, res) {
  const $ = cheerio.load(res);
  const element = $(".download");
  const download_links = [];
  let response;

  element.find("ul").filter(function () {
    const quality = $(this).find("li").eq(num).find("strong").text();
    const size = $(this).find("li").eq(num).find("i").text();
    $(this).find("li").eq(num).find("a").each(function () {
        const _list = {
          host: $(this).text(),
          link: $(this).attr("href"),
        };
        download_links.push(_list);
        response = { quality, size, download_links };
        
      });
  });
  return response;
}

function _notFoundQualityHandler(res,num){
  const $ = cheerio.load(res);
  const download_links = [];
  const element = $('.download')
  let response;

  element.filter(function(){
    if($(this).find('.anime-box > .anime-title').eq(0).text() === ''){
      $(this).find('.yondarkness-box').filter(function(){
        const quality = $(this).find('.yondarkness-title').eq(num).text().split('[')[1].split(']')[0];
        const size = $(this).find('.yondarkness-title').eq(num).text().split(']')[1].split('[')[1];
        $(this).find('.yondarkness-item').eq(num).find('a').each((idx,el) => {
          const _list = {
            host: $(el).text(),
            link: $(el).attr("href"),
          };
          download_links.push(_list);
          response = { quality, size, download_links };
        })
      })
    }else{
      $(this).find('.anime-box').filter(function(){
        const quality = $(this).find('.anime-title').eq(num).text().split('[')[1].split(']')[0];
        const size = $(this).find('.anime-title').eq(num).text().split(']')[1].split('[')[1];
        $(this).find('.anime-item').eq(num).find('a').each((idx,el) => {
          const _list = {
            host: $(el).text(),
            link: $(el).attr("href"),
          };
          download_links.push(_list);
          response = { quality, size, download_links };
        })
      })
    }
  })
  return response;

}
