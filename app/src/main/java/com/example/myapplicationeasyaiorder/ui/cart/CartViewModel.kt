package com.example.myapplicationeasyaiorder.ui.cart

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplicationeasyaiorder.data.CartRepository
import com.example.myapplicationeasyaiorder.model.Cart
import com.example.myapplicationeasyaiorder.model.CartItem
import com.example.myapplicationeasyaiorder.model.Resource
import kotlinx.coroutines.launch

class CartViewModel(private val repository: CartRepository) : ViewModel() {
    private val _cartState = MutableLiveData<Resource<Cart>>()
    val cartState: LiveData<Resource<Cart>> = _cartState

    fun loadCart() {
        android.util.Log.d("CartViewModel", "loadCart() called")
        _cartState.value = Resource.Loading
        viewModelScope.launch {
            android.util.Log.d("CartViewModel", "Coroutine started, calling repository.getCart()")
            when (val result = repository.getCart()) {
                is Resource.Success -> {
                    android.util.Log.d("CartViewModel", "Success: ${result.data.data.items.size} items")
                    _cartState.value = Resource.Success(result.data.data)
                }
                is Resource.Error -> {
                    android.util.Log.e("CartViewModel", "Error: ${result.message}")
                    _cartState.value = Resource.Error(result.message)
                }
                is Resource.Loading -> {
                     // no-op
                }
            }
        }
    }

    fun updateQuantity(itemId: String, newQuantity: Int) {
        viewModelScope.launch {
            val request = com.example.myapplicationeasyaiorder.model.CartUpdateRequest(
                items = listOf(
                    com.example.myapplicationeasyaiorder.model.CartItemRequest(
                        upc = itemId,
                        quantity = newQuantity
                    )
                )
            )
            when (val result = repository.updateCart(request)) {
                is Resource.Success -> {
                    loadCart()
                }
                else -> {
                    // Log error
                }
            }
        }
    }
    fun updateQuantity(item: CartItem, newQuantity: Int) {
        updateQuantity(item.itemId, newQuantity)
    }

    fun removeItem(itemId: String) {
        val currentCart = (_cartState.value as? Resource.Success)?.data
        if (currentCart == null) {
            android.util.Log.e("CartViewModel", "Cannot remove item, cart not loaded")
            return
        }

        viewModelScope.launch {
            when (val result = repository.removeCartItem(currentCart.cartId, itemId)) {
                is Resource.Success -> {
                    loadCart()
                }
                else -> {
                    // Log error
                    android.util.Log.e("CartViewModel", "Failed to remove item $itemId")
                }
            }
        }
    }
}
