package com.tripletriad.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.protocol.Credentials
import kotlinx.coroutines.launch

const val ACCOUNT_SCREEN_TEST_TAG: String = "account-screen"
const val ACCOUNT_NAME_TEST_TAG: String = "account-name"
const val ACCOUNT_PASSWORD_TEST_TAG: String = "account-password"
const val ACCOUNT_SUBMIT_TEST_TAG: String = "account-submit"
const val ACCOUNT_TOGGLE_TEST_TAG: String = "account-toggle"
const val ACCOUNT_ERROR_TEST_TAG: String = "account-error"

/**
 * Signing in, or creating an account — one screen with a switch, not two.
 *
 * ### Why one screen
 *
 * The two forms take the same two fields, validate them with the same rules ([Credentials]) and
 * differ in one word on one button. Splitting them would mean two layouts to keep in step and a
 * player who typed their name into the wrong one having to type it again.
 *
 * ### What is deliberately missing
 *
 * A "stay signed in" checkbox. The session lasts thirty days and is stored either way; offering the
 * choice would imply the alternative is more secure, and on a device the player owns it is not — it
 * is just a sign-in form more often. And password recovery, which needs a channel the server does
 * not have (`AccountRoutes`).
 *
 * ### Why a required update takes the whole screen
 *
 * Because the form below it cannot work. A server that will not serve this build will refuse the
 * sign-in too, and leaving the fields there would invite the player to type their password into
 * something guaranteed to fail and then read an error about it. A *suggested* update is the
 * opposite case — everything still works — so that one is a note above a form still usable.
 *
 * @param update this build's standing with the server, or null when there is nothing to say. See
 *   [UpdateAdvice].
 * @param onSignedIn where to go once the server has said yes. Not called for a failure — the screen
 *   stays and shows why.
 */
@Composable
internal fun AccountScreen(
    session: AccountSession,
    update: UpdateAdvice?,
    onSignedIn: () -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    var isRegistering by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Validated locally so the player learns their password is too short without a round trip. The
    // server checks the same rules and is the only check that counts — `Credentials.looksValid`.
    val credentials = Credentials(username, password)
    val canSubmit = credentials.looksValid() && !session.isBusy

    val title = if (isRegistering) "Create an account" else "Sign in"

    if (update?.isRequired == true) {
        ScreenScaffold(title = "Update needed", onBack = onBack) { UpdateNotice(update) }
        return
    }

    ScreenScaffold(title = title, onBack = onBack) {
        Column(
            modifier = Modifier.testTag(ACCOUNT_SCREEN_TEST_TAG).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            update?.let { UpdateNotice(it) }

            Text(
                text = "Your character lives on the server: sign in from anywhere and your " +
                    "cards, your MGP and your record come with you.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                style = MaterialTheme.typography.labelMedium,
            )

            AccountField(
                value = username,
                onValueChange = { username = it.take(Credentials.USERNAME_LENGTH.last) },
                label = strings[StringKeys.USERNAME],
                tag = ACCOUNT_NAME_TEST_TAG,
                imeAction = ImeAction.Next,
            )

            AccountField(
                value = password,
                onValueChange = { password = it.take(Credentials.PASSWORD_LENGTH.last) },
                label = "Password",
                tag = ACCOUNT_PASSWORD_TEST_TAG,
                imeAction = ImeAction.Done,
                isPassword = true,
            )

            session.failure?.let { failure ->
                Text(
                    text = failure.message(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.testTag(ACCOUNT_ERROR_TEST_TAG),
                )
            }

            WideButton(
                label = if (isRegistering) "Create account" else "Sign in",
                tag = ACCOUNT_SUBMIT_TEST_TAG,
                enabled = canSubmit,
                onClick = {
                    scope.launch {
                        if (isRegistering) {
                            session.register(username.trim(), password)
                        } else {
                            session.signIn(username.trim(), password)
                        }
                        // Only on success. `player` is the honest test for that: it is set by the
                        // same branch that stored the token, so it cannot disagree with whether
                        // there is a session to navigate into.
                        if (session.player != null) onSignedIn()
                    }
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (isRegistering) {
                        "Already have an account? Sign in"
                    } else {
                        "New here? Create an account"
                    },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .testTag(ACCOUNT_TOGGLE_TEST_TAG)
                        .clickable { isRegistering = !isRegistering }
                        .padding(8.dp),
                )
            }
        }
    }
}

/**
 * One field, styled like the profile screen's.
 *
 * Extracted because the two differ only in whether the characters are shown, and a second copy of
 * the eight-line `colors` block is how the two forms would start looking different.
 */
@Composable
private fun AccountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    tag: String,
    imeAction: ImeAction,
    isPassword: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        // The password is masked as it is typed, and this is the only place in the app that
        // renders one at all. It is never logged, never stored, and never put in a `toString`.
        visualTransformation = if (isPassword) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        colors = TextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier.testTag(tag).fillMaxWidth(),
    )
}
