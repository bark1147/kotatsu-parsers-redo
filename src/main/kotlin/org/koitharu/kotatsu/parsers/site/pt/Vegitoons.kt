package org.koitharu.kotatsu.parsers.site.pt

import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.parseJson
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSONNotNull
import okhttp3.HttpUrl.Companion.toHttpUrl

@MangaSourceParser("VEGITOONS", "Vegitoons", "pt")
internal class Vegitoons(context: MangaLoaderContext) :
    PagedMangaParser(context, MangaParserSource.VEGITOONS, 12) {

    private val api = "https://api.vegitoons.black"


    override suspend fun getListPage(
        page: Int,
        order: SortOrder,
        filter: MangaListFilter
    ): List<Manga> {

        val query = filter.query ?: ""

        val url = "$api/obras/buscar".toHttpUrl()
            .newBuilder()
            .addQueryParameter("busca", query)
            .addQueryParameter("limite", "12")
            .addQueryParameter("pagina", page.toString())
            .build()

        val json = webClient.httpGet(url).parseJson()

        val obras = json.optJSONArray("obras") ?: JSONArray()

        return obras.mapJSONNotNull {
            parseManga(it)
        }
    }


    private fun parseManga(json: JSONObject): Manga? {

        val id = json.getStringOrNull("obr_id") ?: return null

        val chapters = json.optJSONArray("capitulos")
            ?.mapJSONNotNull {
                MangaChapter(
                    id = generateUid(it.getString("cap_id")),
                    title = it.getString("cap_nome"),
                    number = it.optFloat("cap_numero"),
                    url = "/capitulo/${it.getString("cap_id")}",
                    scanlator = null,
                    uploadDate = 0,
                    source = source
                )
            } ?: emptyList()


        return Manga(
            id = generateUid(id),
            title = json.getStringOrNull("obr_nome") ?: "",
            url = "/obra/$id",
            publicUrl = "https://vegitoons.black",
            rating = RATING_UNKNOWN,
            coverUrl = json.getStringOrNull("obr_imagem"),
            description = json.getStringOrNull("obr_descricao"),
            tags = emptySet(),
            state = MangaState.ONGOING,
            contentRating = ContentRating.SAFE,
            authors = emptySet(),
            chapters = chapters,
            source = source
        )
    }


    override suspend fun getDetails(manga: Manga): Manga {
        return manga
    }


    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {

        val id = chapter.url.substringAfterLast("/")

        val json = webClient.httpGet(
            "$api/capitulos/$id".toHttpUrl()
        ).parseJson()


        val paginas = json.optJSONArray("cap_paginas")
            ?: return emptyList()


        return paginas.mapJSONNotNull {
            MangaPage(
                id = it.toString(),
                url = it.toString(),
                preview = null,
                source = source
            )
        }
    }
}