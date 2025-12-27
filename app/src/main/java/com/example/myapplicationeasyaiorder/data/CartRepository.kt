package com.example.myapplicationeasyaiorder.data

import com.example.myapplicationeasyaiorder.model.CartResponse
import com.example.myapplicationeasyaiorder.model.Resource
import com.example.myapplicationeasyaiorder.model.CartUpdateRequest

class CartRepository(private val authManager: KrogerAuthManager) {

    suspend fun getCart(): Resource<CartResponse> {
        val token = authManager.getToken()
        android.util.Log.d("CartRepository", "getCart() called. Token exists? ${!token.isNullOrEmpty()}")
        
        if (token.isNullOrEmpty()) {
            android.util.Log.e("CartRepository", "Token is null or empty!")
            return Resource.Error("User not logged in")
        }

        return try {
            android.util.Log.d("CartRepository", "Making API call to getCart...")
            val response = RetrofitClient.krogerApi.getCart("Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                android.util.Log.d("CartRepository", "Cart Response: ${response.body()}")
                Resource.Success(response.body()!!)
            } else if (response.code() == 404) {
                 // 404 means no cart exists yet, return empty list
                android.util.Log.d("CartRepository", "Cart Response: empty")

                Resource.Success(CartResponse(data = com.example.myapplicationeasyaiorder.model.Cart(cartId = "new", items = emptyList())))
            } else {
                Resource.Error("Error: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            android.util.Log.e("CartRepository", "Exception in getCart", e)
            Resource.Error(e.message ?: "Network Error")
        }
    }

    suspend fun updateCart(request: CartUpdateRequest): Resource<Unit> {
        val token = authManager.getToken()
        if (token.isNullOrEmpty()) return Resource.Error("User not logged in")

        return try {
            val response = RetrofitClient.krogerApi.updateCart("Bearer $token", request)
            if (response.isSuccessful) {
                Resource.Success(Unit)
            } else {
                Resource.Error("Error updating cart: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network Error")
        }
    }

    suspend fun removeCartItem(cartId: String, upc: String): Resource<Unit> {
        val token = authManager.getToken()
        if (token.isNullOrEmpty()) return Resource.Error("User not logged in")

        return try {
            val response = RetrofitClient.krogerApi.deleteItem("Bearer $token", cartId, upc)
            if (response.isSuccessful) {
                Resource.Success(Unit)
            } else {
                Resource.Error("Error removing item: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network Error")
        }
    }
}
