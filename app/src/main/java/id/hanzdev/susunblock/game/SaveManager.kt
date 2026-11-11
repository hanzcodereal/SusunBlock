package id.hanzdev.susunblock.game

import android.content.Context

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

    val bestScore: Int get() = prefs.getInt("best", 0)

    fun save(engine: GameEngine) {
        if (engine.tutorialMode) return
        val piecesText = engine.pieces.joinToString(",") {
            if (it == null) "-" else "${it.shapeId}:${it.colorIdx}"
        }
        prefs.edit()
            .putInt("best", engine.bestScore)
            .putBoolean("game_over", engine.gameOver)
            .putInt("score", engine.score)
            .putInt("combo", engine.combo)
            .putInt("combo_reset", engine.comboReset)
            .putInt("theme", engine.themeIndex)
            .putString("field", engine.colors.joinToString(","))
            .putString("pieces", piecesText)
            .apply()
    }

    /** Memuat progres. Jika tidak ada simpanan valid atau game sebelumnya sudah selesai, mulai game baru. */
    fun load(engine: GameEngine) {
        val best = prefs.getInt("best", 0)
        val savedOver = prefs.getBoolean("game_over", false)
        val fieldText = prefs.getString("field", null)
        if (savedOver || fieldText == null) {
            engine.newGame()
            engine.bestScore = best
            return
        }
        try {
            val parts = fieldText.split(",")
            require(parts.size == engine.colors.size)
            for (i in parts.indices) engine.colors[i] = parts[i].trim().toInt()

            val pieceParts = (prefs.getString("pieces", "") ?: "").split(",")
            require(pieceParts.size == GameConfig.SLOTS)
            for (i in 0 until GameConfig.SLOTS) {
                val p = pieceParts[i].trim()
                engine.pieces[i] = if (p == "-" || p.isEmpty()) {
                    null
                } else {
                    val (id, color) = p.split(":")
                    val shapeId = id.toInt()
                    require(shapeId in Shapes.all.indices)
                    Piece(shapeId, color.toInt())
                }
            }
            engine.bestScore = best
            engine.score = prefs.getInt("score", 0)
            engine.combo = prefs.getInt("combo", -1)
            engine.comboReset = prefs.getInt("combo_reset", 0)
            engine.themeIndex = prefs.getInt("theme", 0).coerceIn(0, Themes.all.size - 1)
            engine.gameOver = false
            engine.tutorialMode = false
            if (engine.pieces.all { it == null }) engine.spawnPieces()
            if (engine.isStuck()) {
                engine.newGame()
                engine.bestScore = best
            }
        } catch (e: Exception) {
            engine.newGame()
            engine.bestScore = best
        }
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
