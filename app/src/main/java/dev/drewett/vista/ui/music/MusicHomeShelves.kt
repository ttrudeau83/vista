package dev.drewett.vista.ui.music

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.ClickableSurfaceDefaults
import dev.drewett.vista.ui.components.focusPop
import coil.compose.AsyncImage
import dev.drewett.vista.data.music.MusicHomeCard
import dev.drewett.vista.data.music.MusicShelf

/** Native shelves retain the categories and ordering supplied by Music. */
@Composable
fun MusicHomeShelves(shelves:List<MusicShelf>,onClick:(MusicHomeCard)->Unit,modifier:Modifier=Modifier) {
    LazyColumn(modifier,verticalArrangement=Arrangement.spacedBy(22.dp)) {
        items(shelves,key={it.title}) { shelf ->
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(shelf.title,style=MaterialTheme.typography.titleLarge)
                LazyRow(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    items(shelf.cards) { card ->
                        Surface(onClick={onClick(card)},modifier=Modifier.width(170.dp).focusPop(),scale=ClickableSurfaceDefaults.scale(focusedScale=1f)) {
                            Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                AsyncImage(card.thumbnail,null,modifier=Modifier.fillMaxWidth().aspectRatio(1f))
                                Text(card.title,maxLines=2,minLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}
