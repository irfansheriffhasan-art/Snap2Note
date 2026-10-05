package io.github.irfansheriffhasan.snap2note.ai

import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/** Minimal binding for the Gemini `generateContent` REST endpoint. */
interface GeminiApi {
  @POST("v1beta/models/{model}:generateContent")
  suspend fun generateContent(
    @Path("model") model: String,
    @Header("x-goog-api-key") apiKey: String,
    @Body request: GenerateContentRequest,
  ): GenerateContentResponse
}

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
  val contents: List<Content>,
  val systemInstruction: Content? = null,
  val generationConfig: GenerationConfig? = null,
)

@JsonClass(generateAdapter = true)
data class Content(
  val parts: List<Part>,
  val role: String? = null,
)

@JsonClass(generateAdapter = true)
data class Part(
  val text: String? = null,
  val inlineData: InlineData? = null,
  val thought: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class InlineData(
  val mimeType: String,
  val data: String,
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
  val temperature: Float? = null,
  val responseMimeType: String? = null,
  val responseSchema: Map<String, Any>? = null,
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
  val candidates: List<Candidate>? = null,
  val promptFeedback: PromptFeedback? = null,
)

@JsonClass(generateAdapter = true)
data class Candidate(
  val content: Content? = null,
  val finishReason: String? = null,
)

@JsonClass(generateAdapter = true)
data class PromptFeedback(
  val blockReason: String? = null,
)

@JsonClass(generateAdapter = true)
data class ErrorResponse(
  val error: ErrorBody? = null,
)

@JsonClass(generateAdapter = true)
data class ErrorBody(
  val code: Int? = null,
  val message: String? = null,
  val status: String? = null,
)
