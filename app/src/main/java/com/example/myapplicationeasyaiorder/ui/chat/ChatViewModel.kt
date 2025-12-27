package com.example.myapplicationeasyaiorder.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplicationeasyaiorder.data.AiRepository
import com.example.myapplicationeasyaiorder.data.CartRepository
import com.example.myapplicationeasyaiorder.data.ProductRepository
import com.example.myapplicationeasyaiorder.model.CartItemRequest
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)


class ChatViewModel(
    private val aiRepository: AiRepository,
    private val productRepository: ProductRepository,
    private val cartRepository: CartRepository
) : ViewModel() {
    private val _messages = MutableLiveData<List<ChatMessage>>(emptyList())
    val messages: LiveData<List<ChatMessage>> = _messages
    
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Pending items waiting for user confirmation
    private val _pendingItems = MutableLiveData<List<PendingCartItem>?>(null)
    val pendingItems: LiveData<List<PendingCartItem>?> = _pendingItems

    // Unavailable items to display in dialog
    private val _unavailableItems = MutableLiveData<List<String>>(emptyList())
    val unavailableItems: LiveData<List<String>> = _unavailableItems

    fun updatePendingItemQuantity(productId: String, newQty: Int) {
        val current = _pendingItems.value?.toMutableList() ?: return
        val index = current.indexOfFirst { it.productId == productId }
        if (index >= 0) {
            current[index] = current[index].copy(quantity = newQty)
            _pendingItems.value = current
        }
    }

    fun removePendingItem(productId: String) {
        val current = _pendingItems.value?.toMutableList() ?: return
        current.removeAll { it.productId == productId }
        _pendingItems.value = if (current.isEmpty()) null else current
    }

    fun cancelPendingItems() {
        _pendingItems.value = null
    }

    fun confirmPendingItems() {
        val items = _pendingItems.value ?: return
        viewModelScope.launch {
            var successCount = 0
            for (item in items) {
                val cartRequest = com.example.myapplicationeasyaiorder.model.CartUpdateRequest(
                    items = listOf(CartItemRequest(upc = item.productId, quantity = item.quantity))
                )
                when (cartRepository.updateCart(cartRequest)) {
                    is com.example.myapplicationeasyaiorder.model.Resource.Success -> {
                        com.example.myapplicationeasyaiorder.data.LocalCartRepository.addItem(
                            com.example.myapplicationeasyaiorder.data.LocalCartRepository.LocalCartItem(
                                productId = item.productId,
                                name = item.name,
                                price = item.price,
                                quantity = item.quantity,
                                imageUrl = item.imageUrl
                            )
                        )
                        successCount++
                    }
                    else -> {}
                }
            }
            val updatedList = _messages.value.orEmpty().toMutableList()
            updatedList.add(ChatMessage(text = "✅ Added $successCount items to cart!", isUser = false))
            _messages.value = updatedList
            _pendingItems.value = null
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        
        val currentList = _messages.value.orEmpty().toMutableList()
        currentList.add(ChatMessage(text = text, isUser = true))
        _messages.value = currentList
        
        _isLoading.value = true
        
        viewModelScope.launch {
            // Check for recipe/ingredient requests
            val recipePatterns = listOf(
                "(?i)ingredients?\\s+(for|to make)\\s+(.+)".toRegex(),
                "(?i)what do i need (for|to make)\\s+(.+)".toRegex(),
                "(?i)add.*ingredients?.*for\\s+(.+)".toRegex(),
                "(?i)make\\s+(.+)".toRegex()
            )
            
            var recipeMatch: MatchResult? = null
            var recipeName: String? = null
            
            for (pattern in recipePatterns) {
                recipeMatch = pattern.find(text)
                if (recipeMatch != null) {
                    recipeName = recipeMatch.groupValues.last()
                    break
                }
            }
            
            // Basic Intent Parsing for "Add [item]"
            val addRegex = "(?i)^add\\s+(.+)".toRegex()
            val matchResult = addRegex.find(text)
            
            when {
                recipeName != null && !recipeName.contains("add", ignoreCase = true) -> {
                    handleRecipeAdd(recipeName, currentList)
                }
                matchResult != null -> {
                    val itemName = matchResult.groupValues[1]
                    if (itemName.contains("ingredient", ignoreCase = true) || 
                        itemName.contains("for", ignoreCase = true) ||
                        itemName.contains("to make", ignoreCase = true)) {
                        val cleanRecipe = itemName.replace("(?i)ingredients?\\s*(for|to make)?\\s*".toRegex(), "").trim()
                        handleRecipeAdd(cleanRecipe, currentList)
                    } else {
                        handleSmartAdd(itemName, currentList)
                    }
                }
                else -> {
                    // Use AI to classify if this looks like a grocery item
                    val classifyPrompt = """Classify this message: "$text"
Is this:
1. A grocery item or list of grocery items (e.g., "milk", "eggs and bread", "2 apples")
2. A recipe request (e.g., "carbonara", "chicken soup")
3. Something else (a question, greeting, etc.)

Reply with ONLY one word: ITEM, RECIPE, or OTHER"""

                    val classification = aiRepository.chatWithAi(classifyPrompt).trim().uppercase()
                    
                    when {
                        classification.contains("ITEM") -> {
                            // Treat as single item or parse multiple items
                            val items = text.split(",", " and ", "&")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                            
                            if (items.size == 1) {
                                handleSmartAdd(items.first(), currentList)
                            } else {
                                // Multiple items - add each
                                for (item in items) {
                                    handleSmartAdd(item, currentList)
                                }
                            }
                        }
                        classification.contains("RECIPE") -> {
                            handleRecipeAdd(text, currentList)
                        }
                        else -> {
                            val response = aiRepository.chatWithAi(text)
                            val updatedList = _messages.value.orEmpty().toMutableList()
                            updatedList.add(ChatMessage(text = response, isUser = false))
                            _messages.value = updatedList
                        }
                    }
                }
            }
            _isLoading.value = false
        }
    }

    private suspend fun handleRecipeAdd(recipeName: String, currentList: MutableList<ChatMessage>) {
        val updatedList = currentList.toMutableList()
        updatedList.add(ChatMessage(text = "Finding ingredients for $recipeName...", isUser = false))
        _messages.value = updatedList
        
        // Ask AI for ingredient list
        val prompt = """List the essential grocery ingredients needed to make "$recipeName".
Return ONLY a comma-separated list of simple ingredient names.
Example format: chicken, butter, hot sauce, garlic, celery
List 5-8 main ingredients only. No quantities, no numbers, no instructions."""

        val aiResponse = aiRepository.chatWithAi(prompt)
        android.util.Log.d("ChatViewModel", "AI Response for ingredients: $aiResponse")
        
        // Check for AI errors
        if (aiResponse.startsWith("AI_ERROR")) {
            updatedList.add(ChatMessage(text = "⚠️ AI service temporarily unavailable. Please try again.", isUser = false))
            _messages.value = updatedList
            return
        }
        
        // Parse comma-separated ingredients - handle various formats
        val ingredients = aiResponse
            .replace("\n", ",")
            .replace(".", ",")
            .replace("-", "")
            .replace(Regex("\\d+"), "") // Remove numbers
            .replace(Regex("\\([^)]*\\)"), "") // Remove parentheses content
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && it.length > 2 && !it.contains("ingredient") }
            .map { it.replace(Regex("^(a |an |the |some )"), "") } // Remove articles
            .distinct()
            .take(10)
        
        android.util.Log.d("ChatViewModel", "Parsed ingredients: $ingredients")
        
        if (ingredients.isEmpty()) {
            updatedList.add(ChatMessage(text = "Couldn't determine ingredients for $recipeName.", isUser = false))
            _messages.value = updatedList
            return
        }
        
        updatedList.add(ChatMessage(text = "Found ${ingredients.size} ingredients: ${ingredients.joinToString(", ")}. Searching...", isUser = false))
        _messages.value = updatedList
        
        val foundItems = mutableListOf<PendingCartItem>()
        val notFoundItems = mutableListOf<String>()
        
        for (ingredient in ingredients) {
            android.util.Log.d("ChatViewModel", "Searching for ingredient: $ingredient")
            when (val productResult = productRepository.findCheapestVariant(ingredient)) {
                is com.example.myapplicationeasyaiorder.model.Resource.Success -> {
                    val product = productResult.data
                    val item = product.items.firstOrNull()
                    android.util.Log.d("ChatViewModel", "Found product: ${product.description}, item: ${item?.itemId}")
                    if (item != null) {
                        foundItems.add(
                            PendingCartItem(
                                productId = item.itemId,
                                name = product.description,
                                price = item.price?.regular ?: 0.0,
                                quantity = 1,
                                imageUrl = product.images.firstOrNull()?.url
                            )
                        )
                    } else {
                        notFoundItems.add(ingredient)
                    }
                }
                is com.example.myapplicationeasyaiorder.model.Resource.Error -> {
                    android.util.Log.e("ChatViewModel", "Error finding $ingredient: ${productResult.message}")
                    notFoundItems.add(ingredient)
                }
                else -> notFoundItems.add(ingredient)
            }
        }
        
        if (foundItems.isNotEmpty()) {
            // Show confirmation dialog via LiveData
            _pendingItems.value = foundItems
            _unavailableItems.value = notFoundItems
        } else {
            updatedList.add(ChatMessage(text = "⚠️ Couldn't find any products for $recipeName.", isUser = false))
            _messages.value = updatedList
        }
    }

    private suspend fun handleSmartAdd(itemName: String, currentList: MutableList<ChatMessage>) {
        val updatedList = currentList.toMutableList()
        // 1. Search for cheapest variant
        updatedList.add(ChatMessage(text = "Searching for cheapest $itemName...", isUser = false))
        _messages.value = updatedList
        
        when (val productResult = productRepository.findCheapestVariant(itemName)) {
            is com.example.myapplicationeasyaiorder.model.Resource.Success -> {
                val product = productResult.data
                val item = product.items.firstOrNull()
                if (item != null) {
                   // 2. Add to Cart
                   val cartRequest = com.example.myapplicationeasyaiorder.model.CartUpdateRequest(
                       items = listOf(CartItemRequest(upc = item.itemId, quantity = 1))
                   )
                   when (val cartResult = cartRepository.updateCart(cartRequest)) {
                       is com.example.myapplicationeasyaiorder.model.Resource.Success -> {
                            // Add to local session cart
                            val imageUrl = product.images.firstOrNull()?.url
                            com.example.myapplicationeasyaiorder.data.LocalCartRepository.addItem(
                                com.example.myapplicationeasyaiorder.data.LocalCartRepository.LocalCartItem(
                                    productId = item.itemId,
                                    name = product.description,
                                    price = item.price?.regular ?: 0.0,
                                    quantity = 1,
                                    imageUrl = imageUrl
                                )
                            )
                            updatedList.add(ChatMessage(text = "Added ${product.description} ($${item.price?.regular}) to cart!", isUser = false))
                       }
                       is com.example.myapplicationeasyaiorder.model.Resource.Error -> {
                            updatedList.add(ChatMessage(text = "Failed to add to cart: ${cartResult.message}", isUser = false))
                       }
                       else -> {}
                   }
                } else {
                    updatedList.add(ChatMessage(text = "Product found but no item details available.", isUser = false))
                }
            }
            is com.example.myapplicationeasyaiorder.model.Resource.Error -> {
                updatedList.add(ChatMessage(text = "Could not find $itemName. ${productResult.message}", isUser = false))
            }
            else -> {}
        }
        _messages.value = updatedList
    }
}
