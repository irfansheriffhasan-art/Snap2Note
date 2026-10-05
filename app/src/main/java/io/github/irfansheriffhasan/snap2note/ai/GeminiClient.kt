package io.github.irfansheriffhasan.snap2note.ai

import android.graphics.Bitmap
import android.util.Base64
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import io.github.irfansheriffhasan.snap2note.data.ImageProcessing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Talks to the Gemini API with the user's own API key.
 * All public functions return a [Result] whose failures are [GeminiException]s with a
 * message that can be shown to the user as-is.
 */
class GeminiClient(private val apiKey: () -> String) {
  private val moshi = Moshi.Builder().build()
  private val analysisAdapter = moshi.adapter(PageAnalysisJson::class.java)
  private val errorAdapter = moshi.adapter(ErrorResponse::class.java)

  private val api: GeminiApi = Retrofit.Builder()
    .baseUrl(BASE_URL)
    .client(
      OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
    )
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .build()
    .create(GeminiApi::class.java)

  suspend fun analyzePage(image: File): Result<PageAnalysis> = runCatchingGemini {
    val encodedImage = withContext(Dispatchers.IO) {
      val bitmap = ImageProcessing.decode(UPLOAD_IMAGE_SIDE) { image.takeIf { it.exists() }?.inputStream() }
        ?: throw GeminiException("This page's image couldn't be read.")
      try {
        bitmap.toBase64Jpeg()
      } finally {
        bitmap.recycle()
      }
    }

    val request = GenerateContentRequest(
      contents = listOf(
        Content(
          role = "user",
          parts = listOf(
            Part(inlineData = InlineData(mimeType = "image/jpeg", data = encodedImage)),
            Part(text = Prompts.PAGE_ANALYSIS),
          ),
        ),
      ),
      generationConfig = GenerationConfig(
        temperature = 0.2f,
        responseMimeType = "application/json",
        responseSchema = Prompts.PAGE_ANALYSIS_SCHEMA,
      ),
    )

    val json = generate(ANALYSIS_MODEL, request).removeCodeFence()
    val parsed = analysisAdapter.fromJson(json) ?: throw GeminiException(UNEXPECTED_RESPONSE)
    PageAnalysis(
      title = parsed.title?.trim().orEmpty().ifEmpty { "Untitled page" },
      text = parsed.text?.trim().orEmpty(),
      summary = parsed.summary?.trim().orEmpty(),
      flashcards = parsed.flashcards.orEmpty().filter { it.question.isNotBlank() && it.answer.isNotBlank() },
    )
  }

  suspend fun chat(
    folderName: String,
    notes: String,
    history: List<ChatTurn>,
    message: String,
  ): Result<String> = runCatchingGemini {
    val contents = history.map { turn ->
      Content(role = if (turn.fromUser) "user" else "model", parts = listOf(Part(text = turn.text)))
    } + Content(role = "user", parts = listOf(Part(text = message)))

    val request = GenerateContentRequest(
      contents = contents,
      systemInstruction = Content(parts = listOf(Part(text = Prompts.studyAssistant(folderName, notes)))),
      generationConfig = GenerationConfig(temperature = 0.6f),
    )
    generate(CHAT_MODEL, request)
  }

  private suspend fun generate(model: String, request: GenerateContentRequest): String {
    val response = api.generateContent(model, apiKey(), request)
    val candidate = response.candidates?.firstOrNull()
    val text = candidate?.content?.parts
      ?.filter { it.thought != true }
      ?.mapNotNull { it.text }
      ?.joinToString("")
      ?.trim()
    if (text.isNullOrEmpty()) {
      val blocked = response.promptFeedback?.blockReason != null || candidate?.finishReason == "SAFETY"
      throw GeminiException(
        if (blocked) "Gemini declined to respond to this content." else "Gemini returned an empty response. Please try again.",
      )
    }
    return text
  }

  private suspend fun <T> runCatchingGemini(block: suspend () -> T): Result<T> {
    if (apiKey().isBlank()) {
      return Result.failure(GeminiException("Add your Gemini API key in Settings to use AI features.", stopsBatch = true))
    }
    return try {
      Result.success(block())
    } catch (e: CancellationException) {
      throw e
    } catch (e: GeminiException) {
      Result.failure(e)
    } catch (e: HttpException) {
      Result.failure(e.toGeminiException())
    } catch (e: JsonDataException) {
      Result.failure(GeminiException(UNEXPECTED_RESPONSE))
    } catch (e: JsonEncodingException) {
      Result.failure(GeminiException(UNEXPECTED_RESPONSE))
    } catch (e: IOException) {
      Result.failure(GeminiException("Couldn't reach Gemini. Check your internet connection and try again."))
    } catch (e: Exception) {
      Result.failure(GeminiException(e.message ?: "Something went wrong. Please try again."))
    }
  }

  private fun HttpException.toGeminiException(): GeminiException {
    val detail = runCatching { response()?.errorBody()?.string()?.let(errorAdapter::fromJson)?.error?.message }.getOrNull()
    return when (code()) {
      400 ->
        if (detail?.contains("API key", ignoreCase = true) == true) {
          GeminiException("Your Gemini API key isn't valid. Check it in Settings.", stopsBatch = true)
        } else {
          GeminiException(detail ?: "Gemini couldn't process this request.")
        }
      401, 403 -> GeminiException("Your Gemini API key doesn't have access to this model. Check it in Settings.", stopsBatch = true)
      404 -> GeminiException("The Gemini model isn't available right now. Please try again later.", stopsBatch = true)
      429 -> GeminiException("You've reached your Gemini usage limit. Wait a minute and try again.", stopsBatch = true)
      in 500..599 -> GeminiException("Gemini is temporarily unavailable. Please try again.")
      else -> GeminiException(detail ?: "Request failed (HTTP ${code()}).")
    }
  }

  private fun Bitmap.toBase64Jpeg(): String {
    val output = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, UPLOAD_JPEG_QUALITY, output)
    return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
  }

  private fun String.removeCodeFence(): String =
    trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

  private companion object {
    const val BASE_URL = "https://generativelanguage.googleapis.com/"
    const val ANALYSIS_MODEL = "gemini-3.5-flash"
    const val CHAT_MODEL = "gemini-3.5-flash"
    const val UPLOAD_IMAGE_SIDE = 1600
    const val UPLOAD_JPEG_QUALITY = 85
    const val UNEXPECTED_RESPONSE = "Gemini returned an unexpected response. Please try again."
  }
}
