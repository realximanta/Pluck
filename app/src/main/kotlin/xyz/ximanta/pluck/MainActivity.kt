package xyz.ximanta.pluck

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import xyz.ximanta.pluck.ui.MainViewModel
import xyz.ximanta.pluck.ui.PluckTheme
import xyz.ximanta.pluck.ui.navigation.PluckNavHost

class MainActivity : ComponentActivity() {

    private val loginViewModel: MainViewModel by viewModels {
        val app = application as PluckApplication
        MainViewModel.factory(app.apiClient, app.authStore)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PluckTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PluckNavHost(loginViewModel = loginViewModel)
                }
            }
        }
    }
}
