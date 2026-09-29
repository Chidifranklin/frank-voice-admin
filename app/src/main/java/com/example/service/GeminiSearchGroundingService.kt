package com.example.service

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Result model representing answers grounded by Google Search data.
 */
data class GroundedSearchResult(
    val answer: String,
    val searchSources: List<SearchSourceItem> = emptyList(),
    val searchQueries: List<String> = emptyList()
)

data class SearchSourceItem(
    val title: String,
    val url: String
)

/**
 * Service providing real-time up-to-date information by querying Gemini 3.5 Flash
 * with Google Search Grounding tool enabled.
 */
class GeminiSearchGroundingService {

    companion object {
        const val MODEL = "gemini-3.5-flash"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Executes query with Google Search grounding enabled.
     * Uses gemini-3.5-flash with the googleSearch tool.
     */
    suspend fun queryWithSearchGrounding(userPrompt: String): GroundedSearchResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext GroundedSearchResult(
                answer = "Gemini API key is required to query live Google Search grounding. Configure it in AI Studio Secrets.",
                searchSources = emptyList()
            )
        }

        val endpoint = "$BASE_URL/$MODEL:generateContent?key=$apiKey"

        // Build payload with googleSearch tool enabled
        val jsonPayload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", userPrompt)
                        })
                    })
                })
            })
            // Configure googleSearch tool for grounding
            put("tools", JSONArray().apply {
                put(JSONObject().apply {
                    put("googleSearch", JSONObject())
                })
            })
            // System instructions
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", "You are Frank, the Super Admin with live Google Search Grounding. Provide direct, up-to-date, and accurate answers incorporating the latest search results.")
                    })
                })
            })
        }

        val request = Request.Builder()
            .url(endpoint)
            .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                return@withContext GroundedSearchResult(
                    answer = "Search Grounding error (${response.code}): ${response.message}",
                    searchSources = emptyList()
                )
            }

            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")

            // Parse response text
            val textBuilder = StringBuilder()
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val p = parts.getJSONObject(i)
                    if (p.has("text")) {
                        textBuilder.append(p.getString("text"))
                    }
                }
            }

            val answerText = if (textBuilder.isNotEmpty()) textBuilder.toString() else "No content returned."

            // Parse grounding metadata (search queries & sources)
            val sources = mutableListOf<SearchSourceItem>()
            val searchQueries = mutableListOf<String>()

            val groundingMetadata = firstCandidate?.optJSONObject("groundingMetadata")
            if (groundingMetadata != null) {
                // Web search queries executed by Google
                val webSearchQueries = groundingMetadata.optJSONArray("webSearchQueries")
                if (webSearchQueries != null) {
                    for (i in 0 until webSearchQueries.length()) {
                        searchQueries.add(webSearchQueries.getString(i))
                    }
                }

                // Grounding chunks (Sources)
                val groundingChunks = groundingMetadata.optJSONArray("groundingChunks")
                if (groundingChunks != null) {
                    for (i in 0 until groundingChunks.length()) {
                        val chunk = groundingChunks.getJSONObject(i)
                        val web = chunk.optJSONObject("web")
                        if (web != null) {
                            val title = web.optString("title", "Web Source")
                            val uri = web.optString("uri", "")
                            if (uri.isNotBlank()) {
                                sources.add(SearchSourceItem(title = title, url = uri))
                            }
                        }
                    }
                }
            }

            GroundedSearchResult(
                answer = answerText,
                searchSources = sources.distinctBy { it.url },
                searchQueries = searchQueries
            )
        } catch (e: Exception) {
            GroundedSearchResult(
                answer = "Error contacting Gemini Grounding: ${e.message}",
                searchSources = emptyList()
            )
        }
    }
}
