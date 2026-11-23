package com.kraat.lostfilmnewtv.data.network

import com.kraat.lostfilmnewtv.data.model.LostFilmCookie
import com.kraat.lostfilmnewtv.data.model.LostFilmSession
import com.kraat.lostfilmnewtv.data.parser.LostFilmBaseUrl
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Прямой вход в LostFilm без auth-bridge: то же самое, что делала страница логина на сервере
 * (POST /ajaxik.users.php), только из приложения. Cookie копятся в собственном CookieJar
 * между шагами «логин -> капча -> повторный логин», затем превращаются в [LostFilmSession].
 *
 * Состояние (cookie, флаг капчи) живёт в экземпляре, поэтому вызывайте [reset] при открытии формы.
 */
class LostFilmLoginClient(
    okHttpClient: OkHttpClient,
    private val baseUrlProvider: () -> String = { LostFilmBaseUrl.get() },
) {
    sealed interface LoginResult {
        data class Success(val session: LostFilmSession) : LoginResult
        data object NeedCaptcha : LoginResult
        data object WrongCaptcha : LoginResult
        data object InvalidCredentials : LoginResult
    }

    private val jar = InMemoryCookieJar()
    private val client: OkHttpClient = okHttpClient.newBuilder()
        .cookieJar(jar)
        .followRedirects(false)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var captchaRequired = false

    fun reset() {
        jar.clear()
        captchaRequired = false
    }

    /** Получаем стартовые cookie сайта (как это делал браузер на телефоне до ввода пароля). */
    suspend fun warmUp() = withContext(Dispatchers.IO) {
        val request = baseRequest("${base()}/").get().build()
        client.newCall(request).execute().use { /* нужны только Set-Cookie */ }
    }

    suspend fun fetchCaptcha(): ByteArray = withContext(Dispatchers.IO) {
        val request = baseRequest("${base()}/simple_captcha.php?${System.currentTimeMillis()}")
            .header("Accept", "image/*,*/*;q=0.8")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Captcha HTTP ${response.code}")
            response.body?.bytes() ?: throw IOException("Empty captcha body")
        }
    }

    suspend fun login(email: String, password: String, captcha: String?): LoginResult =
        withContext(Dispatchers.IO) {
            val form = FormBody.Builder()
                .add("act", "users")
                .add("type", "login")
                // Сайт ожидает значения, прогнанные через encodeURIComponent ещё до form-encoding
                // (страница логина делала именно так), поэтому кодируем дважды.
                .add("mail", encodeComponent(email))
                .add("pass", encodeComponent(password))
                .add("need_captcha", encodeComponent(if (captchaRequired) "1" else ""))
                .add("captcha", encodeComponent(captcha.orEmpty()))
                .add("rem", "1")
                .build()

            val request = baseRequest("${base()}/ajaxik.users.php")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .post(form)
                .build()

            val text = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Login HTTP ${response.code}")
                response.body?.string() ?: throw IOException("Empty login body")
            }

            val payload: JsonObject = runCatching { json.parseToJsonElement(text).jsonObject }
                .getOrElse { throw IOException("Unexpected login response") }

            val ok = payload["result"].asString() == "ok"
            val success = payload["success"].asBoolean()
            val hasSessionCookie = jar.all().any { it.name.equals("lf_session", ignoreCase = true) }

            when {
                (ok && success) || hasSessionCookie -> {
                    captchaRequired = false
                    LoginResult.Success(buildSession())
                }
                ok && payload["need_captcha"].asBoolean() -> {
                    captchaRequired = true
                    LoginResult.NeedCaptcha
                }
                payload["error"].asInt() == WRONG_CAPTCHA_ERROR -> {
                    captchaRequired = true
                    LoginResult.WrongCaptcha
                }
                else -> LoginResult.InvalidCredentials
            }
        }

    private fun buildSession(): LostFilmSession {
        val host = base().toHttpUrl()
        val cookies = jar.loadForRequest(host).map {
            LostFilmCookie(name = it.name, value = it.value, domain = it.domain, path = it.path)
        }
        return LostFilmSession(cookies = cookies)
    }

    private fun base(): String = baseUrlProvider().trimEnd('/')

    private fun baseRequest(url: String) = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.5")

    private fun encodeComponent(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun kotlinx.serialization.json.JsonElement?.asString(): String? =
        (this as? JsonPrimitive)?.contentOrNull

    private fun kotlinx.serialization.json.JsonElement?.asBoolean(): Boolean {
        val primitive = this as? JsonPrimitive ?: return false
        return primitive.booleanOrNull ?: (primitive.contentOrNull in setOf("1", "true"))
    }

    private fun kotlinx.serialization.json.JsonElement?.asInt(): Int? =
        (this as? JsonPrimitive)?.intOrNull

    private class InMemoryCookieJar : CookieJar {
        private val store = mutableListOf<Cookie>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val now = System.currentTimeMillis()
            cookies.forEach { c ->
                store.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
                if (c.expiresAt > now) store += c
            }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            return store.filter { it.expiresAt > now && it.matches(url) }
        }

        @Synchronized
        fun all(): List<Cookie> = store.toList()

        @Synchronized
        fun clear() = store.clear()
    }

    private companion object {
        const val WRONG_CAPTCHA_ERROR = 4
        const val USER_AGENT =
            "Mozilla/5.0 (Android TV; LostFilmNewTV) AppleWebKit/537.36 Chrome/132.0.0.0 Safari/537.36"
    }
}
