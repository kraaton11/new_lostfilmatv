package com.kraat.lostfilmnewtv.platform.torrserve

class TorrServeLinkBuilder(private val config: TorrServeConfig) : TorrServeSourceBuilder {
    fun supportsSource(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return false
        
        val scheme = trimmed.substringBefore(":")
        return when (scheme.lowercase()) {
            "http", "https" -> {
                try {
                    val uri = java.net.URI(trimmed)
                    uri.host?.isNotBlank() == true
                } catch (e: Exception) { false }
            }
            "magnet" -> {
                trimmed.startsWith("magnet:", ignoreCase = true) && 
                trimmed.substringAfter("magnet:").let { query ->
                    query.contains("xt=", ignoreCase = true) ||
                    query.split("&").any { it.startsWith("xt=", ignoreCase = true) }
                }
            }
            else -> false
        }
    }

    override fun build(rawUrl: String): String? {
        if (!supportsSource(rawUrl)) return null
        return rawUrl.trim()
    }
}
