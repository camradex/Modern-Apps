package com.vayunmathur.email

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vayunmathur.library.ui.DynamicTheme
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom

class MainActivity : ComponentActivity() {
    private val scope = CoroutineScope(Dispatchers.Main)

    // Configuration Constants
    private val clientId = "827025129169-1ihnv9r1a8nd1i3qjs98tkvluo4vjbhe.apps.googleusercontent.com"
    private val redirectUri = "com.googleusercontent.apps.827025129169-1ihnv9r1a8nd1i3qjs98tkvluo4vjbhe:/oauth2redirect"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            DynamicTheme {
                MainContent(onGoogleLogin = { startGoogleLogin() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data
        val expectedScheme = "com.googleusercontent.apps.827025129169-1ihnv9r1a8nd1i3qjs98tkvluo4vjbhe"
        if (data != null && data.scheme == expectedScheme && data.path == "/oauth2redirect") {
            val code = data.getQueryParameter("code")
            if (code != null) {
                exchangeCodeForToken(code)
            }
        }
    }

    private fun startGoogleLogin() {
        val verifier = generateCodeVerifier()
        TokenState.codeVerifier = verifier
        val challenge = generateCodeChallenge(verifier)

        val authUri = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth")
            .buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", "https://mail.google.com/ email profile")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("access_type", "offline")
            .build()

        startActivity(Intent(Intent.ACTION_VIEW, authUri))
    }

    private fun exchangeCodeForToken(code: String) {
        val verifier = TokenState.codeVerifier ?: return

        scope.launch {
            try {
                HttpClient(CIO) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }.use { client ->
                    val httpResponse = client.submitForm(
                        url = "https://oauth2.googleapis.com/token",
                        formParameters = parameters {
                            append("client_id", clientId)
                            append("client_secret", clientSecret)
                            append("code", code)
                            append("code_verifier", verifier)
                            append("grant_type", "authorization_code")
                            append("redirect_uri", redirectUri)
                        }
                    )

                    if (httpResponse.status.isSuccess()) {
                        val response: TokenResponse = httpResponse.body()
                        
                        // Fetch user info to get email address
                        val userInfo: UserInfo = client.get("https://www.googleapis.com/oauth2/v3/userinfo") {
                            bearerAuth(response.accessToken)
                        }.body()

                        TokenState.userEmail = userInfo.email
                        TokenState.accessToken = response.accessToken
                    } else {
                        val errorText = httpResponse.bodyAsText()
                        android.util.Log.e("OAuthError", "Failed to exchange code: $errorText")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun generateCodeVerifier(): String {
        val sr = SecureRandom()
        val code = ByteArray(32)
        sr.nextBytes(code)
        return Base64.encodeToString(code, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING).trim()
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        md.update(bytes)
        val digest = md.digest()
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING).trim()
    }
}

object TokenState {
    var accessToken by mutableStateOf<String?>(null)
    var userEmail by mutableStateOf<String?>(null)
    var codeVerifier: String? = null
}

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("scope") val scope: String,
    @SerialName("token_type") val tokenType: String
)

@Serializable
data class UserInfo(
    val email: String,
    val name: String? = null,
    val picture: String? = null
)

@Composable
fun MainContent(onGoogleLogin: () -> Unit) {
    if (TokenState.accessToken == null || TokenState.userEmail == null) {
        LoginScreen(onGoogleLogin)
    } else {
        InboxScreen(
            accessToken = TokenState.accessToken!!,
            userEmail = TokenState.userEmail!!
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onGoogleLogin: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Email") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome to Email",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            Button(
                onClick = onGoogleLogin,
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Sign in with Google")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(accessToken: String, userEmail: String) {
    val emailManager = remember { EmailManager() }
    var subjects by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(accessToken, userEmail) {
        emailManager.fetchLatestSubjects(
            host = "imap.gmail.com",
            user = userEmail,
            auth = EmailManager.AuthType.OAuth2(accessToken),
            onSuccess = {
                subjects = it
                isLoading = false
            },
            onError = {
                errorMessage = it.message ?: "Unknown error"
                isLoading = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inbox") },
                actions = {
                    IconButton(onClick = { TokenState.accessToken = null }) {
                        Text("Logout", style = MaterialTheme.typography.labelSmall)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(subjects) { subject ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = subject,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
