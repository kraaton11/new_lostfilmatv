package com.kraat.lostfilmnewtv.data.auth

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Мини-сервер внутри приложения на ТВ: телефон открывает форму по локальному адресу из QR,
 * вводит логин/пароль, а сам вход в LostFilm выполняет приложение на телевизоре.
 * Никакого внешнего сервера не нужно, но телефон и ТВ должны быть в одной сети.
 *
 * Защита: одноразовый случайный токен в ссылке, только запросы из локальной сети,
 * лимит попыток, срок жизни [ttlMillis], остановка после успешного входа.
 */
class PhoneLoginServer(
    private val login: suspend (email: String, password: String, captcha: String?) -> PasswordLoginResult,
    private val captcha: suspend () -> ByteArray,
    private val ttlMillis: Long = LOGIN_TTL_MS,
    private val lanAddress: () -> String? = ::findLanIpv4,
) {
    private var server: Server? = null
    @Volatile
    private var token: String = ""
    @Volatile
    private var expiresAt: Long = 0L
    private val attempts = AtomicInteger(0)
    private var stopTask: TimerTask? = null
    private val timer = Timer("phone-login-timer", true)

    /** Запускает сервер и возвращает ссылку для QR. Бросает [IOException], если нет локальной сети. */
    @Synchronized
    fun start(): String {
        stop()
        val ip = lanAddress() ?: throw IOException("No LAN address")
        token = randomToken()
        expiresAt = System.currentTimeMillis() + ttlMillis
        attempts.set(0)

        var lastError: IOException? = null
        for (port in PORTS) {
            val candidate = Server(ip, port)
            try {
                candidate.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
            } catch (e: IOException) {
                candidate.stop()
                lastError = e
                continue
            }
            server = candidate
            stopTask = stopAfter(candidate, ttlMillis)
            return "http://$ip:$port/?t=$token"
        }
        throw lastError ?: IOException("No free port")
    }

    @Synchronized
    fun stop() {
        stopTask?.cancel()
        stopTask = null
        server?.close()
        server = null
        token = ""
    }

    private fun stopAfter(expected: Server, delayMillis: Long): TimerTask =
        object : TimerTask() {
            override fun run() {
                synchronized(this@PhoneLoginServer) {
                    if (server === expected) stop()
                }
            }
        }.also { timer.schedule(it, delayMillis) }

    private fun tokenMatches(candidate: String): Boolean {
        val expected = token
        if (expected.isEmpty()) return false
        return MessageDigest.isEqual(expected.toByteArray(), candidate.toByteArray())
    }

    private inner class Server(ip: String, port: Int) : NanoHTTPD(ip, port) {
        private val requestJob = Job()
        private val requestMutex = Mutex()
        private var authenticated = false

        fun close() {
            requestJob.cancel()
            stop()
        }

        override fun serve(session: IHTTPSession): Response {
            if (!isLocalClient(session.remoteIpAddress)) {
                return plain(Status.FORBIDDEN, "Forbidden")
            }
            if (System.currentTimeMillis() > expiresAt) {
                return plain(Status.GONE, "Ссылка устарела. Получите новый QR-код на телевизоре.")
            }

            if (session.method == Method.POST) {
                val length = session.headers["content-length"]?.toIntOrNull() ?: 0
                if (length > MAX_BODY_BYTES) return plain(Status.BAD_REQUEST, "Too large")
                try {
                    session.parseBody(HashMap())
                } catch (_: Exception) {
                    return plain(Status.BAD_REQUEST, "Bad request")
                }
            }

            val params = session.parameters
            if (!tokenMatches(params["t"]?.firstOrNull().orEmpty())) {
                return plain(Status.UNAUTHORIZED, "Unauthorized")
            }

            return when {
                session.method == Method.GET && session.uri == "/" -> noStore(
                    newFixedLengthResponse(Status.OK, "text/html; charset=utf-8", PAGE),
                )
                session.method == Method.GET && session.uri == "/captcha" -> serveCaptcha()
                session.method == Method.POST && session.uri == "/login" -> handleLogin(params)
                else -> plain(Status.NOT_FOUND, "Not found")
            }
        }

        private fun serveCaptcha(): Response = try {
            val bytes = runBlocking(requestJob) { requestMutex.withLock { captcha() } }
            val mime = when {
                bytes.size > 3 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "image/png"
                bytes.size > 3 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() -> "image/gif"
                else -> "image/jpeg"
            }
            noStore(newFixedLengthResponse(Status.OK, mime, ByteArrayInputStream(bytes), bytes.size.toLong()))
        } catch (_: Exception) {
            plain(Status.INTERNAL_ERROR, "Captcha unavailable")
        }

        private fun handleLogin(params: Map<String, List<String>>): Response {
            if (attempts.incrementAndGet() > MAX_ATTEMPTS) return json(Status.FORBIDDEN, "too_many")

            val mail = params["mail"]?.firstOrNull().orEmpty().trim()
            val pass = params["pass"]?.firstOrNull().orEmpty()
            val captchaText = params["captcha"]?.firstOrNull()?.trim()?.ifBlank { null }
            if (mail.isEmpty() || pass.isEmpty()) return json(Status.OK, "invalid")

            val result = try {
                runBlocking(requestJob) {
                    requestMutex.withLock {
                        if (authenticated) {
                            PasswordLoginResult.Authenticated
                        } else {
                            login(mail, pass, captchaText).also {
                                authenticated = it == PasswordLoginResult.Authenticated
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                PasswordLoginResult.NetworkError
            }
            return when (result) {
                PasswordLoginResult.Authenticated -> {
                    stopAfter(this, 3_000L)
                    json(Status.OK, "ok")
                }
                PasswordLoginResult.NeedCaptcha -> json(Status.OK, "need_captcha")
                PasswordLoginResult.WrongCaptcha -> json(Status.OK, "wrong_captcha")
                PasswordLoginResult.InvalidCredentials -> json(Status.OK, "invalid")
                PasswordLoginResult.VerificationFailed -> json(Status.OK, "verification_failed")
                PasswordLoginResult.NetworkError -> json(Status.OK, "network")
            }
        }
    }

    private fun json(status: Status, code: String): NanoHTTPD.Response =
        noStore(newFixedLengthResponse(status, "application/json; charset=utf-8", "{\"status\":\"$code\"}"))

    private fun plain(status: Status, text: String): NanoHTTPD.Response =
        noStore(newFixedLengthResponse(status, "text/plain; charset=utf-8", text))

    private fun noStore(response: NanoHTTPD.Response): NanoHTTPD.Response = response.apply {
        addHeader("Cache-Control", "no-store")
        addHeader("Referrer-Policy", "no-referrer")
    }

    private fun isLocalClient(remote: String?): Boolean {
        if (remote.isNullOrBlank()) return false
        return try {
            val address = InetAddress.getByName(remote)
            address.isSiteLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress
        } catch (_: Exception) {
            false
        }
    }

    private fun randomToken(): String {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val LOGIN_TTL_MS = 10 * 60 * 1000L
        private val PORTS = 8765..8775
        private const val MAX_ATTEMPTS = 15
        private const val MAX_BODY_BYTES = 4096

        private fun findLanIpv4(): String? = try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        } catch (_: Exception) {
            null
        }

        private const val PAGE = """<!DOCTYPE html>
<html lang="ru"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="referrer" content="no-referrer">
<title>Вход в LostFilm</title>
<style>
body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#0f141a;color:#fff;font-family:system-ui,-apple-system,"Segoe UI",sans-serif}
form{width:min(420px,calc(100vw - 32px));display:grid;gap:12px;padding:24px;border-radius:18px;background:#141b22;box-sizing:border-box}
h1{margin:0 0 4px;font-size:22px}
input,button{box-sizing:border-box;width:100%;min-height:52px;border-radius:12px;font:inherit}
input{border:1px solid #ffffff24;background:#ffffff10;color:#fff;padding:0 14px}
button{border:0;background:#2f78cf;color:#fff;font-weight:700}
button[disabled]{opacity:.6}
#cb[hidden]{display:none!important}
#cr{background:#ffffff18}
#ci{max-width:220px;background:#fff;border-radius:10px;min-height:50px}
#st{min-height:22px;font-size:14px}.err{color:#ff8f8f}.ok{color:#7ee787}
.note{margin:0;font-size:12px;color:#ffffffa0}
</style></head><body>
<form id="f" method="post" autocomplete="on">
<h1>Вход в LostFilm</h1>
<input type="email" name="mail" placeholder="E-mail" autocomplete="username" required>
<input type="password" name="pass" placeholder="Пароль" autocomplete="current-password" required>
<div id="cb" hidden style="display:grid;gap:10px">
<img id="ci" alt="Captcha">
<button type="button" id="cr">Обновить картинку</button>
<input type="text" name="captcha" placeholder="Код с картинки" autocomplete="off">
</div>
<button type="submit" id="go" disabled>Войти</button>
<div id="st"></div>
<p class="note">Пароль передаётся на телевизор по HTTP. Используйте доверенную домашнюю сеть.</p>
</form>
<script>
(function(){
var f=document.getElementById('f'),st=document.getElementById('st'),go=document.getElementById('go'),
cb=document.getElementById('cb'),ci=document.getElementById('ci');
var t=new URLSearchParams(location.search).get('t')||'';
function msg(s,ok){st.textContent=s;st.className=ok?'ok':'err';}
function captcha(){cb.hidden=false;ci.src='/captcha?t='+encodeURIComponent(t)+'&_='+Date.now();}
document.getElementById('cr').onclick=captcha;
f.addEventListener('submit',function(e){
e.preventDefault();msg('');go.disabled=true;
var b=new URLSearchParams();
b.append('t',t);b.append('mail',f.elements.mail.value.trim());
b.append('pass',f.elements.pass.value);b.append('captcha',f.elements.captcha.value.trim());
var done=false;
fetch('/login',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:b.toString()})
.then(function(r){return r.json();})
.then(function(p){
switch(p.status){
case 'ok':done=true;msg('Готово! Вернитесь к телевизору.',true);break;
case 'need_captcha':captcha();msg('Введите код с картинки');break;
case 'wrong_captcha':captcha();msg('Неверный код с картинки');break;
case 'invalid':if(!cb.hidden)captcha();msg('Неверный e-mail или пароль');break;
case 'network':msg('Нет связи с LostFilm. Попробуйте ещё раз.');break;
case 'too_many':done=true;msg('Слишком много попыток. Получите новый QR на ТВ.');break;
default:msg('Не удалось войти');}
})
.catch(function(){msg('Ошибка сети или ссылка устарела');})
.then(function(){go.disabled=done;});
});
go.disabled=false;
})();
</script></body></html>"""
    }
}
