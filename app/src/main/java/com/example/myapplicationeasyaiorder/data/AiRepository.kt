package com.example.myapplicationeasyaiorder.data

import android.graphics.Bitmap
import com.example.myapplicationeasyaiorder.model.ParsedItem

interface AiRepository {
    suspend fun chatWithAi(prompt: String): String
    suspend fun analyzeImageForItems(image: Bitmap): List<String>
    suspend fun analyzeListImage(base64Image: String): List<ParsedItem>
}

