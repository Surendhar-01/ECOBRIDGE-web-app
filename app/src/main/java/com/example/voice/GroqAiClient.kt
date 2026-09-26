package com.example.voice

import android.util.Log
import com.example.BuildConfig
import com.example.model.AppNavigationContext
import com.example.model.AppScreen
import com.example.model.Language
import com.example.model.MaterialCategory
import com.example.model.RoleType
import com.example.model.SemanticAnalysisResult
import com.example.model.SemanticIntent
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
 * Intelligent AI Intent Engine backed by Groq's OpenAI-compatible
 * `/chat/completions` endpoint, with strict JSON output and automatic fallback to
 * the on-device [SemanticIntentClassifier] when offline or unconfigured.
 *
 * Provider notes:
 * - Groq is text-only on the models available to this project (see
 *   `AiScannerClient`), so this class handles spoken-text intent only. Photo
 *   analysis stays on-device.
 * - `response_format = json_object` is deliberately NOT used: on
 *   `openai/gpt-oss-120b` it intermittently fails with
 *   "Failed to validate JSON". The prompt asks for raw JSON and the fence
 *   stripping below handles the rare fenced reply.
 */
class GroqAiClient {

    private companion object {
        const val TAG = "GroqAiClient"
        const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
        const val FALLBACK_TEXT_MODEL = "openai/gpt-oss-120b"
        /** `gpt-oss-120b` reasons before answering; 900 leaves room for the JSON. */
        const val MAX_TOKENS = 900
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Model override via `GROQ_TEXT_MODEL` in .env; the flag keeps the default
     *  working on a fresh clone that has no .env of its own. */
    private val model: String
        get() = runCatching { BuildConfig.GROQ_TEXT_MODEL }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && !it.startsWith("DEFAULT_") }
            ?: FALLBACK_TEXT_MODEL

    private fun apiKey(): String = runCatching { BuildConfig.GROQ_API_KEY }.getOrDefault("")

    private fun isUsable(key: String): Boolean =
        key.isNotBlank() && !key.startsWith("MY_") && !key.startsWith("DEFAULT_")

