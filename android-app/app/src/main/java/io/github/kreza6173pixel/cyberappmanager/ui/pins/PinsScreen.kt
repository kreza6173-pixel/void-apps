package io.github.kreza6173pixel.cyberappmanager.ui.pins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.cyberappmanager.R
import io.github.kreza6173pixel.cyberappmanager.inventory.InventoryRepository

@Composable
fun PinsScreen(repository: InventoryRepository, modifier: Modifier = Modifier) {
    var pins by remember { mutableStateOf(repository.pins().toList().sorted()) }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.pins_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.pins_description), style = MaterialTheme.typography.bodyMedium)
        if (pins.isEmpty()) Text(stringResource(R.string.pins_empty)) else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(pins, key = { it }) { pkg ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(pkg, style = MaterialTheme.typography.bodyLarge)
                        OutlinedButton(onClick = { pins = repository.setPinned(pkg, false).toList().sorted() }) { Text(stringResource(R.string.action_unpin)) }
                    }
                }
            }
        }
    }
}
