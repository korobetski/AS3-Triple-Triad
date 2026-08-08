package com.tripletriad.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripletriad.data.SaveSlot
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.CardCollection
import com.tripletriad.model.GameSave
import kotlinx.coroutines.launch

const val PROFILE_LIST_TEST_TAG: String = "profile-list"
const val PROFILE_NEW_TEST_TAG: String = "profile-new"
const val PROFILE_NAME_TEST_TAG: String = "profile-name"
const val PROFILE_CREATE_TEST_TAG: String = "profile-create"
const val PROFILE_EMPTY_TEST_TAG: String = "profile-empty"

/** `profile-row-<key>`, so a test can find a specific profile without knowing its position. */
fun profileRowTestTag(key: String): String = "profile-row-$key"

/** `profile-delete-<key>`. */
fun profileDeleteTestTag(key: String): String = "profile-delete-$key"

/** `collection-ff14_` / `collection-ff8_`, by the AS3 prefix that is also the stored value. */
fun collectionChoiceTestTag(collection: CardCollection): String = "collection-${collection.prefix}"

/**
 * The profile list — the original's `LoadScreen`, which listed characters rather than files.
 *
 * Every row is a whole profile rather than a name: level, MGP and the win record are the three
 * things that tell a player which of two similarly-named characters is the one they meant, and the
 * profile is already fully decoded by the time the list exists ([SaveSlot]), so showing them costs
 * nothing.
 *
 * Deletion is **two taps and no dialog**: the row's × arms itself and the second tap does it. A
 * modal confirm was the original's answer (`STR_DELETE_SAVE_CONFIRMATION_MESSAGE`, which is why
 * that string is used as the armed label) but a dialog on a phone covers the list it is asking
 * about, and an armed control that disarms when you touch anything else is as recoverable and reads
 * faster.
 *
 * @param onDeleted what else a deleted profile takes with it, by key. Run **after** the profile is
 *   gone and never in its place: anything kept alongside a save is worth less than the save, and a
 *   failure to clean it up must not leave the profile itself half-deleted.
 */
@Composable
internal fun ProfileListScreen(
    session: ProfileSession,
    onSelected: (GameSave) -> Unit,
    onNew: () -> Unit,
    onBack: () -> Unit,
    onDeleted: suspend (String) -> Unit = {},
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var armed by remember { mutableStateOf<String?>(null) }

    ScreenScaffold(title = strings[StringKeys.PROFILES], onBack = onBack) {
        if (session.isLoaded && session.slots.isEmpty()) {
            Text(
                text = strings[StringKeys.NO_PROFILE],
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(PROFILE_EMPTY_TEST_TAG).padding(vertical = 24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.testTag(PROFILE_LIST_TEST_TAG).weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(session.slots, key = { it.key }) { slot ->
                    ProfileRow(
                        slot = slot,
                        isArmed = armed == slot.key,
                        onSelect = {
                            armed = null
                            session.select(slot.save)
                            onSelected(slot.save)
                        },
                        onDelete = {
                            if (armed == slot.key) {
                                armed = null
                                scope.launch {
                                    session.delete(slot.key)
                                    onDeleted(slot.key)
                                }
                            } else {
                                armed = slot.key
                            }
                        },
                    )
                }
            }
        }

        WideButton(
            label = strings[StringKeys.NEW_PROFILE],
            tag = PROFILE_NEW_TEST_TAG,
            enabled = !session.isBusy,
            onClick = onNew,
        )
    }
}

@Composable
private fun ProfileRow(
    slot: SaveSlot,
    isArmed: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalStrings.current
    val save = slot.save

    Row(
        modifier = Modifier
            .testTag(profileRowTestTag(slot.key))
            .fillMaxWidth()
            .rowSurface(armed = isArmed)
            .clickable(onClick = onSelect)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = save.username,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(
                    collectionLabel(save.mode),
                    "${strings[StringKeys.LEVEL]} ${save.level}",
                    "${save.mgp} ${strings[StringKeys.MGP]}",
                ).joinToString(DOT_SEPARATOR),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(
                    "${save.stats.wins} ${strings[StringKeys.WINS]}",
                    "${save.stats.draws} ${strings[StringKeys.DRAWS]}",
                    "${save.stats.defeats} ${strings[StringKeys.DEFEATS]}",
                ).joinToString(DOT_SEPARATOR),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = if (isArmed) strings[StringKeys.DELETE] else "×",
            color = if (isArmed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            },
            fontSize = if (isArmed) 12.sp else 18.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .testTag(profileDeleteTestTag(slot.key))
                .clickable(onClick = onDelete)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * Creating a profile: a name, and the collection.
 *
 * The collection is the one irreversible choice in the game and it is made here because it has to
 * be: `Save.DATAS.MODE` decides which of the two card tables the profile's card ids index, which
 * opponents it can meet and which rules those opponents may impose. The original never offered the
 * choice at all — `setToDefaultValues()` hard-codes `'ff14_'` and nothing changes it — so an `ff8_`
 * profile was unreachable despite the whole second table shipping with the game.
 */
@Composable
internal fun ProfileCreateScreen(
    session: ProfileSession,
    onCreated: (GameSave) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(GameSave.DEFAULT_USERNAME) }
    var collection by remember { mutableStateOf(CardCollection.FF14) }

    ScreenScaffold(title = strings[StringKeys.NEW_PROFILE], onBack = onBack) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(MAX_NAME_LENGTH) },
            label = { Text(strings[StringKeys.USERNAME]) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = TextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier.testTag(PROFILE_NAME_TEST_TAG).fillMaxWidth(),
        )

        Text(
            text = strings[StringKeys.COLLECTION],
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (choice in CardCollection.entries) {
                CollectionChoice(
                    collection = choice,
                    isSelected = collection == choice,
                    modifier = Modifier.weight(1f),
                    onClick = { collection = choice },
                )
            }
        }

        Box(modifier = Modifier.weight(1f))

        WideButton(
            label = strings[StringKeys.START],
            tag = PROFILE_CREATE_TEST_TAG,
            enabled = !session.isBusy,
            onClick = {
                scope.launch {
                    session.create(name, collection)
                    session.active?.let(onCreated)
                }
            },
        )
    }
}

@Composable
private fun CollectionChoice(
    collection: CardCollection,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .testTag(collectionChoiceTestTag(collection))
            .rowSurface(selected = isSelected)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = collectionLabel(collection),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/**
 * `FFXIV` / `FFVIII` — deliberately not translated and deliberately not localised.
 *
 * These are the two source games' titles, and the AS3 bundles have no key for either: the
 * collection appears in the data only as the prefix `ff14_` / `ff8_`. A proper noun is the same in
 * all four languages, so inventing four identical translations would be four files to keep in step
 * for no gain. The Roman numerals are how Square Enix writes them.
 */
internal fun collectionLabel(collection: CardCollection): String = when (collection) {
    CardCollection.FF14 -> "FFXIV"
    CardCollection.FF8 -> "FFVIII"
}

/** The longest a character name may be. Long enough for any real name, short enough to lay out. */
private const val MAX_NAME_LENGTH = 24
