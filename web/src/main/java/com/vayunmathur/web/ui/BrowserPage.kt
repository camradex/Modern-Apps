package com.vayunmathur.web.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.vayunmathur.library.ui.IconForward
import com.vayunmathur.library.ui.IconMenu
import com.vayunmathur.library.ui.IconSearch
import com.vayunmathur.library.util.NavBackStack
import com.vayunmathur.web.Route
import com.vayunmathur.web.util.BrowserViewModel
import org.mozilla.geckoview.GeckoView

@Composable
fun BrowserPage(
    viewModel: BrowserViewModel,
    backStack: NavBackStack<Route>
) {
    BackHandler(enabled = viewModel.canGoBack) {
        viewModel.goBack()
    }

    val isNewTab = viewModel.currentUrl == "about:blank" || viewModel.currentUrl.isBlank()
    val displayUrl = if (isNewTab) "" else viewModel.currentUrl

    Column(modifier = Modifier.fillMaxSize()) {
        BrowserToolbar(
            currentUrl = displayUrl,
            tabCount = viewModel.tabs.size,
            isIncognito = viewModel.activeTab?.isIncognito == true,
            canGoForward = viewModel.canGoForward,
            onNavigate = { viewModel.navigate(it) },
            onForward = { viewModel.goForward() },
            onOpenTabs = { backStack.add(Route.Tabs) },
            onNewTab = { viewModel.createTab() },
            onNewIncognitoTab = { viewModel.createTab(isIncognito = true) },
            onOpenHistory = { backStack.add(Route.History) }
        )

        AnimatedVisibility(visible = viewModel.isLoading) {
            LinearProgressIndicator(
                progress = { viewModel.progress },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (isNewTab) {
            NewTabContent(onSearch = { viewModel.navigate(it) })
        } else {
            val activeSession = viewModel.getActiveSession()
            if (activeSession != null) {
                AndroidView(
                    factory = { context -> GeckoView(context) },
                    update = { view ->
                        val session = viewModel.getActiveSession()
                        if (session != null && view.session != session) {
                            if (view.session != null) view.releaseSession()
                            view.setSession(session)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NewTabContent(onSearch: (String) -> Unit) {
    var searchText by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            placeholder = { Text("Search with DuckDuckGo") },
            leadingIcon = { IconSearch() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (searchText.isNotBlank()) {
                    onSearch(searchText)
                    focusManager.clearFocus()
                }
            })
        )
    }
}

@Composable
private fun BrowserToolbar(
    currentUrl: String,
    tabCount: Int,
    isIncognito: Boolean,
    canGoForward: Boolean,
    onNavigate: (String) -> Unit,
    onForward: () -> Unit,
    onOpenTabs: () -> Unit,
    onNewTab: () -> Unit,
    onNewIncognitoTab: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var urlFieldValue by remember(currentUrl) {
        mutableStateOf(TextFieldValue(currentUrl))
    }
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        color = if (isIncognito) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = urlFieldValue,
                onValueChange = { urlFieldValue = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            urlFieldValue = urlFieldValue.copy(
                                selection = TextRange(0, urlFieldValue.text.length)
                            )
                        }
                    },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                placeholder = { Text("Search or enter URL") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    onNavigate(urlFieldValue.text)
                    focusManager.clearFocus()
                }),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            IconButton(onClick = onOpenTabs) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = tabCount.toString(),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    IconMenu()
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Forward") },
                        leadingIcon = { IconForward() },
                        enabled = canGoForward,
                        onClick = { showMenu = false; onForward() }
                    )
                    DropdownMenuItem(
                        text = { Text("New tab") },
                        onClick = { showMenu = false; onNewTab() }
                    )
                    DropdownMenuItem(
                        text = { Text("New incognito tab") },
                        onClick = { showMenu = false; onNewIncognitoTab() }
                    )
                    DropdownMenuItem(
                        text = { Text("History") },
                        onClick = { showMenu = false; onOpenHistory() }
                    )
                }
            }
        }
    }
}
