package com.example.argus

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.example.argus.core.biometric.BiometricPromptManager
import com.example.argus.theme.ArgusTheme
import com.example.argus.ui.navigation.ArgusNavGraph

class MainActivity : FragmentActivity() {
    lateinit var biometricManager: BiometricPromptManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as ArgusApplication).container
        biometricManager = BiometricPromptManager(this)

        setContent {
            ArgusTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ArgusNavGraph(
                        container = appContainer,
                        biometricManager = biometricManager
                    )
                }
            }
        }
    }
}
