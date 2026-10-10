package app.dictation

import android.content.Context
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface DictateResult {
    data class Ok(val text: String, val empty: Boolean, val cleaned: Boolean) : DictateResult

    /** Show the error; the audio is not worth keeping (401, 413, 415). */
    data class Fatal(val message: String) : DictateResult

    /** Keep the audio and retry later (502, other 5xx, no network, timeout). */
    data class Retryable(val message: String) : DictateResult
}

object DictationApi {
    fun dictate(ctx: Context, wav: ByteArray): DictateResult {
        val token = Settings.token(ctx)
            ?: return DictateResult.Retryable("No token set. Open the app and enter it in Settings")
        val conn = try {
            URL("${Settings.baseUrl(ctx)}/v1/dictate").openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return DictateResult.Retryable(e.message ?: "bad URL")
        }
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 60_000
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(wav.size)
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "audio/wav")
            conn.setRequestProperty("X-Client", "android")
            conn.outputStream.use { it.write(wav) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            when {
                code == 200 -> {
                    val json = JSONObject(body)
                    DictateResult.Ok(
                        text = json.optString("text"),
                        empty = json.optBoolean("empty"),
                        cleaned = json.optBoolean("cleaned", true),
                    )
                }
                code in 400..499 -> DictateResult.Fatal("$code ${errorText(body)}")
                else -> DictateResult.Retryable("$code ${errorText(body)}")
            }
        } catch (e: IOException) {
            DictateResult.Retryable(e.message ?: e.javaClass.simpleName)
        } catch (e: org.json.JSONException) {
            DictateResult.Retryable("bad response: ${e.message}")
        } finally {
            conn.disconnect()
        }
    }

    private fun errorText(body: String): String = try {
        val json = JSONObject(body)
        "${json.optString("error")}: ${json.optString("message")}"
    } catch (e: org.json.JSONException) {
        body.take(200)
    }
}
