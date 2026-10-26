package xyz.ximanta.pluck

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.ximanta.pluck.data.AuthState
import xyz.ximanta.pluck.ui.LoginState
import xyz.ximanta.pluck.ui.MainViewModel
import xyz.ximanta.pluck.ui.PluckColors
import xyz.ximanta.pluck.ui.PluckTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
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
                    PluckRoot(viewModel)
                }
            }
        }
    }
}

@Composable
private fun PluckRoot(viewModel: MainViewModel) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    if (!authState.loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PluckColors.Emerald500)
        }
        return
    }

    if (!authState.isLoggedIn) {
        LoginPlaceholder(viewModel)
    } else {
        HomePlaceholder(viewModel, authState)
    }
}

@Composable
private fun LoginPlaceholder(viewModel: MainViewModel) {
    var key by remember { mutableStateOf("") }
    val loginState by viewModel.loginState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Pluck",
            style = MaterialTheme.typography.displayMedium,
            color = PluckColors.Emerald400
        )
        Text(
            text = "Enter your key",
            style = MaterialTheme.typography.bodyMedium,
            color = PluckColors.Slate400,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )
        OutlinedTextField(
            value = key,
            onValueChange = { key = it.uppercase() },
            label = { Text("API key") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { viewModel.login(key) },
            enabled = key.length == 10 && loginState !is LoginState.Loading,
            colors = ButtonDefaults.buttonColors(containerColor = PluckColors.Emerald500),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            if (loginState is LoginState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(4.dp),
                    color = PluckColors.Slate950
                )
            } else {
                Text("Sign in")
            }
        }
        val err = (loginState as? LoginState.Error)?.message
        if (!err.isNullOrBlank()) {
            Text(
                text = err,
                color = PluckColors.Red400,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun HomePlaceholder(viewModel: MainViewModel, authState: AuthState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome${if (authState.name.isNotBlank()) ", ${authState.name}" else ""}",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Home screen — coming next",
            style = MaterialTheme.typography.bodyMedium,
            color = PluckColors.Slate400,
            modifier = Modifier.padding(top = 8.dp)
        )
        Button(
            onClick = { viewModel.logout() },
            colors = ButtonDefaults.buttonColors(containerColor = PluckColors.Slate700),
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text("Sign out")
        }
    }
}
