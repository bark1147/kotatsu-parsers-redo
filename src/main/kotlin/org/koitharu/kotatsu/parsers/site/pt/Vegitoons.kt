package org.koitharu.kotatsu.parsers.site.pt

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.parseJson
import org.koitharu.kotatsu.parsers.util.parseSafe
import java.text.SimpleDateFormat
import java.util.EnumSet

@MangaSourceParser("VEGITOONS", "Vegitoons", "pt")
internal class Vegitoons(context: MangaLoaderContext) : PagedMangaParser(
	context,
	source = MangaParserSource.VEGITOONS,
	pageSize = 20,
) {

	override val configKeyDomain = ConfigKey.Domain("vegitoons.black")

	private val apiUrl = "https://api.vegitoons.black"

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
	)

	override val filterCapabilities = MangaListFilterCapabilities(
		isSearchSupported = true,
		isSearchWithFiltersSupported = true,
		isMultipleTagsSupported = true,
	)

	override suspend fun getFilterOptions(): MangaListFilterOptions {

	val tags = mutableSetOf<MangaTag>()

	for (pagina in 1..5) {

		val json = webClient
			.httpGet(
				"$apiUrl/obras/buscar?pagina=$pagina&limite=100"
			)
			.parseJson()

		val obras = json.optJSONArray("obras")
			?: break


		for (i in 0 until obras.length()) {

			val obra = obras.optJSONObject(i)
				?: continue

			val tagArray = obra.optJSONArray("tags")
				?: continue


			for (j in 0 until tagArray.length()) {

				val tag = tagArray.optJSONObject(j)
					?: continue

				val id = tag.optInt("tag_id")
				val nome = tag.optString("tag_nome")

				if (nome.isNotBlank()) {

					tags.add(
						MangaTag(
							key = id.toString(),
							title = nome,
							source = source,
						)
					)
				}
			}
		}


		if (obras.length() < 100) {
			break
		}
	}


	    return MangaListFilterOptions(
		availableTags = tags,

		availableStates = EnumSet.of(
			MangaState.ONGOING,
			MangaState.FINISHED,
		),

		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.HENTAI,
		),
	   )
        }

	private val chapterDateFormat =
		SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", sourceLocale)

	override suspend fun getListPage(
		page: Int,
		order: SortOrder,
		filter: MangaListFilter
	): List<Manga> {

		val url = if (!filter.query.isNullOrEmpty() || filter.types.isNotEmpty() || filter.tags.isNotEmpty()) {
			buildSearchUrl(page, filter)
		} else {
			"$apiUrl/obras/ranking".toHttpUrl().newBuilder()
				.addQueryParameter("tipo", "visualizacoes_geral")
				.addQueryParameter("limite", pageSize.toString())
				.addQueryParameter("pagina", page.toString())
				.build()
		}

		val response = webClient.httpGet(url).parseJson()

		val results = response.optJSONArray("obras")
			?: response.optJSONArray("data")
			?: return emptyList()

		return results.mapJSON {
			parseMangaFromJson(it)
		}
	}

	    private fun buildSearchUrl(
	            page: Int,
	            filter: MangaListFilter
            ): HttpUrl {

	        val builder = "$apiUrl/obras/buscar".toHttpUrl()
		     .newBuilder()
		     .addQueryParameter("pagina", page.toString())
		     .addQueryParameter("limite", pageSize.toString())

	       filter.query?.let {
		       builder.addQueryParameter("busca", it)
	       }

	       if (filter.tags.isNotEmpty()) {
		      builder.addQueryParameter(
		  	  "tag_ids",
		       	  filter.tags.joinToString(",") { tag -> tag.key }
		    )
	       }

	             when {
		         filter.types.contains(ContentType.HENTAI) -> {
			        builder.addQueryParameter("gen_id", "5")
		         }

		         filter.types.contains(ContentType.MANGA) -> {
			         builder.addQueryParameter("gen_id", "1,4,6,8")
		         }
	              }

	              return builder.build()
                }

        	private fun parseMangaFromJson(json: JSONObject): Manga {

		val id = json.optInt("obr_id")
		val name = json.optString("obr_nome")
		val slug = json.optString("obr_slug")

		val tags = mutableSetOf<MangaTag>()

		// Gênero
		json.optJSONObject("genero")?.let {
	            val id = it.optInt("gen_id")
	            val nome = it.optString("gen_nome")

	            if (nome.isNotBlank()) {
		        tags.add(
			     MangaTag(
				key = "gen_$id",
				title = nome,
				source = source,
			     )
		         )
	             }
                 }

		// Tags
		json.optJSONArray("tags")?.let { array ->

	             for (i in 0 until array.length()) {

		          val tagObj = array.optJSONObject(i)

		          val id = tagObj?.optInt("tag_id")
		          val nome = tagObj?.optString("tag_nome")

		          if (id != null && !nome.isNullOrBlank()) {
			      tags.add(
				   MangaTag(
					key = id.toString(),
					title = nome,
					source = source,
				   )
			      )
		         }
	             }
                 }

		// Formato
		json.optString("formato_nome")
			.takeIf { it.isNotBlank() }
			?.let {
				tags.add(
					MangaTag(
						key = it.lowercase().replace(" ", "_"),
						title = it,
						source = source,
					)
				)
			}


		return Manga(
			id = generateUid(id.toLong()),
			title = name,
			url = "/obra/$id/$slug",
			publicUrl = "https://$domain/obra/$id/$slug",
			coverUrl = json.optString("obr_imagem"),
			source = source,
			rating = RATING_UNKNOWN,
			altTitles = emptySet(),
			contentRating = null,
			tags = tags,
			state = when(json.optString("status_nome")) {
				"Em Andamento" -> MangaState.ONGOING
				"Concluído" -> MangaState.FINISHED
				else -> null
			},
			authors = emptySet(),
			largeCoverUrl = null,
			description = json.optString("obr_descricao"),
			chapters = null,
		)
	}


	override suspend fun getDetails(manga: Manga): Manga {

		val id = manga.url.substringAfter("/obra/")
			.substringBefore("/")

		val json = webClient
			.httpGet("$apiUrl/obras/$id")
			.parseJson()


		val chapters = json.optJSONArray("capitulos")
			?.mapJSON {
				parseChapter(it)
			}
			?.sortedBy {
				it.number
			}
			?: emptyList()


		return manga.copy(
			description = json.optString("obr_descricao"),
			chapters = chapters,
		)
	}


        	private fun parseChapter(json: JSONObject): MangaChapter {

		val id = json.getLong("cap_id")

		return MangaChapter(
			id = generateUid(id),
			title = json.optString("cap_nome"),
			number = json.optDouble("cap_numero").toFloat(),
			volume = 0,
			url = "/capitulo/$id",
			scanlator = null,
			uploadDate = chapterDateFormat.parseSafe(
				json.optString("cap_criado_em")
			),
			branch = null,
			source = source,
		)
	}


	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {

		val id = chapter.url.substringAfterLast("/")

		val json = webClient
			.httpGet("$apiUrl/capitulos/$id")
			.parseJson()


		val pages = json.getJSONArray("cap_paginas")


		return (0 until pages.length()).map {

			val url = pages.getString(it)

			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}