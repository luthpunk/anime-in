const url = require("../helpers/base-url");
const Axios = require("../helpers/axios-instance");
const cheerio = require("cheerio");
const errors = require("../helpers/errors");
const episodeHelper = require("../helpers/episodeHelper");
const { getDomain } = require("../helpers/domain-config");
const e = require("express");

exports.detailAnime = async (req, res) => {
  const baseUrl = getDomain();
  const id = normalizeAnimeId(req.params.id || req.params[0]);
  const fullUrl = baseUrl + `anime/${id}`;
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
    res.setHeader('Cache-Control', 's-maxage=43200, stale-while-revalidate=3600');
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
  const baseUrl = getDomain();
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
async function getIframeFromDataContent(dataContent) {
  try {
    const headers = {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
      'Referer': 'https://otakudesu.blog/',
      'Content-Type': 'application/x-www-form-urlencoded'
    };
    
    // 1. Get nonce
    const params1 = new URLSearchParams();
    params1.append('action', 'aa1208d27f29ca340c92c66d1926f13f');
    const nonceRes = await Axios.post('https://otakudesu.blog/wp-admin/admin-ajax.php', params1.toString(), { headers });
    const nonce = nonceRes.data?.data;
    if (!nonce) return null;
    
    // 2. Decode content
    const decoded = JSON.parse(Buffer.from(dataContent, 'base64').toString('utf-8'));
    
    // 3. Post to get embed
    const params2 = new URLSearchParams();
    params2.append('action', '2a3505c93b0035d3f455df82bf976b84');
    params2.append('id', decoded.id);
    params2.append('i', decoded.i);
    params2.append('q', decoded.q);
    params2.append('nonce', nonce);
    
    const embedRes = await Axios.post('https://otakudesu.blog/wp-admin/admin-ajax.php', params2.toString(), { headers });
    
    if (embedRes.data && embedRes.data.data) {
      const embedHtml = Buffer.from(embedRes.data.data, 'base64').toString('utf-8');
      const $ = cheerio.load(embedHtml);
      return $("iframe").attr("src");
    }
  } catch (e) {
    console.error("Failed to get iframe from data-content:", e.message);
  }
  return null;
}

exports.epsAnime = async (req, res) => {
  const baseUrl = getDomain();
  const id = normalizeEpisodeId(req.params.id || req.params[0]);
  const fullUrl = `${baseUrl}${id}`;
  try {
    const response = await Axios.get(fullUrl);
    const $ = cheerio.load(response.data);
    const obj = {};
    obj.title = $(".venutama > h1").text();
    obj.baseUrl = fullUrl;
    obj.id = fullUrl.replace(baseUrl, "");
    
    let low_quality;
    let medium_quality;
    let high_quality;
    let mirror1 = [];
    let mirror2 = [];
    let mirror3 = [];

    $('#embed_holder > div.mirrorstream > ul.m360p > li').each((idx,el)=>{
      const aTag = $(el).find('a');
      mirror1.push({
        host: aTag.text().trim(),
        id: aTag.attr('data-content') || aTag.attr('href'),
      });
    });
    $('#embed_holder > div.mirrorstream > ul.m480p > li').each((idx,el)=>{
      const aTag = $(el).find('a');
      mirror2.push({
        host: aTag.text().trim(),
        id: aTag.attr('data-content') || aTag.attr('href'),
      });
    });
    $('#embed_holder > div.mirrorstream > ul.m720p > li').each((idx,el)=>{
      const aTag = $(el).find('a');
      mirror3.push({
        host: aTag.text().trim(),
        id: aTag.attr('data-content') || aTag.attr('href'),
      });
    });
    obj.mirror1 = {quality:'360p',mirrorList:mirror1}
    obj.mirror2 = {quality:'480p',mirrorList:mirror2}
    obj.mirror3 = {quality:'720p',mirrorList:mirror3}

    // Resolve default stream link
    let defaultStreamLink = "-";
    const defaultMirrorA = $('.mirrorstream a[data-default="true"]').first();
    const defaultMirrorContent = defaultMirrorA.attr('data-content') 
      || $('.mirrorstream a[data-content]').first().attr('data-content');

    if (defaultMirrorContent) {
      const iframeUrl = await getIframeFromDataContent(defaultMirrorContent);
      if (iframeUrl) {
        defaultStreamLink = await episodeHelper.get(iframeUrl);
      }
    }
    obj.link_stream = defaultStreamLink;

    if($('#venkonten > div.venser > div.venutama > div.download > ul > li:nth-child(1)').text() === ''){
      low_quality = _notFoundQualityHandler(response.data,0)
      medium_quality = _notFoundQualityHandler(response.data,1)
      high_quality = _notFoundQualityHandler(response.data,2)
    }else{
      low_quality = _epsQualityFunction(0, response.data);
      medium_quality = _epsQualityFunction(1, response.data);
      high_quality = _epsQualityFunction(2, response.data);
    }
    obj.quality = { low_quality, medium_quality, high_quality };
    res.setHeader('Cache-Control', 's-maxage=7200, stale-while-revalidate=600');
    res.send(obj);
  } catch (err) {
    console.log(err);
    res.status(200).json({
      title: episodeIdToTitle(id),
      baseUrl: fullUrl,
      id: fullUrl,
      link_stream: "-",
      mirror1: { quality: "360p", mirrorList: [] },
      mirror2: { quality: "480p", mirrorList: [] },
      mirror3: { quality: "720p", mirrorList: [] },
      quality: { low_quality: null, medium_quality: null, high_quality: null },
      fallback: true,
      message: "Episode upstream blocked; use WebView fallback.",
    });
  }
};

exports.epsMirror = async (req, res) => {
  const baseUrl = getDomain();
  const mirrorId = req.body.mirrorId;
  const animeId = req.params.animeId;
  try {
    const obj = {};
    obj.title = "Mirror Stream";
    obj.baseUrl = `${baseUrl}${animeId}/${mirrorId}`;
    obj.id = `${animeId}/${mirrorId}`;
    
    let iframeUrl = null;
    if (mirrorId && mirrorId.startsWith("eyJ")) {
      iframeUrl = await getIframeFromDataContent(mirrorId);
    } else {
      const fullUrl = `${baseUrl}${animeId}/${mirrorId}`;
      const response = await Axios.get(fullUrl);
      const $ = cheerio.load(response.data);
      iframeUrl = $('#pembed > div > iframe').attr('src');
    }
    
    obj.streamLink = iframeUrl;
    obj.link_stream = iframeUrl ? await episodeHelper.get(iframeUrl) : "-";
    res.send(obj);
  } catch (err) {
    console.log(err);
    errors.requestFailed(req, res, err);
  }
};

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

function normalizeEpisodeId(rawId) {
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
    .replace(/^https?:\/\/otakudesu\.(blog|cloud)\//, "")
    .replace(/^\/+/, "")
    .replace(/\/+$/, "");

  return id ? `${id}/` : "";
}

function episodeIdToTitle(id) {
  return normalizeEpisodeId(id)
    .replace(/^episode\//, "")
    .replace(/\/$/, "")
    .replace(/-/g, " ")
    .replace(/\b\w/g, (char) => char.toUpperCase());
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
