package com.example.myapplicationeasyaiorder

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplicationeasyaiorder.data.AuthState
import com.example.myapplicationeasyaiorder.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as androidx.navigation.fragment.NavHostFragment
        val navController = navHostFragment.navController
        val navView = binding.navView

        androidx.navigation.ui.NavigationUI.setupWithNavController(navView, navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.loginFragment -> navView.visibility = android.view.View.GONE
                else -> navView.visibility = android.view.View.VISIBLE
            }
        }

        // Observe session expiry and redirect to login
        AuthState.sessionExpired.observe(this) { expired ->
            if (expired) {
                Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_LONG).show()
                navController.navigate(R.id.loginFragment)
                AuthState.reset()
            }
        }
    }
}