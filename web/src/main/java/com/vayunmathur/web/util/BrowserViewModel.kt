package com.vayunmathur.web.util

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vayunmathur.web.data.BrowserDatabase
import com.vayunmathur.web.data.HistoryDao
import com.vayunmathur.web.data.HistoryEntry
import com.vayunmathur.web.data.Tab
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.StorageController
import java.util.UUID

class BrowserViewModel(
    application: Application,
    private val historyDao: HistoryDao
) : AndroidViewModel(application) {

    val runtime: GeckoRuntime by lazy { GeckoRuntime.create(application) }

    private val _tabs = mutableStateListOf<Tab>()
    val tabs: List<Tab> = _tabs

    var activeTabIndex by mutableIntStateOf(0)
        private set

    val activeTab: Tab? get() = _tabs.getOrNull(activeTabIndex)

    private val sessions = mutableMapOf<String, GeckoSession>()

    var currentUrl by mutableStateOf("")
        private set

    var currentTitle by mutableStateOf("")
        private set

    var isLoading by mutableStateOf(false)
        private set

    var canGoBack by mutableStateOf(false)
        private set

    var canGoForward by mutableStateOf(false)
        private set

    var progress by mutableFloatStateOf(0f)
        private set

    init {
        createTab(url = HOME_URL)
    }

    fun createTab(url: String = HOME_URL, isIncognito: Boolean = false) {
        val tab = Tab(
            id = UUID.randomUUID().toString(),
            isIncognito = isIncognito
        )
        _tabs.add(tab)
        activeTabIndex = _tabs.lastIndex

        val session = createSession(tab.id, isIncognito)
        session.loadUri(url)
        currentUrl = url
        currentTitle = "New Tab"
        canGoBack = false
        canGoForward = false
    }

    private fun createSession(tabId: String, isIncognito: Boolean): GeckoSession {
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(isIncognito)
            .build()

        val session = GeckoSession(settings)
        session.open(runtime)

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                url?.let {
                    if (_tabs.getOrNull(activeTabIndex)?.id == tabId) {
                        currentUrl = it
                    }
                    updateTab(tabId) { copy(url = it) }
                }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                if (_tabs.getOrNull(activeTabIndex)?.id == tabId) {
                    this@BrowserViewModel.canGoBack = canGoBack
                }
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                if (_tabs.getOrNull(activeTabIndex)?.id == tabId) {
                    this@BrowserViewModel.canGoForward = canGoForward
                }
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {}

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                if (_tabs.getOrNull(activeTabIndex)?.id == tabId) {
                    isLoading = true
                    progress = 0f
                }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                if (_tabs.getOrNull(activeTabIndex)?.id == tabId) {
                    isLoading = false
                    progress = 1f
                }
                val tab = _tabs.firstOrNull { it.id == tabId }
                if (success && tab != null && !tab.isIncognito) {
                    val tabUrl = tab.url
                    val tabTitle = tab.title
                    if (tabUrl.isNotBlank() && tabUrl != "about:blank") {
                        viewModelScope.launch {
                            historyDao.insert(
                                HistoryEntry(
                                    url = tabUrl,
                                    title = tabTitle.ifBlank { tabUrl },
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
            }

            override fun onProgressChange(session: GeckoSession, progressValue: Int) {
                if (_tabs.getOrNull(activeTabIndex)?.id == tabId) {
                    progress = progressValue / 100f
                }
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                title?.let {
                    if (_tabs.getOrNull(activeTabIndex)?.id == tabId) {
                        currentTitle = it
                    }
                    updateTab(tabId) { copy(title = it) }
                }
            }
        }

        sessions[tabId] = session
        return session
    }

    fun getActiveSession(): GeckoSession? = activeTab?.let { sessions[it.id] }

    fun switchTab(index: Int) {
        if (index in _tabs.indices) {
            activeTabIndex = index
            val tab = _tabs[index]
            currentUrl = tab.url
            currentTitle = tab.title
            canGoBack = false
            canGoForward = false
        }
    }

    fun closeTab(tabId: String) {
        val index = _tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return

        sessions[tabId]?.close()
        sessions.remove(tabId)
        _tabs.removeAt(index)

        if (_tabs.isEmpty()) {
            createTab()
        } else {
            activeTabIndex = index.coerceAtMost(_tabs.lastIndex)
            val tab = _tabs[activeTabIndex]
            currentUrl = tab.url
            currentTitle = tab.title
        }
    }

    fun navigate(input: String) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return

        val url = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "$SEARCH_URL${Uri.encode(trimmed)}"
        }
        getActiveSession()?.loadUri(url)
    }

    fun goBack() { getActiveSession()?.goBack() }
    fun goForward() { getActiveSession()?.goForward() }
    fun reload() { getActiveSession()?.reload() }
    fun stop() { getActiveSession()?.stop() }

    fun clearHistory() {
        viewModelScope.launch { historyDao.deleteAll() }
    }

    fun clearBrowsingData() {
        runtime.storageController.clearData(StorageController.ClearFlags.ALL)
        clearHistory()
    }

    val history = historyDao.getAll()

    private fun updateTab(tabId: String, update: Tab.() -> Tab) {
        val index = _tabs.indexOfFirst { it.id == tabId }
        if (index >= 0) {
            _tabs[index] = _tabs[index].update()
        }
    }

    override fun onCleared() {
        sessions.values.forEach { it.close() }
        sessions.clear()
        runtime.shutdown()
    }

    companion object {
        const val HOME_URL = "about:blank"
        const val SEARCH_URL = "https://duckduckgo.com/?q="
    }
}

class BrowserViewModelFactory(
    private val application: Application,
    private val db: BrowserDatabase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return BrowserViewModel(application, db.historyDao()) as T
    }
}
