package com.example.myapplicationeasyaiorder.data

import com.example.myapplicationeasyaiorder.model.Product
import com.example.myapplicationeasyaiorder.model.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProductRepository(
    private val authManager: KrogerAuthManager,
    private val aiRepository: AiRepository? = null
) {

    suspend fun searchProducts(term: String, locationId: String = "01400943"): Resource<List<Product>> {
        val token = authManager.getToken()
        if (token.isNullOrEmpty()) return Resource.Error("User not logged in")

        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("ProductRepository", "Searching for: $term")
                val response = RetrofitClient.krogerApi.searchProducts("Bearer $token", term, locationId)
                android.util.Log.d("ProductRepository", "Search response: ${response.code()} - ${response.message()}")
                if (response.isSuccessful && response.body() != null) {
                    android.util.Log.d("ProductRepository", "Found ${response.body()!!.data.size} products")
                    Resource.Success(response.body()!!.data)
                } else if (response.code() == 401) {
                    // Token expired - clear it and notify app
                    authManager.clearToken()
                    AuthState.notifySessionExpired()
                    android.util.Log.e("ProductRepository", "Token expired, cleared. Redirecting to login.")
                    Resource.Error("Session expired")
                } else {
                    android.util.Log.e("ProductRepository", "Error: ${response.code()} ${response.errorBody()?.string()}")
                    Resource.Error("Error searching products: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("ProductRepository", "Exception: ${e.message}", e)
                Resource.Error(e.message ?: "Network Error")
            }
        }
    }

    /**
     * Use AI to pick the most relevant/commonly purchased product from search results.
     */
    suspend fun findBestProduct(term: String): Resource<Product> {
        return when (val result = searchProducts(term)) {
            is Resource.Success -> {
                val products = result.data
                if (products.isEmpty()) {
                    return Resource.Error("No products found for $term")
                }
                
                // If we have AI, use it to pick the best product
                if (aiRepository != null && products.size > 1) {
                    val selectedProduct = selectWithAi(term, products)
                    if (selectedProduct != null) {
                        return Resource.Success(selectedProduct)
                    }
                }
                
                // Fallback: Use first result (Kroger's most relevant)
                Resource.Success(products.first())
            }
            is Resource.Error -> Resource.Error(result.message)
            else -> Resource.Error("Unknown error")
        }
    }
    
    private suspend fun selectWithAi(searchTerm: String, products: List<Product>): Product? {
        if (aiRepository == null) return null
        
        // Build a list of options for the AI
        val optionsList = products.take(5).mapIndexed { index, product ->
            val price = product.items.firstOrNull()?.price?.regular ?: 0.0
            val size = product.items.firstOrNull()?.size ?: "unknown size"
            "${index + 1}. ${product.description} - $size - $${"%.2f".format(price)}"
        }.joinToString("\n")
        
        val prompt = """You are a shopping assistant. A customer wants to buy "$searchTerm".
Here are the available options:
$optionsList

Pick the NUMBER (1-${products.take(5).size}) of the product that a typical shopper would most likely want. 
Consider: common sizes (gallon of milk, dozen eggs, standard packages), popular brands, and reasonable quantities.
Reply with ONLY the number, nothing else."""

        val aiResponse = aiRepository.chatWithAi(prompt)
        
        // Parse the number from AI response
        val selectedIndex = aiResponse.trim().filter { it.isDigit() }.toIntOrNull()
        
        return if (selectedIndex != null && selectedIndex in 1..products.take(5).size) {
            products[selectedIndex - 1]
        } else {
            null // Fallback to first result
        }
    }
    
    // Keep old method for backwards compatibility
    suspend fun findCheapestVariant(term: String): Resource<Product> = findBestProduct(term)
}

