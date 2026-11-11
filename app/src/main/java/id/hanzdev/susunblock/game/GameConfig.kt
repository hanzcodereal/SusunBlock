package id.hanzdev.susunblock.game

/** Konstanta permainan (dulu berada di ScriptableObject Settings pada versi Unity). */
object GameConfig {
    const val ROWS = 8
    const val COLS = 8
    const val SLOTS = 3

    // Skor
    const val LINE_REMOVAL_SCORE = 10
    const val MULTI_LINE_SCORE = 50
    const val COMBO_SCORE = 50
    const val RESET_COMBO_AFTER_MOVES = 3
    const val ALL_CLEAR_BONUS = 1000

    // Pembuatan blok cerdas
    const val BLOCK_SIZE_GRADE_MULTIPLIER = 10
    const val REQUIRED_BUSY_PERCENT_FOR_BEST_BLOCK = 60f
    const val BETTER_BLOCK_PROBABILITY = 0.85f
    const val ALL_CLEAR_FIELD_MULTIPLIER = 2
}
