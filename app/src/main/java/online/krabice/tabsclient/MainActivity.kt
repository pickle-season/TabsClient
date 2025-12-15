@file:OptIn(InternalSerializationApi::class)

package online.krabice.tabsclient

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

import online.krabice.tabsclient.ui.theme.TabsClientTheme

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.statement.HttpResponse
import io.ktor.client.request.*
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.network.UnresolvedAddressException

import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import androidx.core.content.edit
import io.ktor.http.URLParserException
import java.net.ConnectException



fun encryptedPrefs(context: Context): SharedPreferences {
    // TODO: Maybe do something with this deprecation
    //  after google releases a built in solution
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    return EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

fun saveCredentials(
    context: Context,
    username: String,
    password: String,
    serverUrl: String,
) {
    val prefs = encryptedPrefs(context)
    prefs.edit {
        putString("username", username)
            .putString("password", password)
            .putString("serverUrl", serverUrl)
    }
}
fun loadCredentials(context: Context): Triple<String, String, String> {
    // TODO: BuildConfig is for debug only, remove later
    val prefs = encryptedPrefs(context)
    val username = prefs.getString("username", "") ?: ""
    val password = prefs.getString("password", "") ?: ""
    val serverUrl = prefs.getString("serverUrl", "") ?: ""

    return Triple(username, password, serverUrl)
}

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"

    private var isRefreshing by mutableStateOf(false)

    private var songs by mutableStateOf<List<Song>>(emptyList())



    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
        install(HttpTimeout)
    }

    suspend fun getRequest(url: String): HttpResponse? {
        val (_, _, serverUrl) = loadCredentials(this)

        Log.i(TAG, "Getting $serverUrl/$url")
        try {
            val response = client.get("$serverUrl/$url")

            if (response.status != HttpStatusCode.OK) {
                Log.e(TAG, "Non-OK HTTP status: ${response.status.value}, ${response.status.description}, detail: ${response.body<ErrorResponse>().detail}")
                return null
            }
            return response

        } catch (e: UnresolvedAddressException) {
            Toast.makeText(
                this,
                "ERROR: Invalid server URL",
                Toast.LENGTH_SHORT
            ).show()
            Log.e(TAG, "Unresolved address exception")
            return null
        } catch (e: ConnectException) {
            Toast.makeText(
                this,
                "ERROR: Server refused connection",
                Toast.LENGTH_SHORT
            ).show()
            Log.e(TAG, "Connect exception")
            return null
        } catch (e: URLParserException) {
            Toast.makeText(
                this,
                "ERROR: Invalid server URL",
                Toast.LENGTH_SHORT
            ).show()
            Log.e(TAG, "URL parser exception")
            return null
        }
    }

    suspend fun updateSavedSongs() {
        isRefreshing = true

        songs = getRequest("saved_songs")?.body() ?: emptyList()

        isRefreshing = false
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        lifecycleScope.launch {
            updateSavedSongs()
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TabsClientTheme {
                TabsClientApp(
                    songs,
                    isRefreshing,
                    {
                        lifecycleScope.launch { updateSavedSongs() }
                    },
                )
            }
        }
    }
}

