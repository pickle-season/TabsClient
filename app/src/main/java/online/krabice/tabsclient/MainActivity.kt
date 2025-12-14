@file:OptIn(InternalSerializationApi::class)

package online.krabice.tabsclient

import android.os.Bundle
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Divider
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope

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

import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json


const val API_URL = "http://cat-heater:8000"


@Serializable
data class Chords(
    val id: Int,
    val url: String,
    val version: Int,

    @SerialName("song_id")
    val songId: Int,
)

@Serializable
data class Tab(
    val id: Int,
    val url: String,
    val version: Int,
    val bass: Boolean,

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


class MainActivity : ComponentActivity() {
    private var isRefreshing by mutableStateOf(false)

    private var songs by mutableStateOf<List<Song>>(emptyList())
    // TODO: For debug only, remove later
    private var username by mutableStateOf(BuildConfig.USERNAME)
    private var password by mutableStateOf(BuildConfig.PASSWORD)

    suspend fun getChords(chordsId: Int): String {
        println("Getting chords")

        val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 10000
            }
        }
        val response = client.get("$API_URL/chords/$chordsId")
        if (response.status != HttpStatusCode.OK) {
            println("Non-OK HTTP status: ${response.status.value}, ${response.status.description}")
            return "ERROR: ${response.body<ErrorResponse>().detail}"
        }

        val content: String = response.body<ContentResponse>().content

        client.close()
        return content
    }

    suspend fun updateSavedSongs() {
        isRefreshing = true

        try {
            val client = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json()
                }
            }

            songs = client.get("$API_URL/saved_songs").body()
            client.close()
        } finally {
            isRefreshing = false
        }

    }

    suspend fun updateSongs() {
        isRefreshing = true

        try {
            println("Updating songs")

            val client = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json()
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = 60000
                }
            }
            val response = client.post("$API_URL/update") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("username" to username, "password" to password))
            }
            if (response.status != HttpStatusCode.OK) {
                println("Non-OK HTTP status: ${response.status.value}, ${response.status.description}")
                println("Response: ${response.bodyAsText()}")
                isRefreshing = false
                client.close()
                return
            }

            client.close()
        } finally {
            isRefreshing = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        println("API URL: $API_URL!")

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
                    username,
                    {username = it},
                    password,
                    {password = it},
                    { getChords(it) }
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
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    getChords: suspend (Int) -> String
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
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize() ) {
            when {
                selectedSong != null -> SongDetailScreen(
                    selectedSong!!,
                    { selectedSong = null },
                    getChords
                )

                else -> when (currentDestination) {
                    AppDestinations.HOME -> SongListScreen(songs, isRefreshing, onRefresh) {
                        selectedSong = it
                    }

                    AppDestinations.FAVORITES -> Text("Favourites")
                    AppDestinations.PROFILE -> ProfileScreen(
                        username,
                        onUsernameChange,
                        password,
                        onPasswordChange
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
                    .padding(top = 16.dp, bottom = 16.dp),
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
                style = MaterialTheme.typography.titleMedium
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
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 40.dp, start = 16.dp, end = 16.dp)) {
        item {
            OutlinedTextField(
                value=username,
                onValueChange = onUsernameChange,
                label = { Text("Username") }
            )

            OutlinedTextField(
                value=password,
                onValueChange = onPasswordChange,
                label = { Text("Password") },

            )
        }
    }
}

@Composable
fun SongDetailScreen(song: Song, onBack: () -> Unit, getChords: suspend (Int) -> String) {
    BackHandler(onBack = onBack)
    var chordsText by rememberSaveable { mutableStateOf<String?>(null) }
    var isLoading by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(song.id) {
        isLoading = true
        chordsText = getChords(song.chords.first().id)
        isLoading = false
    }

    val scrollState = rememberScrollState()

    Column(modifier = Modifier
        .padding(16.dp)
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