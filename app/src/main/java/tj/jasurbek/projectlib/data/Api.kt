package tj.jasurbek.projectlib.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** An HTTP answer that isn't a success (status 503 = server unreachable, also used for network errors). */
class HttpFailure(val status: Int, message: String) : Exception(message)

@Serializable
data class Me(val id: String, val name: String, val email: String = "", val avatar: String? = null, val role: Role = Role.Member, val createdAt: Long = 0)

@Serializable
data class AuthStatus(
    val authRequired: Boolean = false,
    val authenticated: Boolean = false,
    /** "open" | "session" | "token" | "none" */
    val via: String = "none",
    /** No users yet: the next passkey (created on the web) makes the first admin. */
    val bootstrap: Boolean = false,
    val user: Me? = null,
)

@Serializable data class Person(val id: String, val name: String, val avatar: String? = null)
@Serializable data class Passkey(val id: String, val name: String, val synced: Boolean = false, val createdAt: Long = 0, val lastUsedAt: Long? = null)
@Serializable data class DeviceSession(val id: String, val userAgent: String = "", val createdAt: Long = 0, val lastSeenAt: Long = 0, val current: Boolean = false)
@Serializable data class AdminUser(
    val id: String, val name: String, val email: String = "", val avatar: String? = null, val role: Role = Role.Member,
    val createdAt: Long = 0, val disabled: Boolean = false, val lastSeenAt: Long? = null, val passkeys: Int = 0, val projects: Int = 0,
)
@Serializable data class Invite(val id: String, val role: Role, val name: String = "", val createdAt: Long = 0, val expiresAt: Long = 0, val createdBy: String = "")
@Serializable data class NewInvite(val id: String, val token: String, val expiresAt: Long = 0)
@Serializable data class DeviceCode(val code: String, val expiresAt: Long)
@Serializable data class Paired(val session: String, val user: Me)

/**
 * Client for the ProjectLib server (same endpoints as the web app).
 * Every request to the server is signed by one interceptor: the pairing session as the `pl_session` cookie
 * (acts as that user) or else the server token as `Authorization: Bearer` (acts as the first admin).
 * That covers sync, the SSE stream and attachment images loaded by Coil alike.
 */
class Api(private val settings: Settings) {
    private val auth = Interceptor { chain ->
        val req = chain.request()
        val base = baseUrl()
        if (base == null || req.url.host != base.host || req.url.port != base.port || req.header("X-No-Auth") != null) {
            return@Interceptor chain.proceed(req.newBuilder().removeHeader("X-No-Auth").header("User-Agent", USER_AGENT).build())
        }
        val b = req.newBuilder().header("User-Agent", USER_AGENT)
        val s = settings.current
        if (s.session.isNotEmpty()) b.header("Cookie", "pl_session=${s.session}")
        else if (s.token.isNotEmpty()) b.header("Authorization", "Bearer ${s.token}")
        chain.proceed(b.build())
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(auth)
        .build()

    /** Same pool, no read timeout — for the server-sent events stream. */
    val streamClient: OkHttpClient = client.newBuilder().readTimeout(0, TimeUnit.SECONDS).build()

    val configured get() = baseUrl() != null
    val signedIn get() = settings.current.let { it.session.isNotEmpty() || it.token.isNotEmpty() }

    fun baseUrl(): HttpUrl? = settings.current.serverUrl.takeIf { it.isNotBlank() }?.toHttpUrlOrNull()

    fun url(path: String, query: Map<String, String> = emptyMap()): HttpUrl {
        val base = baseUrl() ?: throw HttpFailure(0, "Сервер не указан")
        return base.newBuilder().addPathSegments(path.trimStart('/')).apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
    }

    private suspend fun call(request: Request): String = withContext(Dispatchers.IO) {
        val response: Response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw HttpFailure(503, "Сервер недоступен")
        }
        response.use { res ->
            val text = res.body.string()
            if (!res.isSuccessful) {
                val error = runCatching { AppJson.parseToJsonElement(text) as JsonObject }.getOrNull()?.get("error")?.jsonPrimitive?.contentOrNull
                // A 5xx that isn't our server's JSON comes from a proxy in front of it → server unreachable.
                if (res.code >= 500 && error == null) throw HttpFailure(503, "Сервер недоступен")
                throw HttpFailure(res.code, error ?: "HTTP ${res.code}")
            }
            text
        }
    }

