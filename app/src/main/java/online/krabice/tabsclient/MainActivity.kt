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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

import online.krabice.tabsclient.ui.theme.TabsClientTheme
import online.krabice.tabsclient.BuildConfig

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.statement.HttpResponse
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.network.UnresolvedAddressException

import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.UnknownHostException
import androidx.core.content.edit
import io.ktor.client.plugins.timeout
import io.ktor.http.URLParserException
import java.net.ConnectException


@Serializable
data class Chords(
    val id: Int,
    val url: String,
    val version: Int,
    val content: String,

    @SerialName("song_id")
    val songId: Int,
)

@Serializable
data class Tab(
    val id: Int,
    val url: String,
    val version: Int,
    val bass: Boolean,
    val content: String,

    @SerialName("song_id")
    val songId: Int,
)

@Serializable
data class Song(
    val id: Int,
    val artist: String,
    val title: String,
    val chords: List<Chords>,
    val tabs: List<Tab>,
)

@Serializable
data class ContentResponse(
    val content: String
)

@Serializable
data class ErrorResponse(
    val detail: String
)

fun encryptedPrefs(context: Context): SharedPreferences {
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

@Composable
fun TabsClientApp(
    songs: List<Song>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var selectedSong by rememberSaveable { mutableStateOf<Song?>(null) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            it.icon,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it; selectedSong = null }
                )
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize() ) {
            when {
                selectedSong != null -> SongDetailScreen(
                    selectedSong!!,
                    { selectedSong = null },
                )

                else -> when (currentDestination) {
                    AppDestinations.HOME -> SongListScreen(songs, isRefreshing, onRefresh) {
                        selectedSong = it
                    }

                    AppDestinations.FAVORITES -> Text("Favourites")
                    AppDestinations.PROFILE -> ProfileScreen(
                    )
                }
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Default.Home),
    FAVORITES("Favorites", Icons.Default.Favorite),
    PROFILE("Profile", Icons.Default.AccountBox),
}

@Composable
fun SongListScreen(
    songs: List<Song>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onSongClick: (Song) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 30.dp),
                contentPadding = PaddingValues(top = 40.dp)
            ) {
                if (songs.isEmpty() && !isRefreshing) {
                    item {Text("No songs found", modifier = Modifier.padding(16.dp))}
                } else {
                    items(songs) { song ->
                        SongRow(song) { onSongClick(song) }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SongRow(song: Song, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }, // ripple automatically applied
        color = MaterialTheme.colorScheme.surface, // optional background
        tonalElevation = 1.dp // optional subtle shadow
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
fun ProfileScreen(
) {
    val context = LocalContext.current

    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var serverUrl by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val (u, p, s) = loadCredentials(context)
        username = u
        password = p
        serverUrl = s
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        item {
            OutlinedTextField(
                value=username,
                onValueChange = {
                    username = it
                    saveCredentials(
                        context,
                        username,
                        password,
                        serverUrl
                    )},
                label = { Text("Username") }
            )
            OutlinedTextField(
                value=password,
                onValueChange = {
                    password = it
                    saveCredentials(
                        context,
                        username,
                        password,
                        serverUrl
                    )},
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                )
            )
            OutlinedTextField(
                value=serverUrl,
                onValueChange = {
                    serverUrl = it
                    saveCredentials(
                        context,
                        username,
                        password,
                        serverUrl
                    )},
                label = { Text("Server URL") }
            )

            // TODO: Implement onClick
            Button({}) {
                Text("Update server cache")
            }
        }
    }
}

@Composable
fun SongDetailScreen(song: Song, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var chordsText by rememberSaveable { mutableStateOf<String?>(null) }
    var isLoading by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(song.id) {
        isLoading = true
        //chordsText = getChords(song.chords.first().id)
        // TODO: Temporarily always first chord, later add selection of tab/chords and version
        chordsText = song.chords.first().content
        isLoading = false
    }

    val scrollState = rememberScrollState()

    Column(modifier = Modifier
        .padding(top = 16.dp, start = 16.dp, end = 16.dp)
        .verticalScroll(scrollState)
        .fillMaxSize()
    ) {
        Text("${song.title} - ${song.artist}", modifier = Modifier.padding(30.dp))

        when {
            isLoading -> Text("Loading chords…")
            chordsText != null -> {
                Text(
                    // TODO: Temporary replacement,
                    //  figure out actual chord rendering'

                    // TODO: Chords not correctly aligned sometimes
                    chordsText!!
                        .replace("[tab]", "")
                        .replace("[/tab]", "")
                        .replace("[ch]", "")
                        .replace("[/ch]", ""),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
            }
        }
    }}