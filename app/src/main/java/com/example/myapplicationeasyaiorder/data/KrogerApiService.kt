package com.example.myapplicationeasyaiorder.data

import com.example.myapplicationeasyaiorder.model.*
import retrofit2.Response
import retrofit2.http.*

interface KrogerApiService {
    
    // Auth
    @FormUrlEncoded
    @POST("connect/oauth2/token")
    suspend fun getAccessToken(
        @Header("Authorization") authorization: String, // "Basic base64(client_id:secret)"
        @Field("grant_type") grantType: String = "client_credentials",
        @Field("scope") scope: String? = "product.compact cart.basic:write" 
    ): Response<TokenResponse>

    // Products
    @GET("products")
    suspend fun searchProducts(
        @Header("Authorization") token: String,
        @Query("filter.term") term: String,
        @Query("filter.locationId") locationId: String, // Store ID is required for pricing
        @Query("filter.limit") limit: Int = 5
    ): Response<ProductResponse>

    // Cart
    @GET("carts")
    suspend fun getCart(
        @Header("Authorization") token: String
    ): Response<CartResponse>

    @PUT("cart/add")
    suspend fun updateCart(
        @Header("Authorization") token: String,
        @Body cartRequest: CartUpdateRequest
    ): Response<Unit>

    @DELETE("carts/{cartId}/items/{upc}")
    suspend fun deleteItem(
        @Header("Authorization") token: String,
        @Path("cartId") cartId: String,
        @Path("upc") upc: String
    ): Response<Unit>
}
