@file:OptIn(InternalSerializationApi::class)

package online.krabice.tabsclient

import android.R
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
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


const val API_URL = "http://cat-heater.lan:8000"


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


class MainActivity : ComponentActivity() {
    private var isRefreshing by mutableStateOf(false)

    private var songs by mutableStateOf<List<Song>>(emptyList())
    // TODO: For debug only, remove later
    private var username by mutableStateOf(BuildConfig.USERNAME)
    private var password by mutableStateOf(BuildConfig.PASSWORD)


    suspend fun getData() {
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

            songs = client.get("$API_URL/saved_songs").body()
            client.close()
        } finally {
            isRefreshing = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        println("API URL: $API_URL!")

//        lifecycleScope.launch {
//            getData()
//        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TabsClientTheme {
                TabsClientApp(
                    songs,
                    isRefreshing,
                    {
                        lifecycleScope.launch { getData() }
                    },
                    username,
                    {username = it},
                    password,
                    {password = it}
                )
            }
        }
    }
}

@OptIn(InternalSerializationApi::class)
@Composable
fun TabsClientApp(
    songs: List<Song>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
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
        Column(modifier = Modifier.fillMaxSize()) {
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
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (songs.isEmpty() && !isRefreshing) {
                    item {Text("No songs found", modifier = Modifier.padding(16.dp))}
                } else {
                    items(songs) { song ->
                        SongRow(song) { onSongClick(song) }
                    }
                }
            }
        }
    }
}

@Composable
fun SongRow(song: Song, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .clickable { onClick() }
    ) {
        Text(song.title)
        Text(song.artist)
    }
}

@Composable
fun ProfileScreen(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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
fun SongDetailScreen(song: Song, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Text("Song detail: ${song.title}", modifier = Modifier.padding(16.dp))
}