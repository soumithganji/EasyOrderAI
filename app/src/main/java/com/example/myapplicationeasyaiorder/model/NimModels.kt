package com.example.myapplicationeasyaiorder.model

data class NimChatRequest(
    val model: String,
    val messages: List<NimMessage>,
    val max_tokens: Int = 1024,
    val temperature: Double = 0.2,
    val top_p: Double = 0.7,
    val stream: Boolean = false
)

data class NimMessage(
    val role: String,
    val content: String
)

data class NimChatResponse(
    val id: String,
    val choices: List<NimChoice>
)

data class NimChoice(
    val index: Int,
    val message: NimMessage
)

// Vision API models for image-based requests
data class NimVisionRequest(
    val model: String,
    val messages: List<NimVisionMessage>,
    val max_tokens: Int = 1024,
    val temperature: Double = 0.2,
    val top_p: Double = 0.7,
    val stream: Boolean = false
)

data class NimVisionMessage(
    val role: String,
    val content: List<NimVisionContent>
)

data class NimVisionContent(
    val type: String,  // "text" or "image_url"
    val text: String? = null,
    val image_url: NimImageUrl? = null
)

data class NimImageUrl(
    val url: String  // base64 data URL: "data:image/jpeg;base64,..."
)

// Parsed item with name and quantity
data class ParsedItem(
    val name: String,
    val quantity: Int = 1
)
