package xyz.ximanta.pluck.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.ximanta.pluck.ui.LoginState
import xyz.ximanta.pluck.ui.PluckColors

@Composable
fun LoginScreen(
    loginState: LoginState,
    onLogin: (String) -> Unit,
    onNavigateToPurchase: () -> Unit,
    modifier: Modifier = Modifier
) {
    var keyInput by remember { mutableStateOf("") }
    val isLoading = loginState is LoginState.Loading
    val errorMessage = (loginState as? LoginState.Error)?.message
    val canSubmit = keyInput.length == 10 && !isLoading

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        PluckColors.Slate950,
                        PluckColors.Slate900.copy(alpha = 0.6f),
                        PluckColors.Slate950
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))

        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    color = PluckColors.Emerald500,
                    shape = RoundedCornerShape(20.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "P",
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = PluckColors.Slate950
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Pluck",
            fontSize = 40.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            color = PluckColors.Slate100,
            letterSpacing = (-0.5).sp
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Pull any media from anywhere",
            fontSize = 14.sp,
            color = PluckColors.Slate400,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(56.dp))

        Text(
            text = "ENTER YOUR KEY",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = PluckColors.Slate500,
            letterSpacing = 1.5.sp
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = keyInput,
            onValueChange = { raw ->
                keyInput = raw.uppercase().filter { it.isLetterOrDigit() }.take(10)
            },
            enabled = !isLoading,
            placeholder = {
                Text(
                    text = "XXXXXXXXXX",
                    color = PluckColors.Slate600,
                    letterSpacing = 4.sp,
                    fontFamily = FontFamily.Monospace
                )
            },
            textStyle = TextStyle(
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 4.sp,
                color = PluckColors.Slate100,
                textAlign = TextAlign.Center
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrect = false,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (canSubmit) onLogin(keyInput) }
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PluckColors.Emerald500,
                unfocusedBorderColor = PluckColors.Slate700,
                focusedContainerColor = PluckColors.Slate900,
                unfocusedContainerColor = PluckColors.Slate900,
                cursorColor = PluckColors.Emerald400
            )
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "${keyInput.length} / 10",
            fontSize = 11.sp,
            color = if (keyInput.length == 10) PluckColors.Emerald400 else PluckColors.Slate600,
            fontFamily = FontFamily.Monospace
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { onLogin(keyInput) },
            enabled = canSubmit,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PluckColors.Emerald500,
                contentColor = PluckColors.Slate950,
                disabledContainerColor = PluckColors.Slate800,
                disabledContentColor = PluckColors.Slate600
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = PluckColors.Slate950,
                    strokeWidth = 2.5.dp
                )
                Spacer(Modifier.width(10.dp))
                Text("Signing in…", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Text("Sign in", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            Surface(
                color = PluckColors.Red900.copy(alpha = 0.3f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMessage,
                    color = PluckColors.Red400,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "No key yet?",
                fontSize = 13.sp,
                color = PluckColors.Slate500
            )
            Spacer(Modifier.width(6.dp))
            TextButton(
                onClick = onNavigateToPurchase,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Buy a plan",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PluckColors.Emerald400
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
