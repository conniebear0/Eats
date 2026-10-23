package com.eats.app.support

import com.eats.app.support.bot.AiHelper
import com.eats.app.support.bot.AiPrompt
import com.eats.app.support.bot.AiResult
import com.eats.app.support.bot.AiSettings
import com.eats.app.support.bot.BotContext
import com.eats.app.support.bot.Complaint
import com.eats.app.support.bot.TranscriptLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/**
 * Calls an OpenAI-compatible chat API (Groq by default) for the chatbot's AI helper.
 * No extra libraries: a plain HttpURLConnection on a background thread.
 * [interpret] throws on a failure (no internet, wrong key, rate limit, no reply within 12 seconds) so the
 * app can note it quietly; the chat then answers with its built-in rules, and customers see nothing of it.
 */
class GroqAiHelper(private val settings: () -> AiSettings) : AiHelper {

    override suspend fun interpret(message: String, ctx: BotContext, recent: List<TranscriptLine>): AiResult? {
        val s = settings()
        if (!s.usable) return null
        return withContext(Dispatchers.IO) {
            val body = AiPrompt.requestBody(s, AiPrompt.system(ctx), AiPrompt.user(message, recent))
            AiPrompt.parse(AiPrompt.content(post(s, body)))
        }
    }

    /** A short case summary for the agent; null on any failure (the complaint is already logged). */
    override suspend fun brief(c: Complaint): String? {
        val s = settings()
        if (!s.usable) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val body = AiPrompt.requestBody(s, AiPrompt.briefSystem(), AiPrompt.briefUser(c))
                AiPrompt.parseBrief(AiPrompt.content(post(s, body)))
            }.getOrNull()
        }
    }

    /** Used by "Test connection" in the AI settings. Returns a short message for the user. */
    suspend fun test(s: AiSettings): String = withContext(Dispatchers.IO) {
        if (s.apiKey.isBlank()) return@withContext "Add your API key first."
        runCatching {
            val body = AiPrompt.requestBody(
                s,
                "Reply ONLY with this JSON: {\"intent\":\"none\",\"answer\":\"Connected\"}",
                "Test",
            )
            val reply = AiPrompt.content(post(s, body))
            if (reply.isNullOrBlank()) "Connected, but the model sent an empty reply. Try another model." else "Connected to ${s.model}. The AI helper is ready."
        }.getOrElse { "Couldn't connect: ${it.message ?: "unknown error"}" }
    }

    /** Same messages as the browser build, for Logcat and "Test connection". */
    private fun post(s: AiSettings, body: String): String = try {
        request(s, body)
    } catch (e: java.net.SocketTimeoutException) {
        throw IOException("no reply within 12 seconds", e)
    } catch (e: java.net.UnknownHostException) {
        throw IOException("no connection to the AI service", e)
    } catch (e: java.net.ConnectException) {
        throw IOException("no connection to the AI service", e)
    }

    private fun request(s: AiSettings, body: String): String {
        val connection = URI(s.baseUrl.trimEnd('/') + "/chat/completions").toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer ${s.apiKey.trim()}")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val reason = when (code) {
                    401 -> "the API key was not accepted"
                    404 -> "the model or address was not found"
                    429 -> "the free limit was reached; try again later"
                    else -> "HTTP $code"
                }
                throw IOException(reason + (AiPrompt.stringField(error, "message")?.let { " ($it)" } ?: ""))
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        /** Past this the customer would be left waiting: the rules answer instead. */
        private const val TIMEOUT_MS = 12_000
    }
}
