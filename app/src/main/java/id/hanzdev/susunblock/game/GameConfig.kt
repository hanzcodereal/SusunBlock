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

    // Pembuat blok cerdas
    /** Bobot nilai tiap baris/kolom yang bisa dihapus oleh sebuah penempatan. */
    const val LINE_CLEAR_SCORE = 60f
    /** Seberapa besar penempatan yang bagus memengaruhi peluang sebuah blok dipilih. */
    const val FIT_WEIGHT = 3.0
    /** Mulai kepadatan papan ini, blok pertama kadang diusahakan bisa menghapus garis. */
    const val HELPER_BUSY_FRACTION = 0.5f
    /** Kesulitan awal (0..1). Naik bertahap sampai 1 saat skor mencapai DIFFICULTY_FULL_SCORE. */
    const val DIFFICULTY_BASE = 0.35f
    const val DIFFICULTY_FULL_SCORE = 8000f
    /** Hukuman untuk lubang kosong yang terkurung (1, 2, dan 3 sel). */
    const val HOLE_1_PENALTY = 20f
    const val HOLE_2_PENALTY = 10f
    const val HOLE_3_PENALTY = 4f
}
