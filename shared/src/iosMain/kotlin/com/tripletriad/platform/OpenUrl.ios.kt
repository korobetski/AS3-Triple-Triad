package com.tripletriad.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tripletriad.log.Log
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/** Safari, or the App Store app when the URL is a store link — UIKit decides which. */
@Composable
actual fun rememberUrlOpener(): (String) -> Unit = remember {
    { url ->
        val target = NSURL.URLWithString(url)
        if (target == null) {
            Log.w(TAG) { "not a URL; not opening it" }
        } else {
            UIApplication.sharedApplication.openURL(target, emptyMap<Any?, Any>(), null)
        }
    }
}

private const val TAG = "OpenUrl"
