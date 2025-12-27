package com.example.myapplicationeasyaiorder.data

import android.graphics.Bitmap
import com.example.myapplicationeasyaiorder.model.NimChatRequest
import com.example.myapplicationeasyaiorder.model.NimMessage
import com.example.myapplicationeasyaiorder.model.NimVisionRequest
import com.example.myapplicationeasyaiorder.model.NimVisionMessage
import com.example.myapplicationeasyaiorder.model.NimVisionContent
import com.example.myapplicationeasyaiorder.model.NimImageUrl
import com.example.myapplicationeasyaiorder.model.ParsedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class AiRepositoryImpl(private val apiKey: String) : AiRepository {

    private val nimService: NvidiaNimService by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://integrate.api.nvidia.com/v1/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NvidiaNimService::class.java)
    }

    override suspend fun chatWithAi(prompt: String): String {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("AiRepository", "Sending prompt to AI: ${prompt.take(100)}...")
                val request = NimChatRequest(
                    model = "meta/llama-3.1-8b-instruct",  // Using smaller, more reliable model
                    messages = listOf(NimMessage("user", prompt))
                )
                val response = nimService.chatCompletion("Bearer $apiKey", request)
                android.util.Log.d("AiRepository", "AI Response code: ${response.code()}")
                if (response.isSuccessful) {
                    val content = response.body()?.choices?.firstOrNull()?.message?.content ?: "No response from AI."
                    android.util.Log.d("AiRepository", "AI Response: ${content.take(100)}...")
                    content
                } else {
                    val error = response.errorBody()?.string() ?: response.message()
                    android.util.Log.e("AiRepository", "AI Error: ${response.code()} - $error")
                    "AI_ERROR: ${response.code()}"  // Prefix with AI_ERROR so we can detect it
                }
            } catch (e: Exception) {
                android.util.Log.e("AiRepository", "AI Exception: ${e.message}", e)
                "AI_ERROR: ${e.localizedMessage}"
            }
        }
    }

    override suspend fun analyzeImageForItems(image: Bitmap): List<String> {
        return withContext(Dispatchers.IO) {
            // Legacy method - kept for compatibility
            listOf("Apple", "Banana", "Milk (Simulated from NIM Stub)")
        }
    }

    override suspend fun analyzeListImage(base64Image: String): List<ParsedItem> {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("AiRepository", "Analyzing image for grocery list...")
                
                val prompt = """Look at this image of a grocery/shopping list. 
Extract ALL items from the list, whether handwritten or typed.
For each item, identify the quantity if mentioned (e.g., "milk x2" means quantity 2, "3 eggs" means quantity 3).
If no quantity is specified, assume quantity is 1.

Return ONLY a simple list in this exact format, one item per line:
item_name|quantity

Examples of valid output:
milk|2
eggs|1
bread|1
butter|3

Do not include any other text, explanations, or formatting. Just the items and quantities separated by pipe character."""

                // Build vision request with image
                val imageDataUrl = if (base64Image.startsWith("data:")) {
                    base64Image
                } else {
                    "data:image/jpeg;base64,$base64Image"
                }
                
                val request = NimVisionRequest(
                    model = "nvidia/llama-3.2-nv-vision-instruct-dpo",
                    messages = listOf(
                        NimVisionMessage(
                            role = "user",
                            content = listOf(
                                NimVisionContent(type = "text", text = prompt),
                                NimVisionContent(
                                    type = "image_url",
                                    image_url = NimImageUrl(url = imageDataUrl)
                                )
                            )
                        )
                    ),
                    max_tokens = 512,
                    temperature = 0.1
                )
                
                val response = nimService.visionCompletion("Bearer $apiKey", request)
                android.util.Log.d("AiRepository", "Vision Response code: ${response.code()}")
                
                if (response.isSuccessful) {
                    val content = response.body()?.choices?.firstOrNull()?.message?.content ?: ""
                    android.util.Log.d("AiRepository", "Vision Response: $content")
                    
                    // Parse the response
                    parseItemsFromResponse(content)
                } else {
                    val error = response.errorBody()?.string() ?: response.message()
                    android.util.Log.e("AiRepository", "Vision Error: ${response.code()} - $error")
                    emptyList()
                }
            } catch (e: Exception) {
                android.util.Log.e("AiRepository", "Vision Exception: ${e.message}", e)
                emptyList()
            }
        }
    }
    
    private fun parseItemsFromResponse(response: String): List<ParsedItem> {
        val items = mutableListOf<ParsedItem>()
        
        response.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && trimmed.contains("|")) {
                val parts = trimmed.split("|")
                if (parts.size >= 2) {
                    val name = parts[0].trim()
                    val quantity = parts[1].trim().toIntOrNull() ?: 1
                    if (name.isNotEmpty()) {
                        items.add(ParsedItem(name = name, quantity = quantity))
                    }
                }
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && !trimmed.contains("```")) {
                // Fallback: try to parse lines without pipe
                val quantityPatterns = listOf(
                    "(\\d+)\\s*[xX×]?\\s*(.+)".toRegex(),  // "2 milk" or "2x milk"
                    "(.+)\\s*[xX×]\\s*(\\d+)".toRegex()    // "milk x2"
                )
                
                var matched = false
                for (pattern in quantityPatterns) {
                    val match = pattern.find(trimmed)
                    if (match != null) {
                        val groups = match.groupValues
                        if (pattern.pattern.startsWith("(\\d+)")) {
                            val qty = groups[1].toIntOrNull() ?: 1
                            val name = groups[2].trim()
                            if (name.isNotEmpty()) {
                                items.add(ParsedItem(name = name, quantity = qty))
                                matched = true
                                break
                            }
                        } else {
                            val name = groups[1].trim()
                            val qty = groups[2].toIntOrNull() ?: 1
                            if (name.isNotEmpty()) {
                                items.add(ParsedItem(name = name, quantity = qty))
                                matched = true
                                break
                            }
                        }
                    }
                }
                
                if (!matched && trimmed.length > 1) {
                    // Just add as single item
                    items.add(ParsedItem(name = trimmed, quantity = 1))
                }
            }
        }
        
        return items.distinctBy { it.name.lowercase() }
    }
}

