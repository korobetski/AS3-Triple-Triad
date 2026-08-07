package com.tripletriad.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Which of a potion's two boons it raises. `PotionItem.as:18-23` writes `{type:'XP'|'MGP',
 * value:n}`.
 *
 * `Save.DATAS.BOONS` has a third slot, `LUCK`, that **no potion grants** — no `LUCK_BOOST_MOD`
 * exists and nothing writes it. It is modelled on [Boons] because the save file has the field, but
 * it has no member here because nothing can produce it.
 */
@Serializable
enum class BoonType {
    @SerialName("XP")
    XP,

    @SerialName("MGP")
    MGP,
}

/** What using a potion does: raise [type] by [value]. `PotionItem.modifier`. */
data class BoonModifier(val type: BoonType, val value: Int)

/** The five metal packs share one icon; only the four tribe packs have their own. */
private const val PACK_ICON = "booster_pack_icon"

/**
 * The six potions of `datas/PotionItem.as`.
 *
 * The serial names are the raw AS3 strings, which are also the i18n key stems (`STR_XP_BOOST`,
 * `STR_XP_BOOST_DESC`) and the values stored in `Save.DATAS.BAG` — so a bag written by the original
 * still parses. `NPCs.as` names them directly in reward tables (`potion:'MGP_BOOST'`), which is the
 * other reason they cannot be renamed.
 */
@Serializable
enum class PotionType(val modifier: BoonModifier) {
    @SerialName("SMALL_XP_BOOST")
    SMALL_XP(BoonModifier(BoonType.XP, 2)),

    @SerialName("SMALL_MGP_BOOST")
    SMALL_MGP(BoonModifier(BoonType.MGP, 2)),

    @SerialName("XP_BOOST")
    XP(BoonModifier(BoonType.XP, 5)),

    @SerialName("MGP_BOOST")
    MGP(BoonModifier(BoonType.MGP, 5)),

    @SerialName("BIG_XP_BOOST")
    BIG_XP(BoonModifier(BoonType.XP, 10)),

    @SerialName("BIG_MGP_BOOST")
    BIG_MGP(BoonModifier(BoonType.MGP, 10)),
    ;

    /** `PotionItem.as:35` — `i18n.gettext('STR_' + _potionType)`. */
    val nameKey: String get() = "STR_$as3Name"

    /** `PotionItem.as:37`. */
    val descriptionKey: String get() = "STR_${as3Name}_DESC"

    /** The AS3 constant's value, which the serial name also uses. */
    private val as3Name: String
        get() = when (this) {
            SMALL_XP -> "SMALL_XP_BOOST"
            SMALL_MGP -> "SMALL_MGP_BOOST"
            XP -> "XP_BOOST"
            MGP -> "MGP_BOOST"
            BIG_XP -> "BIG_XP_BOOST"
            BIG_MGP -> "BIG_MGP_BOOST"
        }
}

/**
 * The nine booster packs of `datas/BoosterItem.as`, each with its fixed card pool.
 *
 * `pool` is transcribed verbatim from the `*_BOOSTER_CARDS` constants (`:19-27`), not re-derived
 * from rarity or type — the lists are hand-picked and several are inconsistent with any rule you
 * might infer (card 51 appears in Gold, Platinum *and* Garlean; Silver and Scion overlap on 19, 50
 * and 56).
 *
 * The pools name **`ff14_` card ids**, and the collection is not recorded anywhere: `CardItem`
 * looks an id up in `cards.DATAS`, which `cards.as` points at whichever table the profile's `MODE`
 * selects. Opening a bronze booster on an `ff8_` profile therefore yields *ff8* card 4, not ff14
 * card 4. That is the original's behaviour and [BoosterItem.open] preserves it by returning a bare
 * id; the caller resolves it against the profile's collection.
 */
@Serializable
enum class BoosterType(val pool: List<Int>, val iconId: String) {
    @SerialName("BRONZE_BOOSTER")
    BRONZE(listOf(4, 5, 8, 12, 27, 38), PACK_ICON),

    @SerialName("SILVER_BOOSTER")
    SILVER(listOf(14, 15, 16, 17, 19, 50, 56, 57), PACK_ICON),

    @SerialName("GOLD_BOOSTER")
    GOLD(listOf(28, 29, 30, 34, 51, 58, 68, 76), PACK_ICON),

    @SerialName("MITHRIL_BOOSTER")
    MITHRIL(listOf(39, 108, 109, 113, 123, 52, 70, 72, 73), PACK_ICON),

    @SerialName("PLATINUM_BOOSTER")
    PLATINUM(listOf(51, 55, 57, 63, 69, 71, 77, 80), PACK_ICON),

    @SerialName("BEAST_BOOSTER")
    BEAST(listOf(14, 15, 16, 17, 18, 27, 35, 36, 37, 82, 83, 117, 128), "beast_booster"),

    @SerialName("PRIMAL_BOOSTER")
    PRIMAL(listOf(40, 41, 42, 43, 52, 53, 54, 55, 61, 97, 98, 137), "primal_booster"),

    @SerialName("SCION_BOOSTER")
    SCION(listOf(19, 122, 46, 48, 49, 50, 56, 59, 60, 138), "scion_booster"),

    @SerialName("GARLEAN_BOOSTER")
    GARLEAN(listOf(31, 32, 47, 51, 64, 119), "garlean_booster"),
    ;

    /** `BoosterItem.as:49` — `i18n.gettext('STR_' + _boosterType)`. */
    val nameKey: String get() = "STR_$as3Name"

    /** `BoosterItem.as:51`. */
    val descriptionKey: String get() = "STR_${as3Name}_DESC"

    private val as3Name: String get() = "${name}_BOOSTER"
}

/**
 * Something in the player's bag.
 *
 * ### Display split out
 *
 * `datas/Item.as` **extends `starling.display.Sprite`**: it owns an `ItemIcon` child and a
 * `TouchEvent` listener, and dispatches `Event.TRIGGERED` when tapped. None of that is here. What
 * remains is the state the original persisted plus the constants its constructors assigned, and the
 * icon is named rather than held — a composable resolves [iconId] to a drawable.
 *
 * ### The flags are derived, not stored
 *
 * `_sellable`, `_stackable`, `_useable`, `_dropable` and `_value` look like fields but every
 * subclass assigns them **fixed values in its constructor** and nothing ever changes them
 * afterwards. So they are computed properties per subtype here. This is not a simplification that
 * loses information: it makes it impossible to construct a booster that is sellable, which the AS3
 * type system allowed and the AS3 code never did.
 *
 * ### Wire format
 *
 * The discriminator is `type` and the subclass serial names are the `ITEM_TYPE_*` constants, so
 * [Item] round-trips exactly the objects `__toJSON()` produced and `Item.itemize` consumed:
 * `{"type":"item-type-card","card":13,"stack":2}`. That is what sits in `Save.DATAS.BAG`.
 *
 * `ITEM_TYPE_ACCESSORY` (`Item.as:19`) has no subclass here because it has none there either — the
 * constant is declared and never used. `ITEM_TYPE_MISC` is [MiscItem], the fallback `itemize`
 * returns for an unrecognised type.
 */
@Serializable
sealed class Item {
    /** How many are held. `Item.as:41` defaults it to 1. */
    abstract val stack: Int

    /** Texture name for the bag icon. */
    abstract val iconId: String

    /** i18n key for the tooltip text. */
    abstract val descriptionKey: String

    /** MGP the shop pays, per unit. Only [CardItem] has a non-zero one. */
    abstract val value: Int

    abstract val sellable: Boolean
    abstract val stackable: Boolean
    abstract val useable: Boolean
    abstract val dropable: Boolean

    /** The same entry with [stack] set to [count]. */
    abstract fun withStack(count: Int): Item

    protected fun requireValidStack() {
        require(stack >= 0) { "stack must not be negative, was $stack" }
    }
}

/**
 * A card, held as an inventory entry rather than in the collection. `datas/CardItem.as`.
 *
 * Using one adds [cardId] to `Save.DATAS.CARDS`; that is the shop and reward path by which a card
 * enters a collection.
 *
 * [iconId] and the display name both need the card's own record — the icon is `card_r{rarity}_icon`
 * and the name is the card's name composed with `STR_CARD` — so neither is available from this
 * object alone. [iconFor] takes the card; the name is left to the UI, because `CardItem.as:19-22`
 * orders the two words differently in French and word order is not a data-layer concern.
 */
@Serializable
@SerialName("item-type-card")
data class CardItem(
    @SerialName("card") val cardId: Int,
    override val stack: Int = 1,
) : Item() {
    init {
        requireValidStack()
        require(cardId > 0) { "card id must be positive, was $cardId" }
    }

    /** `CardItem.as:23` needs the card's rarity, which only the catalog knows. */
    fun iconFor(card: Card): String = "card_r${card.rarity}_icon"

    /**
     * A placeholder, used when the card is not resolvable. The real icon is [iconFor].
     *
     * Rarity 1 rather than an "unknown" texture: there is no such texture, and a wrong star count
     * on an unresolvable card is a better failure than a missing image.
     */
    override val iconId: String get() = "card_r1_icon"

    override val descriptionKey: String get() = "STR_CARD_ITEM_DESC"

    /** `CardItem.as:25` — `value = _cardId * 4`. Later cards are worth more because ids ascend. */
    override val value: Int get() = cardId * MGP_PER_ID

    override val sellable: Boolean get() = true
    override val stackable: Boolean get() = true
    override val useable: Boolean get() = true
    override val dropable: Boolean get() = true

    override fun withStack(count: Int): CardItem = copy(stack = count)

    private companion object {
        const val MGP_PER_ID = 4
    }
}

/**
 * A booster pack. `datas/BoosterItem.as`.
 */
@Serializable
@SerialName("item-type-booster")
data class BoosterItem(
    @SerialName("booster") val boosterType: BoosterType,
    override val stack: Int = 1,
) : Item() {
    init {
        requireValidStack()
    }

    override val iconId: String get() = boosterType.iconId
    override val descriptionKey: String get() = boosterType.descriptionKey

    /** `BoosterItem.as:52` — `value = 0`. Boosters cannot be sold, so the price is unused. */
    override val value: Int get() = 0

    override val sellable: Boolean get() = false
    override val stackable: Boolean get() = true
    override val useable: Boolean get() = true
    override val dropable: Boolean get() = false

    override fun withStack(count: Int): BoosterItem = copy(stack = count)

    /**
     * Draws one card id from the pack's pool.
     *
     * `BoosterItem.open()` (`:60-65`) verbatim, bias included:
     *
     * ```actionscript
     * var regularRand:Number = Math.random() * uint(this._boosterCards.length - 1);
     * var downgrade:Number    = Math.random() * 1.25;
     * var fin:uint = Math.min(Math.round(regularRand * downgrade), this._boosterCards.length - 1);
     * ```
     *
     * Two uniform draws multiplied together, so the result is heavily weighted towards **index 0**
     * and the last entry is nearly unreachable — with a 6-card pool the mean index is about 1.6 of
     * 5. Since the pools are written best-last (Bronze ends on 38, Beast on 128), that is the
     * rarity curve, and it is intentional in the original however oddly expressed. Reproduced
     * rather than replaced: changing it would change every drop rate in the game.
     *
     * @param random injected so drops are reproducible in tests.
     */
    fun open(random: Random = Random.Default): Int {
        val pool = boosterType.pool
        val last = pool.lastIndex
        val regular = random.nextDouble() * last
        val downgrade = random.nextDouble() * DOWNGRADE_CEILING
        return pool[min((regular * downgrade).roundToInt(), last)]
    }

    private companion object {
        const val DOWNGRADE_CEILING = 1.25
    }
}

/**
 * A boon potion. `datas/PotionItem.as`.
 */
@Serializable
@SerialName("item-type-potion")
data class PotionItem(
    @SerialName("potion") val potionType: PotionType,
    override val stack: Int = 1,
) : Item() {
    init {
        requireValidStack()
    }

    /** `PotionItem.as:36`. Note the AS3 texture name is camelCase where every other one is not. */
    override val iconId: String get() = "potionItem"

    override val descriptionKey: String get() = potionType.descriptionKey
    override val value: Int get() = 0

    override val sellable: Boolean get() = false
    override val stackable: Boolean get() = true
    override val useable: Boolean get() = true
    override val dropable: Boolean get() = false

    override fun withStack(count: Int): PotionItem = copy(stack = count)

    /** What using it raises. `PotionItem.modifier`. */
    val modifier: BoonModifier get() = potionType.modifier
}

/**
 * Anything else. The `else` branch of `Item.itemize` (`Item.as:174`), which returns a bare `Item`
 * with the base class's defaults.
 *
 * Reachable in practice from a bag entry whose `type` this build does not know — a save written by
 * a newer version, or the unused `item-type-accessory`. Keeping it as a real member means such an
 * entry survives a load/save cycle as an inert row instead of being dropped.
 */
@Serializable
@SerialName("item-type-misc")
data class MiscItem(
    override val stack: Int = 1,
) : Item() {
    init {
        requireValidStack()
    }

    /** `Item.as:36` constructs its icon with the booster texture, whatever the item is. */
    override val iconId: String get() = PACK_ICON

    override val descriptionKey: String get() = ""

    /** `Item.as:42` — the base default is 1, not 0. */
    override val value: Int get() = 1

    override val sellable: Boolean get() = true
    override val stackable: Boolean get() = true

    /** `Item.as:45` — the base class is the one thing that is *not* useable. */
    override val useable: Boolean get() = false
    override val dropable: Boolean get() = true

    override fun withStack(count: Int): MiscItem = copy(stack = count)
}
