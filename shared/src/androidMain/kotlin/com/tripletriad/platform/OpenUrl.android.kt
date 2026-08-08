package com.tripletriad.platform

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tripletriad.log.Log

/**
 * An `ACTION_VIEW` intent, which resolves to the browser — or to the Play Store app when the URL
 * is a store link, which is the whole reason the download is a URL and not a file.
 */
@Composable
actual fun rememberUrlOpener(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { url ->
            try {
                // `NEW_TASK` because the context may be an application one, and an activity
                // started from one without it throws rather than opening anything.
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (failure: ActivityNotFoundException) {
                // A device with no browser and no store. Rare, and not the player's problem to
                // solve from inside a card game.
                Log.w(TAG, failure) { "nothing on this device can open the link" }
            }
        }
    }
}

private const val TAG = "OpenUrl"
