package com.kraat.lostfilmnewtv.data.db

import com.kraat.lostfilmnewtv.data.model.TorrentLink
import org.json.JSONArray
import org.json.JSONObject

object TorrentLinkListConverters {
    fun fromTorrentLinks(torrentLinks: List<TorrentLink>): String? {
        if (torrentLinks.isEmpty()) {
            return null
        }

        return JSONArray().apply {
            torrentLinks.forEach { link ->
                put(
                    JSONObject()
                        .put("label", link.label)
                        .put("url", link.url),
                )
            }
        }.toString()
    }

    fun toTorrentLinks(value: String?): List<TorrentLink> {
        if (value.isNullOrBlank()) {
            return emptyList()
        }

        val jsonArray = JSONArray(value)
        return List(jsonArray.length()) { index ->
            val item = jsonArray.getJSONObject(index)
            TorrentLink(
                label = item.getString("label"),
                url = item.getString("url"),
            )
        }
    }
}
