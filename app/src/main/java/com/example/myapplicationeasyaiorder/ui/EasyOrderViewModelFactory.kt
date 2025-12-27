package com.example.myapplicationeasyaiorder.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myapplicationeasyaiorder.data.KrogerAuthManager
import com.example.myapplicationeasyaiorder.ui.login.LoginViewModel
import com.example.myapplicationeasyaiorder.ui.cart.CartViewModel

class EasyOrderViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            val authManager = KrogerAuthManager(context)
            return LoginViewModel(authManager) as T
        }
        if (modelClass.isAssignableFrom(CartViewModel::class.java)) {
            val authManager = KrogerAuthManager(context)
            return CartViewModel(com.example.myapplicationeasyaiorder.data.CartRepository(authManager)) as T
        }
        if (modelClass.isAssignableFrom(com.example.myapplicationeasyaiorder.ui.chat.ChatViewModel::class.java)) {
            val apiKey = com.example.myapplicationeasyaiorder.BuildConfig.NVIDIA_API_KEY
            val authManager = KrogerAuthManager(context)
            val aiRepo = com.example.myapplicationeasyaiorder.data.AiRepositoryImpl(apiKey)
            // Inject ProductRepository with AI capabilities into ChatViewModel
            val productRepo = com.example.myapplicationeasyaiorder.data.ProductRepository(authManager, aiRepo)
            val cartRepo = com.example.myapplicationeasyaiorder.data.CartRepository(authManager)
            return com.example.myapplicationeasyaiorder.ui.chat.ChatViewModel(
                aiRepo,
                productRepo,
                cartRepo
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