    /** JSON request to the configured server; [body] null = no body. */
    private suspend fun <T> json(method: String, path: String, body: JsonElement?, out: KSerializer<T>): T {
        val rb = body?.let { AppJson.encodeToString(JsonElement.serializer(), it).toRequestBody(JSON) }
            ?: if (method == "GET" || method == "DELETE") null else "{}".toRequestBody(JSON)
        val text = call(Request.Builder().url(url(path)).method(method, rb).build())
        return runCatching { AppJson.decodeFromString(out, text) }.getOrElse { throw HttpFailure(502, "Неожиданный ответ сервера") }
    }
    private suspend fun send(method: String, path: String, body: JsonElement? = null) { json(method, path, body, JsonElement.serializer()) }
    private fun obj(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(pairs.associate { (k, v) ->
        k to when (v) {
            null -> kotlinx.serialization.json.JsonNull
            is String -> JsonPrimitive(v)
            is Boolean -> JsonPrimitive(v)
            is Number -> JsonPrimitive(v)
            is JsonElement -> v
            else -> error("unsupported")
        }
    })

    /* ---------- sync & files ---------- */

    suspend fun sync(ops: List<Op>, since: Long): SyncResponse {
        val body = AppJson.encodeToString(SyncRequest.serializer(), SyncRequest(since, ops)).toRequestBody(JSON)
        val text = call(Request.Builder().url(url("api/sync")).post(body).build())
        return runCatching { AppJson.decodeFromString(SyncResponse.serializer(), text) }
            .getOrElse { throw HttpFailure(502, "Неожиданный ответ сервера") }
    }

    /** POST /api/uploads — the server stores the image by content hash and returns its Attachment. */
    suspend fun upload(bytes: ByteArray, type: String, name: String): Attachment {
        val req = Request.Builder().url(url("api/uploads", mapOf("name" to name))).post(bytes.toRequestBody(type.toMediaType())).build()
        val text = try { call(req) } catch (e: HttpFailure) {
            throw when {
                e.status == 401 -> HttpFailure(401, "Нужно войти, чтобы загружать изображения")
                e.status >= 500 -> HttpFailure(e.status, "Сервер недоступен — вложения загружаются только при связи")
                else -> e
            }
        }
        return AppJson.decodeFromString(Attachment.serializer(), text)
    }

    /** Image URL of an upload; the interceptor adds the credentials. */
    fun attachmentUrl(id: String): String? = if (configured) url("api/uploads/$id").toString() else null

    /* ---------- sign-in ---------- */

    /** Checks an address + token before saving them: GET /api/auth/status without the stored credentials. */
    suspend fun checkServer(serverUrl: String, token: String): AuthStatus {
        val base = Settings.normalizeServerUrl(serverUrl).toHttpUrlOrNull() ?: throw HttpFailure(0, "Некорректный адрес сервера")
        val b = Request.Builder().url(base.newBuilder().addPathSegments("api/auth/status").build()).header("X-No-Auth", "1")
        if (token.isNotBlank()) b.header("Authorization", "Bearer ${token.trim()}")
        val text = call(b.build())
        return runCatching { AppJson.decodeFromString(AuthStatus.serializer(), text) }
            .getOrElse { throw HttpFailure(502, "По этому адресу не сервер ProjectLib") }
    }

    /** Trades a one-time code from the web profile for a session of that user. */
    suspend fun redeemCode(serverUrl: String, code: String): Paired {
        val base = Settings.normalizeServerUrl(serverUrl).toHttpUrlOrNull() ?: throw HttpFailure(0, "Некорректный адрес сервера")
        val body = AppJson.encodeToString(JsonObject.serializer(), obj("code" to code)).toRequestBody(JSON)
        val text = call(Request.Builder().url(base.newBuilder().addPathSegments("api/auth/device-codes/redeem").build()).header("X-No-Auth", "1").post(body).build())
        return runCatching { AppJson.decodeFromString(Paired.serializer(), text) }
            .getOrElse { throw HttpFailure(502, "По этому адресу не сервер ProjectLib") }
    }

    suspend fun status(): AuthStatus = json("GET", "api/auth/status", null, AuthStatus.serializer())
    suspend fun logout() = send("POST", "api/auth/logout")

    /* ---------- me ---------- */

    suspend fun updateMe(name: String? = null, email: String? = null, avatar: String? = null, clearAvatar: Boolean = false): Me {
        val fields = buildList<Pair<String, Any?>> {
            name?.let { add("name" to it) }
            email?.let { add("email" to it) }
            if (clearAvatar) add("avatar" to null) else avatar?.let { add("avatar" to it) }
        }
        return json("PATCH", "api/me", obj(*fields.toTypedArray()), Me.serializer())
    }
    suspend fun passkeys() = json("GET", "api/me/passkeys", null, ListSerializer(Passkey.serializer()))
    suspend fun deletePasskey(id: String) = send("DELETE", "api/me/passkeys/$id")
    suspend fun sessions() = json("GET", "api/me/sessions", null, ListSerializer(DeviceSession.serializer()))
    suspend fun endSession(id: String) = send("DELETE", "api/me/sessions/$id")
    suspend fun endOtherSessions() = send("POST", "api/me/sessions/revoke-others")
    suspend fun createDeviceCode() = json("POST", "api/me/device-codes", null, DeviceCode.serializer())

    /* ---------- people ---------- */

    suspend fun directory() = json("GET", "api/users/directory", null, ListSerializer(Person.serializer()))
    suspend fun users() = json("GET", "api/users", null, ListSerializer(AdminUser.serializer()))
    suspend fun setRole(id: String, role: Role) = send("PATCH", "api/users/$id", obj("role" to AppJson.encodeToJsonElement(Role.serializer(), role)))
    suspend fun setDisabled(id: String, disabled: Boolean) = send("PATCH", "api/users/$id", obj("disabled" to disabled))
    suspend fun deleteUser(id: String) = send("DELETE", "api/users/$id")
    suspend fun invites() = json("GET", "api/invites", null, ListSerializer(Invite.serializer()))
    suspend fun createInvite(role: Role, name: String) =
        json("POST", "api/invites", obj("role" to AppJson.encodeToJsonElement(Role.serializer(), role), "name" to name), NewInvite.serializer())
    suspend fun revokeInvite(id: String) = send("DELETE", "api/invites/$id")
    /** Invite links open in the web app: the person creates a passkey there, then pairs this app. */
    fun inviteUrl(token: String) = "${baseUrl().toString().trimEnd('/')}/#/invite/$token"

    /** Share a project: access = null closes it. */
    suspend fun setShare(projectId: String, userId: String, access: ShareAccess?) = send(
        "PUT", "api/projects/$projectId/shares",
        obj("userId" to userId, "access" to access?.let { AppJson.encodeToJsonElement(ShareAccess.serializer(), it) }),
    )

    companion object {
        val JSON = "application/json".toMediaType()
        /** Shows up in the web profile's device list as "Android". */
        const val USER_AGENT = "ProjectLib-App (Android)"
    }
}

