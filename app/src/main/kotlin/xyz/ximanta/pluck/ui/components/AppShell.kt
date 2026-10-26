package xyz.ximanta.pluck.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import xyz.ximanta.pluck.data.AuthState
import xyz.ximanta.pluck.data.HistoryEntry
import xyz.ximanta.pluck.ui.PluckColors
import xyz.ximanta.pluck.ui.screens.HistoryScreen
import xyz.ximanta.pluck.ui.screens.HomeScreen
import xyz.ximanta.pluck.ui.viewmodel.HomeFetchState
import xyz.ximanta.pluck.ui.viewmodel.HomeHistoryState
import xyz.ximanta.pluck.ui.viewmodel.HomeResolveState
import xyz.ximanta.pluck.ui.viewmodel.HomeViewModel
import xyz.ximanta.pluck.ui.viewmodel.SelectedFormat
import xyz.ximanta.pluck.data.MediaFormat

@Composable
fun AppShell(
    viewModel: HomeViewModel,
    authState: AuthState,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val urlInput by viewModel.urlInput.collectAsStateWithLifecycle()
    val resolveState by viewModel.resolveState.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val fetchState by viewModel.fetchState.collectAsStateWithLifecycle()
    val historyState by viewModel.historyState.collectAsStateWithLifecycle()
    val drawerOpen by viewModel.drawerOpen.collectAsStateWithLifecycle()

    LaunchedEffect(drawerOpen) {
        if (drawerOpen && drawerState.isClosed) drawerState.open()
        else if (!drawerOpen && drawerState.isOpen) drawerState.close()
    }

    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Open && !drawerOpen) {
            viewModel.setDrawerOpen(true)
            if (historyState is HomeHistoryState.Idle) viewModel.refreshHistory()
        } else if (drawerState.currentValue == DrawerValue.Closed && drawerOpen) {
            viewModel.setDrawerOpen(false)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        scrimColor = PluckColors.Slate950.copy(alpha = 0.6f),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = PluckColors.Slate950,
                drawerShape = androidx.compose.foundation.shape.RoundedCornerShape(
                    topEnd = 20.dp, bottomEnd = 20.dp
                ),
                modifier = Modifier.width(310.dp)
            ) {
                HistoryScreen(
                    authState = authState,
                    historyState = historyState,
                    onRefresh = { viewModel.refreshHistory() },
                    onEntryClick = { entry: HistoryEntry ->
                        viewModel.loadHistoryFromEntry(entry)
                        scope.launch { drawerState.close() }
                    },
                    onSignOut = {
                        scope.launch { drawerState.close() }
                        onSignOut()
                    }
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) {
        HomeScreen(
            urlInput = urlInput,
            resolveState = resolveState,
            selected = selected,
            fetchState = fetchState,
            onUrlChange = viewModel::onUrlChange,
            onResolve = viewModel::resolve,
            onSelectFormat = { format: MediaFormat, mediaType: String ->
                viewModel.selectFormat(format, mediaType)
            },
            onClearSelection = viewModel::clearSelection,
            onDownload = viewModel::download,
            onDismissFetch = viewModel::dismissFetch,
            onOpenDrawer = { scope.launch { drawerState.open() } }
        )
    }
}
