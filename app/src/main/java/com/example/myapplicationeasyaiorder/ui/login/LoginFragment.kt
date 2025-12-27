package com.example.myapplicationeasyaiorder.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myapplicationeasyaiorder.databinding.FragmentLoginBinding
import com.example.myapplicationeasyaiorder.data.KrogerAuthManager
import com.example.myapplicationeasyaiorder.ui.EasyOrderViewModelFactory
import androidx.navigation.fragment.findNavController

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels {
        EasyOrderViewModelFactory(requireContext())
    }
    
    // AppAuth Intent Launcher
    private val authLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val response = AuthorizationResponse.fromIntent(data)
            val exception = AuthorizationException.fromIntent(data)
            
            if (response != null) {
                // Exchange code for token
                // We need the secret from BuildConfig
                val secret = com.example.myapplicationeasyaiorder.BuildConfig.KROGER_CLIENT_SECRET
                viewModel.handleAuthResponse(response, secret)
            } else {
                Toast.makeText(context, "Auth Failed: ${exception?.message}", Toast.LENGTH_LONG).show()
                viewModel.resetLoginState()
            }
        } else {
             viewModel.resetLoginState()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Check if already logged in
        val authManager = KrogerAuthManager(requireContext())
        if (!authManager.getToken().isNullOrEmpty()) {
            // Already have a token, skip login
            findNavController().navigate(com.example.myapplicationeasyaiorder.R.id.chatFragment)
            return
        }

        binding.loginButton.setOnClickListener {
            val clientId = com.example.myapplicationeasyaiorder.BuildConfig.KROGER_CLIENT_ID
            
            if (!clientId.isNullOrEmpty()) {
                val intent = viewModel.getAuthIntent(clientId)
                authLauncher.launch(intent)
            } else {
                Toast.makeText(context, "Missing Client ID!", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.loginState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is LoginState.Loading -> {
                    binding.loadingBar.visibility = View.VISIBLE
                    binding.loginButton.isEnabled = false
                }
                is LoginState.Success -> {
                    binding.loadingBar.visibility = View.GONE
                    binding.loginButton.isEnabled = true
                    Toast.makeText(context, "Connected Successfully!", Toast.LENGTH_SHORT).show()
                    findNavController().navigate(com.example.myapplicationeasyaiorder.R.id.cartFragment)
                }
                is LoginState.Error -> {
                    binding.loadingBar.visibility = View.GONE
                    binding.loginButton.isEnabled = true
                    Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                }
                else -> {
                     binding.loadingBar.visibility = View.GONE
                     binding.loginButton.isEnabled = true
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
