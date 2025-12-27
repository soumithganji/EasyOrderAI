package com.example.myapplicationeasyaiorder.ui.camera

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplicationeasyaiorder.data.AiRepository
import com.example.myapplicationeasyaiorder.data.CartRepository
import com.example.myapplicationeasyaiorder.data.LocalCartRepository
import com.example.myapplicationeasyaiorder.data.ProductRepository
import com.example.myapplicationeasyaiorder.model.CartItemRequest
import com.example.myapplicationeasyaiorder.model.CartUpdateRequest
import com.example.myapplicationeasyaiorder.model.ParsedItem
import com.example.myapplicationeasyaiorder.model.Resource
import com.example.myapplicationeasyaiorder.ui.chat.PendingCartItem
import kotlinx.coroutines.launch

class ScanViewModel(
    private val aiRepository: AiRepository,
    private val productRepository: ProductRepository,
    private val cartRepository: CartRepository
) : ViewModel() {

    // Status message for UI feedback
    private val _statusMessage = MutableLiveData<String>("")
    val statusMessage: LiveData<String> = _statusMessage

    // Loading state
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Image captured/selected
    private val _capturedImageBase64 = MutableLiveData<String?>(null)
    val capturedImageBase64: LiveData<String?> = _capturedImageBase64

    // Pending items waiting for user confirmation
    private val _pendingItems = MutableLiveData<List<PendingCartItem>?>(null)
    val pendingItems: LiveData<List<PendingCartItem>?> = _pendingItems

    // Unavailable items
    private val _unavailableItems = MutableLiveData<List<String>>(emptyList())
    val unavailableItems: LiveData<List<String>> = _unavailableItems

    // Result message after adding to cart
    private val _resultMessage = MutableLiveData<String?>(null)
    val resultMessage: LiveData<String?> = _resultMessage

    fun setImageBase64(base64: String) {
        _capturedImageBase64.value = base64
        _statusMessage.value = "Image ready. Processing..."
        analyzeImage(base64)
    }

    private fun analyzeImage(base64Image: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = "Analyzing image with AI..."

            try {
                val parsedItems = aiRepository.analyzeListImage(base64Image)
                android.util.Log.d("ScanViewModel", "Parsed ${parsedItems.size} items from image")

                if (parsedItems.isEmpty()) {
                    _statusMessage.value = "⚠️ No items found in the image. Try a clearer photo."
                    _isLoading.value = false
                    return@launch
                }

                _statusMessage.value = "Found ${parsedItems.size} items. Searching products..."
                searchProducts(parsedItems)
            } catch (e: Exception) {
                android.util.Log.e("ScanViewModel", "Error analyzing image", e)
                _statusMessage.value = "❌ Error analyzing image: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    private suspend fun searchProducts(items: List<ParsedItem>) {
        val foundItems = mutableListOf<PendingCartItem>()
        val notFoundItems = mutableListOf<String>()

        for (item in items) {
            android.util.Log.d("ScanViewModel", "Searching for: ${item.name} (qty: ${item.quantity})")
            _statusMessage.value = "Searching: ${item.name}..."

            when (val productResult = productRepository.findCheapestVariant(item.name)) {
                is Resource.Success -> {
                    val product = productResult.data
                    val productItem = product.items.firstOrNull()
                    if (productItem != null) {
                        foundItems.add(
                            PendingCartItem(
                                productId = productItem.itemId,
                                name = product.description,
                                price = productItem.price?.regular ?: 0.0,
                                quantity = item.quantity,
                                imageUrl = product.images.firstOrNull()?.url
                            )
                        )
                    } else {
                        notFoundItems.add(item.name)
                    }
                }
                is Resource.Error -> {
                    android.util.Log.e("ScanViewModel", "Error finding ${item.name}: ${productResult.message}")
                    notFoundItems.add(item.name)
                }
                else -> notFoundItems.add(item.name)
            }
        }

        _isLoading.value = false

        if (foundItems.isNotEmpty()) {
            _statusMessage.value = "Found ${foundItems.size} products. Review and confirm."
            _pendingItems.value = foundItems
            _unavailableItems.value = notFoundItems
        } else {
            _statusMessage.value = "⚠️ Couldn't find any products from the list."
        }
    }

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
        _statusMessage.value = "Cancelled. Take another photo or upload an image."
    }

    fun confirmPendingItems() {
        val items = _pendingItems.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = "Adding items to cart..."

            var successCount = 0
            for (item in items) {
                val cartRequest = CartUpdateRequest(
                    items = listOf(CartItemRequest(upc = item.productId, quantity = item.quantity))
                )
                when (cartRepository.updateCart(cartRequest)) {
                    is Resource.Success -> {
                        LocalCartRepository.addItem(
                            LocalCartRepository.LocalCartItem(
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

            _pendingItems.value = null
            _isLoading.value = false
            _resultMessage.value = "✅ Added $successCount items to cart!"
            _statusMessage.value = "✅ Added $successCount items to cart! Scan another list or check your cart."
            _capturedImageBase64.value = null
        }
    }

    fun clearResultMessage() {
        _resultMessage.value = null
    }

    fun reset() {
        _capturedImageBase64.value = null
        _pendingItems.value = null
        _unavailableItems.value = emptyList()
        _statusMessage.value = "Take a photo or upload an image of your grocery list."
        _isLoading.value = false
    }
}
