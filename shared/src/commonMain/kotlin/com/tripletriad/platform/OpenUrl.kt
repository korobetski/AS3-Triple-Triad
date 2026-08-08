package com.tripletriad.platform

import androidx.compose.runtime.Composable

/**
 * Something that hands a URL to whatever the host uses to open one — a browser, or an app store.
 *
 * ### Why the app opens a link instead of updating itself
 *
 * Because on two of the three targets it is not the app's decision to make: an app store owns
 * updates on Android and iOS, and an app that worked around that would be removed from the store.
 * And on the third it is not a small feature: fetching a binary and running it means owning signed
 * artifacts, a verified release channel and an installer handoff, and getting any part of that
 * wrong turns the update path into the attack. Sending the player to the place their platform
 * already trusts costs one function and is correct everywhere.
 *
 * ### Why it is `@Composable` rather than a plain function
 *
 * Android needs a `Context` and the other two need nothing. A plain `expect fun openUrl(url)` would
 * therefore have forced a mutable global holding the application context, set by the host and
 * readable from anywhere — the sort of thing that works until something reads it before `onCreate`.
 * Taking it from the composition instead means the one platform that needs a context gets the right
 * one, and the two that do not ignore the question.
 *
 * ### Why the returned function swallows failures
 *
 * There is nothing useful to do with one. The link is a suggestion at the end of a message that has
 * already been read: if the host cannot open it, the player still knows what they need and where it
 * lives, and an error dialog about a browser would be about the wrong thing entirely.
 */
@Composable
expect fun rememberUrlOpener(): (String) -> Unit
