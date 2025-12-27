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

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        
        val currentList = _messages.value.orEmpty().toMutableList()
        currentList.add(ChatMessage(text = text, isUser = true))
        _messages.value = currentList
        
        _isLoading.value = true
        
        // Basic Intent Parsing for "Add [item]"
        val addRegex = "(?i)^add\\s+(.+)".toRegex()
        val matchResult = addRegex.find(text)
        
        viewModelScope.launch {
            if (matchResult != null) {
                val itemName = matchResult.groupValues[1]
                handleSmartAdd(itemName, currentList)
            } else {
                val response = aiRepository.chatWithAi(text)
                val updatedList = _messages.value.orEmpty().toMutableList()
                updatedList.add(ChatMessage(text = response, isUser = false))
                _messages.value = updatedList
            }
            _isLoading.value = false
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
