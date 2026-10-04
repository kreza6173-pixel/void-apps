package io.github.kreza6173pixel.cyberappmanager.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.cyberappmanager.R
import io.github.kreza6173pixel.cyberappmanager.install.ExtractResult
import io.github.kreza6173pixel.cyberappmanager.install.InstallerRepository
import io.github.kreza6173pixel.cyberappmanager.ui.common.CopyShareButtons
import io.github.kreza6173pixel.cyberappmanager.ui.common.LtrMonoText
import io.github.kreza6173pixel.cyberappmanager.ui.tools.verdictColor
import io.github.kreza6173pixel.cyberappmanager.ui.tools.verdictText

/** A8 extract (pulse-install Extract tab), per app: base and split APKs, or one .xapk. Copies only, never changes the app. */
@Composable
fun ExtractCard(result: ExtractResult?, enabled: Boolean, onExtract: (Boolean) -> Unit) {
    var showLog by remember(result) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.ext_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.ext_note, InstallerRepository.EXTRACT_ROOT), style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ onExtract(false) }, Modifier.weight(1f), enabled = enabled) { Text(stringResource(R.string.ext_apks)) }
                OutlinedButton({ onExtract(true) }, Modifier.weight(1f), enabled = enabled) { Text(stringResource(R.string.ext_xapk)) }
            }
            if (result != null) {
                Text(stringResource(verdictText(result.verdict)) + " \u00b7 " + result.note, color = verdictColor(result.verdict), style = MaterialTheme.typography.bodySmall)
                if (result.destination.isNotEmpty()) LtrMonoText(result.destination)
                if (result.files.isNotEmpty()) Text(result.files.joinToString("\n"), style = MaterialTheme.typography.labelSmall)
                TextButton({ showLog = !showLog }) { Text(stringResource(if (showLog) R.string.net_raw_hide else R.string.net_raw_show)) }
                if (showLog) { LtrMonoText(result.log); CopyShareButtons(result.log) }
            }
        }
    }
}
