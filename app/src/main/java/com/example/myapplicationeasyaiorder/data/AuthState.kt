package com.example.myapplicationeasyaiorder.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * Singleton to broadcast auth state changes across the app.
 */
object AuthState {
    private val _sessionExpired = MutableLiveData<Boolean>(false)
    val sessionExpired: LiveData<Boolean> = _sessionExpired

    fun notifySessionExpired() {
        _sessionExpired.postValue(true)
    }

    fun reset() {
        _sessionExpired.value = false
    }
}
