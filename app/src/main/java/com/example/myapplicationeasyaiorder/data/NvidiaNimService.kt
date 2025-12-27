package com.example.myapplicationeasyaiorder.data

import com.example.myapplicationeasyaiorder.model.NimChatRequest
import com.example.myapplicationeasyaiorder.model.NimChatResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

import retrofit2.http.Headers

interface NvidiaNimService {
    @Headers("Content-Type: application/json")
    @POST("chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") apiKey: String,
        @Body request: NimChatRequest
    ): Response<NimChatResponse>
}
