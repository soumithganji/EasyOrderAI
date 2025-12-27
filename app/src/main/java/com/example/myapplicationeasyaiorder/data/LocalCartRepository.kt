package com.example.myapplicationeasyaiorder.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * In-memory cart that tracks items added during the current app session.
 * This is a singleton that persists for the app lifecycle.
 */
object LocalCartRepository {
    
    data class LocalCartItem(
        val productId: String,
        val name: String,
        val price: Double,
        var quantity: Int,
        val imageUrl: String? = null
    )
    
    private val _cartItems = MutableLiveData<List<LocalCartItem>>(emptyList())
    val cartItems: LiveData<List<LocalCartItem>> = _cartItems
    
    fun addItem(item: LocalCartItem) {
        val currentList = _cartItems.value.orEmpty().toMutableList()
        
        // Check if item already exists
        val existingIndex = currentList.indexOfFirst { it.productId == item.productId }
        if (existingIndex >= 0) {
            // Increase quantity
            val existing = currentList[existingIndex]
            currentList[existingIndex] = existing.copy(quantity = existing.quantity + item.quantity)
        } else {
            currentList.add(item)
        }
        
        _cartItems.value = currentList
    }
    
    fun updateQuantity(productId: String, newQuantity: Int) {
        val currentList = _cartItems.value.orEmpty().toMutableList()
        val index = currentList.indexOfFirst { it.productId == productId }
        
        if (index >= 0) {
            if (newQuantity <= 0) {
                currentList.removeAt(index)
            } else {
                currentList[index] = currentList[index].copy(quantity = newQuantity)
            }
            _cartItems.value = currentList
        }
    }
    
    fun removeItem(productId: String) {
        val currentList = _cartItems.value.orEmpty().toMutableList()
        currentList.removeAll { it.productId == productId }
        _cartItems.value = currentList
    }
    
    fun clearCart() {
        _cartItems.value = emptyList()
    }
    
    fun getItems(): List<LocalCartItem> = _cartItems.value.orEmpty()
}
