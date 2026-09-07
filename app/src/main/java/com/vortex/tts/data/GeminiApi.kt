package com.vortex.tts.data

import com.vortex.tts.model.GenerateContentRequest
import com.vortex.tts.model.GenerateContentResponse
import com.vortex.tts.model.GeminiModelListResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface GeminiApi {
    @GET("v1beta/models")
    suspend fun listModels(
        @Header("x-goog-api-key") apiKey: String
    ): Response<GeminiModelListResponse>

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GenerateContentRequest
    ): Response<GenerateContentResponse>
}
