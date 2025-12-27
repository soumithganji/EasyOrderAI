package com.example.myapplicationeasyaiorder.data

import android.content.Context
import android.net.Uri
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthState
import net.openid.appauth.TokenRequest

class KrogerAuthManager(context: Context) {
    private val prefs = context.getSharedPreferences("kroger_auth", Context.MODE_PRIVATE)
    private val authService = AuthorizationService(context)

    companion object {
        private const val AUTH_ENDPOINT = "https://api.kroger.com/v1/connect/oauth2/authorize"
        private const val TOKEN_ENDPOINT = "https://api.kroger.com/v1/connect/oauth2/token"
        private const val REDIRECT_URI = "com.example.myapplicationeasyaiorder://oauth2redirect"
    }
    
    fun getAuthRequest(clientId: String): AuthorizationRequest {
        val serviceConfig = AuthorizationServiceConfiguration(
            Uri.parse(AUTH_ENDPOINT),
            Uri.parse(TOKEN_ENDPOINT)
        )

        return AuthorizationRequest.Builder(
            serviceConfig,
            clientId,
            ResponseTypeValues.CODE,
            Uri.parse(REDIRECT_URI)
        )
            .setScopes("profile.compact", "product.compact", "cart.basic:write") 
            // AppAuth handles code verifier automatically
            .build()
    }

    fun getAuthIntent(clientId: String): android.content.Intent {
        val request = getAuthRequest(clientId)
        return authService.getAuthorizationRequestIntent(request)
    }
    
    fun performTokenRequest(
        tokenRequest: TokenRequest,
        clientSecret: String, // Secret needed for confidential clients? Kroger uses client_credentials or auth code. 
        // For Android AppAuth with Auth Code, we usually don't send secret if it's a public client.
        // If Kroger REQUIRES secret even for Auth Code flow from mobile, that's unusual (and insecure), but common in enterprise.
        // Based on user snippet: "Authentication: Basic base64(client_id:secret)" implies we need to authenticate the token endpoint call.
        callback: (String?, Throwable?) -> Unit
    ) {
         // AppAuth supports ClientSecretBasic or ClientSecretPost.
         // We'll configure ClientSecretBasic
         val clientAuth = net.openid.appauth.ClientSecretBasic(clientSecret)
         
         authService.performTokenRequest(tokenRequest, clientAuth) { response, ex ->
             if (response != null) {
                 android.util.Log.d("KrogerAuthManager", "Token Response Scope: ${response.scope}")
                 android.util.Log.d("KrogerAuthManager", "Token Response: $response")
                 val accessToken = response.accessToken
                 if (accessToken != null) {
                     saveToken(accessToken)
                     callback(accessToken, null)
                 } else {
                     callback(null, Exception("Access Token is null"))
                 }
             } else {
                 callback(null, ex)
             }
         }
    }

    fun saveToken(token: String) {
        prefs.edit().putString("access_token", token).apply()
    }

    fun getToken(): String? {
        return prefs.getString("access_token", null)
    }

    fun clearToken() {
        prefs.edit().remove("access_token").apply()
    }
    
    fun dispose() {
        authService.dispose()
    }
}
