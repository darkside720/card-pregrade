package com.cardpregrade.core.model

/** Trading-card games supported by the capture/inspection pipeline. */
enum class CardGame {
    POKEMON,
    ONE_PIECE,

    /** The user did not specify, or the game is not yet supported for identification. */
    UNKNOWN,
}

/**
 * A physical card being inspected.
 *
 * Grading never depends on [identification] being present: an unidentified card is a
 * first-class citizen ("Unknown Card").
 */
data class Card(
    val id: String,
    val game: CardGame,
    val identification: CardIdentification? = null,
    /** Free-text label entered by the user, e.g. "Lugia 149/147". */
    val userLabel: String? = null,
) {
    val displayName: String
        get() = userLabel?.takeIf { it.isNotBlank() }
            ?: identification?.displayName
            ?: UNKNOWN_CARD_NAME

    companion object {
        const val UNKNOWN_CARD_NAME = "Unknown Card"
    }
}

/** Optional catalog metadata. Every field is nullable because identification is best-effort. */
data class CardIdentification(
    val game: CardGame,
    val setName: String? = null,
    val setCode: String? = null,
    val cardNumber: String? = null,
    val cardName: String? = null,
    val language: String? = null,
    val variant: String? = null,
    val year: Int? = null,
    val source: IdentificationSource,
    /** Null for user-entered data; 0..1 for automatic identification. */
    val confidence: Confidence? = null,
) {
    val displayName: String?
        get() = listOfNotNull(cardName, cardNumber).joinToString(" ").ifBlank { null }
}

enum class IdentificationSource {
    USER_ENTERED,
    AUTOMATIC,
}
