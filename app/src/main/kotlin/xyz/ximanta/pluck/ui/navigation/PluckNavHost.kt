package xyz.ximanta.pluck.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.ximanta.pluck.PluckApplication
import xyz.ximanta.pluck.ui.MainViewModel
import xyz.ximanta.pluck.ui.PluckColors
import xyz.ximanta.pluck.ui.components.AppShell
import xyz.ximanta.pluck.ui.screens.LoginScreen
import xyz.ximanta.pluck.ui.screens.PurchaseScreen
import xyz.ximanta.pluck.ui.viewmodel.HomeViewModel
import xyz.ximanta.pluck.ui.viewmodel.PurchaseViewModel

private const val ROUTE_LOADING = "loading"
private const val ROUTE_LOGIN = "login"
private const val ROUTE_PURCHASE = "purchase"
private const val ROUTE_HOME = "home"

@Composable
fun PluckNavHost(
    loginViewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as PluckApplication

    val purchaseViewModel: PurchaseViewModel = viewModel(
        factory = PurchaseViewModel.factory(app.apiClient, app.authStore)
    )
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(context, app.apiClient, app.authStore)
    )

    val authState by loginViewModel.authState.collectAsStateWithLifecycle()
    val loginState by loginViewModel.loginState.collectAsStateWithLifecycle()

    var route by rememberSaveable { mutableStateOf(ROUTE_LOADING) }

    LaunchedEffect(authState.loaded, authState.isLoggedIn) {
        if (!authState.loaded) return@LaunchedEffect
        when {
            route == ROUTE_LOADING -> route = if (authState.isLoggedIn) ROUTE_HOME else ROUTE_LOGIN
            route == ROUTE_LOGIN && authState.isLoggedIn -> route = ROUTE_HOME
            route == ROUTE_HOME && !authState.isLoggedIn -> route = ROUTE_LOGIN
        }
    }

    LaunchedEffect(route) {
        if (route == ROUTE_LOGIN) purchaseViewModel.clearAll()
    }

    BackHandler(enabled = route == ROUTE_PURCHASE) {
        purchaseViewModel.clearAll()
        route = ROUTE_LOGIN
    }

    when (route) {
        ROUTE_LOADING -> SplashScreen()
        ROUTE_LOGIN -> LoginScreen(
            loginState = loginState,
            onLogin = { loginViewModel.login(it) },
            onNavigateToPurchase = { route = ROUTE_PURCHASE },
            modifier = modifier
        )
        ROUTE_PURCHASE -> {
            val plansState by purchaseViewModel.plansState.collectAsStateWithLifecycle()
            val selectedPlan by purchaseViewModel.selectedPlan.collectAsStateWithLifecycle()
            val qrState by purchaseViewModel.qrState.collectAsStateWithLifecycle()
            val verifyState by purchaseViewModel.verifyState.collectAsStateWithLifecycle()
            val remaining by purchaseViewModel.remainingSeconds.collectAsStateWithLifecycle()

            PurchaseScreen(
                plansState = plansState,
                selectedPlan = selectedPlan,
                qrState = qrState,
                verifyState = verifyState,
                remainingSeconds = remaining,
                onBack = {
                    purchaseViewModel.clearAll()
                    route = ROUTE_LOGIN
                },
                onLoadPlans = { purchaseViewModel.loadPlans() },
                onSelectPlan = { purchaseViewModel.selectPlan(it) },
                onRequestQr = { purchaseViewModel.requestQr(it) },
                onVerify = { purchaseViewModel.verify() },
                onResetQr = { purchaseViewModel.resetQr() },
                onContinue = {
                    purchaseViewModel.clearAll()
                    route = ROUTE_HOME
                },
                modifier = modifier
            )
        }
        ROUTE_HOME -> AppShell(
            viewModel = homeViewModel,
            authState = authState,
            onSignOut = { loginViewModel.logout() },
            modifier = modifier
        )
    }
}

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(PluckColors.Slate950),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(64.dp).background(PluckColors.Emerald500, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("P", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = PluckColors.Slate950)
            }
            CircularProgressIndicator(
                color = PluckColors.Emerald500,
                strokeWidth = 2.dp,
                modifier = Modifier.padding(top = 24.dp).size(24.dp)
            )
        }
    }
}
