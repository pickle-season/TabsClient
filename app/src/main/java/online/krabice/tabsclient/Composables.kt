package online.krabice.tabsclient

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsClientApp(
    songs: List<Song>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.SONGS) }
    var selectedSong by rememberSaveable { mutableStateOf<Song?>(null) }
    var selectedChords by rememberSaveable { mutableStateOf<Chords?>(null) }
    var selectedTab by rememberSaveable { mutableStateOf<Tab?>(null) }

    BackHandler(enabled = selectedChords != null || selectedTab != null) {
        selectedChords = null
        selectedTab = null
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = { Icon(it.icon, contentDescription = it.label) },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = {
                        currentDestination = it
                        selectedSong = null
                    }
                )
            }
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            when {
                // when selected chords is not null, show chords detail
                selectedChords != null -> {
                    ChordsDetailScreen(selectedChords!!) { selectedChords = null }
                }
                // when selected tab is not null, show tab detail
                selectedTab != null -> {
                    TabDetailScreen(selectedTab!!) { selectedTab = null }
                }
                // if both are null, show song list
                else -> {
                    when (currentDestination) {
                        AppDestinations.SONGS -> SongListScreen(
                            songs,
                            isRefreshing,
                            onRefresh,
                            onSongClick = { selectedSong = it }
                        )
                        AppDestinations.SETTINGS -> ProfileScreen()
                    }

                    // if song is selected, show modal
                    selectedSong?.let { song ->
                        // if song has only one chords or one tab, go there directly
                        if (song.chords.size == 1 && song.tabs.isEmpty()) {
                            selectedChords = song.chords.first()
                            selectedSong = null
                        }
                        else if (song.chords.isEmpty() && song.tabs.size == 1) {
                            selectedTab = song.tabs.first()
                            selectedSong = null
                        }
                        else {
                            ModalBottomSheet(onDismissRequest = { selectedSong = null }) {
                                SongBottomSheet(
                                    song = song,
                                    onChordsClick = {
                                        selectedChords = it
                                        selectedSong = null
                                    },
                                    onTabClick = {
                                        selectedTab = it
                                        selectedSong = null
                                    },
                                    onClose = { selectedSong = null }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SongBottomSheet(
    song: Song,
    onChordsClick: (Chords) -> Unit,
    onTabClick: (Tab) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = song.title,
            style = MaterialTheme.typography.titleLarge
        )

        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodyMedium
        )

        HorizontalDivider()

        if (song.chords.isNotEmpty()) {
            Text(
                "Chords",
                style = MaterialTheme.typography.titleMedium,
            )
            LazyColumn() {
                items(song.chords) {
                    Button(onClick = { onChordsClick(it) }) {
                        Text("Version ${it.version}")
                    }
                }
            }
        }

//        if (song.tabs.isNotEmpty() && song.chords.isNotEmpty()) {
//            HorizontalDivider()
//        }

        if (song.tabs.isNotEmpty()) {
            Text("Tabs", style = MaterialTheme.typography.titleMedium)
            LazyColumn() {
                items(song.tabs) {
                    Button(onClick = { onTabClick(it) }) {
                        Text("Version ${it.version}")
                    }
                }
            }
        }

        Button(
            modifier = Modifier.align(Alignment.End),
            onClick = onClose
        ) {
            Text("Close")
        }
    }
}


// TODO: Implement searching
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp),
            contentPadding = PaddingValues(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (songs.isEmpty() && !isRefreshing) {
                item {Text("No songs found", modifier = Modifier.padding(16.dp))}
            } else {
                item {
                    Text(
                        "Saved songs",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,

                        )
                }
                items(songs) { song ->
                    SongRow(song) { onSongClick(song) }
                }
            }
        }
    }
}

@Composable
fun SongRow(song: Song, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Column(
                Modifier
                    .weight(1f)
            ) {
                // TODO: Scrolling text for long titles?
                Text(song.title, style = MaterialTheme.typography.titleMedium)
                Text(song.artist, style = MaterialTheme.typography.bodyMedium)
            }
            if (song.tabs.isNotEmpty()) {
                Icon(imageVector = Icons.AutoMirrored.Default.QueueMusic, contentDescription = "Tabs icon")
            }
            if (song.chords.isNotEmpty()) {
                Icon(imageVector = Icons.Default.FontDownload, contentDescription = "Chords icon")
            }
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 40.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    saveCredentials(
                        context,
                        username,
                        password,
                        serverUrl
                    )
                },
                label = { Text("Username") }
            )
        }
        item {
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
        }
        item {
            OutlinedTextField(
                value = serverUrl,
                onValueChange = {
                    serverUrl = it
                    saveCredentials(
                        context,
                        username,
                        password,
                        serverUrl
                    )
                },
                label = { Text("Server URL") }
            )
        }
        item {
            // TODO: Implement onClick
            Button({}) {
                Text("Update server cache")
            }
        }
    }
}

@Composable
fun TabDetailScreen(tab: Tab, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val scrollState = rememberScrollState()

    Column(modifier = Modifier
        .padding(top = 16.dp, start = 16.dp, end = 16.dp)
        .verticalScroll(scrollState)
        .fillMaxSize()
    ) {
        Text(
            // TODO: Temporary replacement,
            //  figure out actual chord rendering
            tab.content
                .replace("[tab]", "")
                .replace("[/tab]", "")
                .replace("[ch]", "")
                .replace("[/ch]", ""),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
}

@Composable
fun ChordsDetailScreen(chords: Chords, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val scrollState = rememberScrollState()

    Column(modifier = Modifier
        .padding(top = 16.dp, start = 16.dp, end = 16.dp)
        .verticalScroll(scrollState)
        .fillMaxSize()
    ) {
        Text(
            // TODO: Temporary replacement,
            //  figure out actual tab
            chords.content
                .replace("[tab]", "")
                .replace("[/tab]", "")
                .replace("[ch]", "")
                .replace("[/ch]", ""),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
}
