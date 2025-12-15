package online.krabice.tabsclient

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    SONGS("Saved Songs", Icons.Default.MusicNote),
    //FAVORITES("Favorites", Icons.Default.Favorite),
    SETTINGS("Settings", Icons.Default.Settings),
}
