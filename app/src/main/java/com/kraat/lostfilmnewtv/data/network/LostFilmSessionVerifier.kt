package com.kraat.lostfilmnewtv.data.network

import com.kraat.lostfilmnewtv.data.model.LostFilmSession
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class LostFilmSessionVerifier(
    private val probeUrl: String = DEFAULT_PROBE_URL,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun verify(session: LostFilmSession): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(probeUrl)
            .header("User-Agent", USER_AGENT)
            .header("Cookie", session.toCookieString())
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for $probeUrl")
            }

            val body = response.body?.string() ?: throw IOException("Empty response body for $probeUrl")
            val normalizedBody = body.lowercase()
            val looksAnonymous = normalizedBody.contains(ANONYMOUS_LOGIN_MARKER)
            val hasProfileLink = normalizedBody.contains(PROFILE_LINK_MARKER)
            val hasUserPane = normalizedBody.contains(USER_PANE_MARKER)
            val hasAvatarMarker =
                normalizedBody.contains(AVATAR_MARKER) || normalizedBody.contains(DEFAULT_AVATAR_MARKER)
            val hasLogoutLink = normalizedBody.contains(LOGOUT_MARKER)

            return@withContext !looksAnonymous && (hasLogoutLink || (hasProfileLink && hasUserPane && hasAvatarMarker))
        }
    }

    private companion object {
        const val DEFAULT_PROBE_URL = "https://www.lostfilm.today/"
        const val USER_AGENT = "Mozilla/5.0 (Android TV; LostFilmNewTV) AppleWebKit/537.36 Chrome/132.0.0.0 Safari/537.36"
        const val ANONYMOUS_LOGIN_MARKER = "id=\"lf-login-form\""
        const val LOGOUT_MARKER = "href=\"/logout\""
        const val PROFILE_LINK_MARKER = "href=\"/my\""
        const val USER_PANE_MARKER = "class=\"user-pane\""
        const val AVATAR_MARKER = "/static/users/"
        const val DEFAULT_AVATAR_MARKER = "/vision/no-avatar-50.jpg"
    }
}