    suspend fun analyzeIntent(
        spokenText: String,
        context: AppNavigationContext,
        language: Language
    ): SemanticAnalysisResult = withContext(Dispatchers.IO) {
        val key = apiKey()

        if (!isUsable(key)) {
            Log.d(TAG, "Using on-device semantic engine (GROQ_API_KEY not configured or placeholder)")
            return@withContext SemanticIntentClassifier.analyze(spokenText, context, language)
        }

        try {
            val requestJson = JSONObject().apply {
                put("model", model)
                put("temperature", 0.1)
                put("max_tokens", MAX_TOKENS)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt(context))
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put(
                            "content",
                            "User spoken utterance: \"$spokenText\"\nPreferred Language: ${language.displayName}"
                        )
                    })
                })
            }

            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(ENDPOINT)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful || responseBody.isBlank()) {
                Log.w(TAG, "Groq API call failed code ${response.code}: $responseBody")
                return@withContext SemanticIntentClassifier.analyze(spokenText, context, language)
            }

            val rootJson = JSONObject(responseBody)
            val errorMessage = rootJson.optJSONObject("error")?.optString("message")
            if (!errorMessage.isNullOrBlank()) {
                Log.w(TAG, "Groq returned an error: $errorMessage")
                return@withContext SemanticIntentClassifier.analyze(spokenText, context, language)
            }

            val content = rootJson
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?: ""

            if (content.isBlank()) {
                return@withContext SemanticIntentClassifier.analyze(spokenText, context, language)
            }

            val parsed = parseIntentJson(content, spokenText, context, language)

            parsed ?: run {
                Log.w(TAG, "Groq reply was not valid intent JSON, using on-device engine")
                SemanticIntentClassifier.analyze(spokenText, context, language)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception during Groq AI parsing, falling back to local semantic engine", e)
            SemanticIntentClassifier.analyze(spokenText, context, language)
        }
    }

    private fun systemPrompt(context: AppNavigationContext): String = """
        You are the intelligent Voice Intent Router for an Indian E-Waste Management application.
        Users speak in English, Hindi, Marathi, or Hinglish.
        You must analyze the user's spoken sentence and output strict JSON with this exact schema:
        {
           "intent": "OPEN_LOGIN" | "NAVIGATE_SCREEN" | "QUERY_MATERIAL_PRICE" | "QUERY_SAFETY_GUIDELINES" | "QUERY_USER_STATS" | "CREATE_LOT_REQUEST" | "DESTRUCTIVE_ACTION_REQUEST" | "CONFIRM_ACTION" | "CANCEL_ACTION" | "CHANGE_LANGUAGE" | "HELP_AND_CAPABILITIES" | "GENERAL_EWASTE_QA" | "CLARIFY_REQUEST" | "SELECT_MOBILE_LOGIN" | "SELECT_EMAIL_LOGIN" | "SELECT_GOOGLE_LOGIN" | "SEND_OTP" | "RESEND_OTP" | "VERIFY_OTP" | "CHANGE_PHONE" | "OPEN_REGISTER" | "VOICE_HELP" | "GO_BACK",
           "targetRole": "INFORMAL_COLLECTOR" | "FORMAL_RECYCLER" | "GOVERNMENT_ADMIN" | null,
           "targetScreen": "INTRO" | "INFORMAL_COLLECTOR_AUTH" | "FORMAL_RECYCLER_AUTH" | "GOVERNMENT_ADMIN_AUTH" | "COLLECTOR_DASHBOARD" | "FORMAL_RECYCLER_PORTAL" | "GOVERNMENT_ADMIN_PORTAL" | null,
           "materialCategory": "PCB_BOARDS" | "CABLES_WIRES" | "BATTERIES" | "CRTS_MONITORS" | "LCD_PANELS" | "MOTORS_MAGNETS" | "MIXED_PLASTICS" | null,
           "quantityKg": number or null,
           "detectedLanguage": "en" | "hi" | "mr",
           "spokenResponse": "Short, clear, friendly spoken response in the user's detected language",
           "actionDescription": "Brief description of the action",
           "requiresConfirmation": boolean,
           "isDestructive": boolean
        }

        Current Application Context:
        - Current Screen: ${context.currentScreen.name}
        - Current Role: ${context.currentRole?.name ?: "None"}
        - Is Authenticated: ${context.isAuthenticated}
        - Has Pending Action: ${context.pendingAction != null}

        CRITICAL DIRECTIVE:
        If the user says anything that means opening, accessing, or going to the login page, or signing in (for collector, recycler, admin, or in general), you must recognize intent = "OPEN_LOGIN", set targetRole appropriately (or default to INFORMAL_COLLECTOR), set targetScreen accordingly, and provide a prompt response.
        DO NOT output markdown code blocks or commentary. Output raw JSON only.
    """.trimIndent()

    /** Returns null when the model reply cannot be read as intent JSON. */
    private fun parseIntentJson(
        rawText: String,
        spokenText: String,
        context: AppNavigationContext,
        language: Language
    ): SemanticAnalysisResult? {
        val cleanJson = stripCodeFence(rawText)
        val parsed = runCatching { JSONObject(cleanJson) }.getOrNull() ?: return null

        val intent = enumOrNull<SemanticIntent>(parsed.optString("intent", ""))
            ?: SemanticIntent.UNKNOWN
        val targetRole = enumOrNull<RoleType>(parsed.optString("targetRole", ""))
        val targetScreen = enumOrNull<AppScreen>(parsed.optString("targetScreen", ""))
        val materialCategory = enumOrNull<MaterialCategory>(parsed.optString("materialCategory", ""))

        val quantityKg = if (parsed.has("quantityKg") && !parsed.isNull("quantityKg")) {
            parsed.optDouble("quantityKg")
        } else {
            null
        }

        val detectedLanguage = when (parsed.optString("detectedLanguage", language.code)) {
            "hi" -> Language.HINDI
            "mr" -> Language.MARATHI
            else -> Language.ENGLISH
        }

        return SemanticAnalysisResult(
            intent = intent,
            targetRole = targetRole,
            targetScreen = targetScreen,
            materialCategory = materialCategory,
            quantityKg = quantityKg,
            detectedLanguage = detectedLanguage,
            confidence = 0.99f,
            spokenResponse = parsed.optString("spokenResponse", "").ifBlank { "Action processed." },
            actionDescription = parsed.optString("actionDescription", "Execute voice action"),
            requiresConfirmation = parsed.optBoolean("requiresConfirmation", false),
            isDestructive = parsed.optBoolean("isDestructive", false),
            rawTranscript = spokenText,
            aiEngineSource = "Groq ${model.substringAfterLast('/')}"
        )
    }

    /** Models occasionally wrap JSON in a ```json fence despite the instruction. */
    private fun stripCodeFence(text: String): String = text.trim()
        .removePrefix("```json")
        .removePrefix("```JSON")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()

    private inline fun <reified T : Enum<T>> enumOrNull(raw: String): T? {
        if (raw.isBlank() || raw == "null") return null
        return runCatching { enumValueOf<T>(raw) }.getOrNull()
    }
}
