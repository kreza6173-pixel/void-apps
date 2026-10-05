package io.github.kreza6173pixel.cyberappmanager.ui.common

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import io.github.kreza6173pixel.cyberappmanager.R

/**
 * Shell text (commands, output, tags, package names) is always LTR + monospace. Under the fa
 * locale the bidi algorithm otherwise reorders it: "bridge start()" became "()bridge start".
 */
@Composable
fun monoStyle(base: TextStyle = MaterialTheme.typography.bodySmall): TextStyle = base.copy(
    fontFamily = FontFamily.Monospace,
    textDirection = TextDirection.Ltr,
    textAlign = TextAlign.Left,
)

@Composable
fun LtrMonoText(
    text: String,
    color: Color = Color.Unspecified,
    style: TextStyle = MaterialTheme.typography.bodySmall,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
            text = text,
            style = monoStyle(style),
            color = color,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Copy + Share for any block of output. Share goes through the system sheet (save, send...). */
@Composable
fun CopyShareButtons(text: String) {
    val context = LocalContext.current
    Row {
        TextButton(onClick = { copyText(context, text) }) {
            Text(stringResource(R.string.action_copy))
        }
        TextButton(onClick = { shareText(context, text) }) {
            Text(stringResource(R.string.action_share))
        }
    }
}

fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Cyber App Manager", text))
    // Android 13+ shows its own clipboard confirmation; older versions show nothing.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
    }
}

fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, null)
    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(chooser) }
}
