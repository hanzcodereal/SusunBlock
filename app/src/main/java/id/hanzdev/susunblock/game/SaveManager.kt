package id.hanzdev.susunblock.game

import android.content.Context

/** Status tantangan untuk tanggal tertentu. */
class ChallengeStatus(val started: Boolean, val over: Boolean, val score: Int)

/** Penyimpanan progres dan pengaturan memakai SharedPreferences. */
class SaveManager(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("susun_block_save", Context.MODE_PRIVATE)

    var sfxOn: Boolean
        get() = prefs.getBoolean("sfx", true)
        set(v) { prefs.edit().putBoolean("sfx", v).apply() }

    var hapticsOn: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(v) { prefs.edit().putBoolean("haptics", v).apply() }

    var tutorialDone: Boolean
        get() = prefs.getBoolean("tutorial_done", false)
        set(v) { prefs.edit().putBoolean("tutorial_done", v).apply() }

    /** Skor terbaik mode klasik. */
    val bestScore: Int get() = prefs.getInt("best", 0)

    /** Status tantangan untuk tanggal [todayKey]. */
    fun challengeStatus(todayKey: Int): ChallengeStatus {
        if (prefs.getInt("ch_key", -1) != todayKey) return ChallengeStatus(false, false, 0)
        return ChallengeStatus(true, prefs.getBoolean("ch_over", false), prefs.getInt("ch_score", 0))
    }

    fun save(engine: GameEngine) {
        if (engine.tutorialMode) return
        if (engine.mode == Mode.CHALLENGE) saveChallenge(engine) else saveClassic(engine)
    }

    private fun saveClassic(engine: GameEngine) {
        prefs.edit()
            .putInt("best", engine.bestScore)
            .putBoolean("game_over", engine.gameOver)
            .putInt("score", engine.score)
            .putInt("combo", engine.streak)
            .putInt("theme", engine.themeIndex)
            .putString("field", fieldText(engine))
            .putString("pieces", piecesText(engine))
            .apply()
    }

    private fun saveChallenge(engine: GameEngine) {
        prefs.edit()
            .putInt("ch_key", engine.challengeKey)
            .putInt("ch_draws", engine.randomDraws)
            .putBoolean("ch_over", engine.gameOver)
            .putInt("ch_score", engine.score)
            .putInt("ch_combo", engine.streak)
            .putInt("ch_theme", engine.themeIndex)
            .putString("ch_field", fieldText(engine))
            .putString("ch_pieces", piecesText(engine))
            .apply()
    }

    private fun fieldText(engine: GameEngine) = engine.colors.joinToString(",")

    private fun piecesText(engine: GameEngine) = engine.pieces.joinToString(",") {
        if (it == null) "-" else "${it.shapeId}:${it.colorIdx}"
    }

    /** Memuat progres. Jika tidak ada simpanan valid atau game sebelumnya sudah selesai, mulai game baru. */
    fun load(engine: GameEngine) {
        engine.bestScore = prefs.getInt("best", 0)
        if (engine.mode == Mode.CHALLENGE) loadChallenge(engine) else loadClassic(engine)
    }

    private fun loadClassic(engine: GameEngine) {
        if (prefs.getBoolean("game_over", false) || !restore(engine, "")) {
            engine.newGame()
            return
        }
        engine.gameOver = false
        if (engine.pieces.all { it == null }) engine.spawnPieces()
        if (engine.isStuck()) engine.newGame()
    }

    /**
     * Tantangan hanya boleh dimainkan sekali per hari.
     * Bila simpanannya dari hari lain atau tidak valid, mulai tantangan hari ini dari awal.
     */
    private fun loadChallenge(engine: GameEngine) {
        if (prefs.getInt("ch_key", -1) != engine.challengeKey || !restore(engine, "ch_")) {
            engine.newGame()
            return
        }
        // Majukan generator acak ke posisi terakhir supaya langkah berikutnya sama persis
        engine.fastForwardRandom(prefs.getInt("ch_draws", 0))
        engine.gameOver = prefs.getBoolean("ch_over", false)
        if (!engine.gameOver) {
            if (engine.pieces.all { it == null }) engine.spawnPieces()
            if (engine.isStuck()) engine.gameOver = true
        }
    }

    /** Mengisi [engine] dari simpanan berprefiks [p]. false bila data tidak lengkap atau rusak. */
    private fun restore(engine: GameEngine, p: String): Boolean {
        try {
            val fieldText = prefs.getString(p + "field", null) ?: return false
            val parts = fieldText.split(",")
            if (parts.size != engine.colors.size) return false
            for (i in parts.indices) engine.colors[i] = parts[i].trim().toInt()

            val pieceParts = (prefs.getString(p + "pieces", "") ?: "").split(",")
            if (pieceParts.size != GameConfig.SLOTS) return false
            for (i in 0 until GameConfig.SLOTS) {
                val text = pieceParts[i].trim()
                engine.pieces[i] = if (text == "-" || text.isEmpty()) {
                    null
                } else {
                    val (id, color) = text.split(":")
                    val shapeId = id.toInt()
                    require(shapeId in Shapes.all.indices)
                    Piece(shapeId, color.toInt())
                }
            }
            engine.score = prefs.getInt(p + "score", 0)
            engine.streak = prefs.getInt(p + "combo", 0).coerceAtLeast(0)
            engine.themeIndex = prefs.getInt(p + "theme", 0).coerceIn(0, Themes.all.size - 1)
            engine.tutorialMode = false
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
