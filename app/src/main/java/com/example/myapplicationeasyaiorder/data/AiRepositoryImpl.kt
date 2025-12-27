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
                android.util.Log.d("AiRepository", "Analyzing image for grocery list... Image size: ${base64Image.length} chars")
                
                val prompt = """You are an OCR system. Read the handwritten or printed text in this image.

INSTRUCTIONS:
1. Look at the image carefully
2. Each LINE in the list is a SEPARATE item - do not combine lines
3. Read ONLY the text that is actually written/visible in the image
4. Do NOT add any words that are not in the image
5. Do NOT guess or infer - only report what you can actually see
6. If you cannot read a word clearly, skip it

IMPORTANT: If the list has items on separate lines, output them as separate items.
For example, if "pepper" is on one line and "hot sauce" is on another line, output:
pepper|1
hot sauce|1
NOT: pepper hot sauce|1

For each item, check if there's a number next to it:
- If a number is written (like "2" or "x3"), use that as quantity
- If no number is written, quantity is 1

OUTPUT FORMAT - one item per line:
item_name|quantity

CRITICAL: Only output items you can clearly see. Do not combine separate lines into one item."""

                // Build vision request with image
                val imageDataUrl = if (base64Image.startsWith("data:")) {
                    base64Image
                } else {
                    "data:image/jpeg;base64,$base64Image"
                }
                
                android.util.Log.d("AiRepository", "Using vision model: meta/llama-3.2-11b-vision-instruct")
                
                val request = NimVisionRequest(
                    model = "meta/llama-3.2-11b-vision-instruct",
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
                    max_tokens = 1024,
                    temperature = 0.2
                )
                
                val response = nimService.visionCompletion("Bearer $apiKey", request)
                android.util.Log.d("AiRepository", "Vision Response code: ${response.code()}")
                
                if (response.isSuccessful) {
                    val content = response.body()?.choices?.firstOrNull()?.message?.content ?: ""
                    android.util.Log.d("AiRepository", "Vision Response content: $content")
                    
                    if (content.isBlank()) {
                        android.util.Log.w("AiRepository", "Vision returned empty content")
                        return@withContext emptyList()
                    }
                    
                    // Parse the response
                    val items = parseItemsFromResponse(content)
                    android.util.Log.d("AiRepository", "Parsed ${items.size} items from vision response")
                    items
                } else {
                    val errorBody = response.errorBody()?.string() ?: ""
                    android.util.Log.e("AiRepository", "Vision Error: ${response.code()} - ${response.message()} - $errorBody")
                    
                    // If vision API fails, return empty - user will see "no items found"
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
        android.util.Log.d("AiRepository", "Parsing response: $response")
        
        response.lines().forEach { line ->
            val trimmed = line.trim()
                .replace("*", "")  // Remove markdown asterisks
                .replace("-", "")   // Remove list dashes
                .trim()
            
            if (trimmed.isEmpty()) return@forEach
            
            // Skip common non-item lines
            if (trimmed.startsWith("#") || 
                trimmed.contains("```") || 
                trimmed.lowercase().contains("here") ||
                trimmed.lowercase().contains("list") ||
                trimmed.lowercase().contains("item") && trimmed.contains(":")) {
                return@forEach
            }
            
            // Try pipe-separated format first (our requested format)
            if (trimmed.contains("|")) {
                val parts = trimmed.split("|")
                if (parts.size >= 2) {
                    val name = parts[0].trim()
                    val quantity = parts[1].trim().filter { it.isDigit() }.toIntOrNull() ?: 1
                    if (name.isNotEmpty() && name.length > 1) {
                        items.add(ParsedItem(name = name, quantity = quantity))
                        android.util.Log.d("AiRepository", "Parsed (pipe): $name x $quantity")
                        return@forEach
                    }
                }
            }
            
            // Try "quantity x item" or "item x quantity" patterns
            val qtyPatterns = listOf(
                "^(\\d+)\\s*[xX×]\\s*(.+)$".toRegex(),      // "2x milk" or "2 x milk"
                "^(\\d+)\\s+(.+)$".toRegex(),               // "2 milk"
                "^(.+?)\\s*[xX×]\\s*(\\d+)$".toRegex(),     // "milk x2" or "milk x 2"
                "^(.+?)\\s*\\((\\d+)\\)$".toRegex()          // "milk (2)"
            )
            
            for (pattern in qtyPatterns) {
                val match = pattern.find(trimmed)
                if (match != null) {
                    val groups = match.groupValues
                    val (name, qty) = if (groups[1].toIntOrNull() != null) {
                        Pair(groups[2].trim(), groups[1].toIntOrNull() ?: 1)
                    } else {
                        Pair(groups[1].trim(), groups[2].toIntOrNull() ?: 1)
                    }
                    if (name.isNotEmpty() && name.length > 1) {
                        items.add(ParsedItem(name = name, quantity = qty))
                        android.util.Log.d("AiRepository", "Parsed (pattern): $name x $qty")
                        return@forEach
                    }
                }
            }
            
            // If no pattern matched and line looks like a simple item name
            if (trimmed.length >= 2 && 
                !trimmed.contains("=") && 
                !trimmed.all { it.isDigit() }) {
                items.add(ParsedItem(name = trimmed, quantity = 1))
                android.util.Log.d("AiRepository", "Parsed (simple): $trimmed x 1")
            }
        }
        
        // Remove duplicates (keep first occurrence)
        val uniqueItems = items.distinctBy { it.name.lowercase().trim() }
        android.util.Log.d("AiRepository", "Final items count: ${uniqueItems.size}")
        return uniqueItems
    }
}


