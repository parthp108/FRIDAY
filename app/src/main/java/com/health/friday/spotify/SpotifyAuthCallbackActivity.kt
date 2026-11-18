package com.health.friday.spotify

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.health.friday.FridayApplication
import kotlinx.coroutines.launch

class SpotifyAuthCallbackActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val callback = intent?.data
        if (callback == null) {
            finish()
            return
        }

        val auth = (application as FridayApplication).container.spotifyAuth
        lifecycleScope.launch {
            val result = auth.completeLogin(callback)
            Toast.makeText(applicationContext, result.message, Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
