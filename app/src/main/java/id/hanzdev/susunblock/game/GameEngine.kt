package id.hanzdev.susunblock.game

import kotlin.random.Random

/**
 * Logika inti permainan: papan 8x8, skor, kombo, dan pembuat blok cerdas.
 * Tidak bergantung pada Android sehingga mudah diuji.
 */
class GameEngine(private val random: Random = Random.Default) {

    /** Indeks warna tiap sel, -1 = kosong. */
    val colors = IntArray(GameConfig.ROWS * GameConfig.COLS) { -1 }
    val pieces = arrayOfNulls<Piece>(GameConfig.SLOTS)

    var score = 0
    var bestScore = 0
    var combo = -1
    var comboReset = 0
    var themeIndex = 0
    var gameOver = false
    var tutorialMode = false

    private class Candidate(val shapeId: Int, val row: Int, val col: Int, val score: Int)

    // ---------------------------------------------------------------- Papan

    fun freeGrid(): BooleanArray = BooleanArray(colors.size) { colors[it] < 0 }

    fun canPlace(shape: BlockShape, row: Int, col: Int): Boolean =
        canPlaceAt(freeGrid(), shape, row, col)

    private fun canPlaceAt(free: BooleanArray, shape: BlockShape, row: Int, col: Int): Boolean {
        if (row < 0 || col < 0 || row + shape.sizeY > GameConfig.ROWS || col + shape.sizeX > GameConfig.COLS) {
            return false
        }
        for (y in 0 until shape.sizeY) {
            for (x in 0 until shape.sizeX) {
                if (shape.filled(x, y) && !free[(row + y) * GameConfig.COLS + col + x]) return false
            }
        }
        return true
    }

    /** Menempatkan blok pada [slot]. Blok harus sudah dicek dengan [canPlace]. */
    fun place(slot: Int, row: Int, col: Int): MoveResult {
        val piece = pieces[slot]!!
        val shape = piece.shape
        val rows = GameConfig.ROWS
        val cols = GameConfig.COLS

        val placedRows = IntArray(shape.count)
        val placedCols = IntArray(shape.count)
        var n = 0
        for (y in 0 until shape.sizeY) {
            for (x in 0 until shape.sizeX) {
                if (!shape.filled(x, y)) continue
                colors[(row + y) * cols + col + x] = piece.colorIdx
                placedRows[n] = row + y
                placedCols[n] = col + x
                n++
            }
        }

        val fullRows = (0 until rows).filter { r -> (0 until cols).all { colors[r * cols + it] >= 0 } }
        val fullCols = (0 until cols).filter { c -> (0 until rows).all { colors[it * cols + c] >= 0 } }

        // Catat dulu semua sel yang akan terhapus, baru dihapus
        // (supaya baris dan kolom yang berpotongan tidak saling menghilangkan).
        val cleared = ArrayList<ClearedCell>()
        val seen = HashSet<Int>()
        for (r in fullRows) for (c in 0 until cols) {
            val idx = r * cols + c
            if (seen.add(idx)) cleared.add(ClearedCell(r, c, colors[idx]))
        }
        for (c in fullCols) for (r in 0 until rows) {
            val idx = r * cols + c
            if (seen.add(idx)) cleared.add(ClearedCell(r, c, colors[idx]))
        }
        for (cc in cleared) colors[cc.row * cols + cc.col] = -1

        pieces[slot] = null
        val allClear = colors.all { it < 0 }
        val lines = fullRows.size + fullCols.size

        var comboShown = 0
        if (!tutorialMode) {
            score += n
            score += fullRows.size * GameConfig.LINE_REMOVAL_SCORE * cols
            score += fullCols.size * GameConfig.LINE_REMOVAL_SCORE * rows
            if (lines > 1) score += lines * lines * GameConfig.MULTI_LINE_SCORE

            if (lines > 0) {
                combo += lines
                comboReset = 0
            } else if (combo > -1) {
                comboReset++
                if (comboReset >= GameConfig.RESET_COMBO_AFTER_MOVES) {
                    combo = -1
                    comboReset = 0
                }
            }
            if (combo > 0) score += lines * lines * GameConfig.COMBO_SCORE * combo
            if (allClear) score += GameConfig.ALL_CLEAR_BONUS
            if (combo > 0 && lines > 0) comboShown = combo

            if (score > bestScore) bestScore = score
        }

        return MoveResult(
            placedCount = n,
            placedRows = placedRows,
            placedCols = placedCols,
            colorIdx = piece.colorIdx,
            rowsCleared = fullRows.size,
            colsCleared = fullCols.size,
            cleared = cleared,
            allClear = allClear,
            comboShown = comboShown
        )
    }

    /** Sel terisi yang akan terhapus jika [shape] diletakkan di (row, col). Untuk pratinjau. */
    fun cellsOfPotentialClears(shape: BlockShape, row: Int, col: Int): List<Int> {
        val rows = GameConfig.ROWS
        val cols = GameConfig.COLS
        val temp = freeGrid()
        for (y in 0 until shape.sizeY) for (x in 0 until shape.sizeX) {
            if (shape.filled(x, y)) temp[(row + y) * cols + col + x] = false
        }
        val out = ArrayList<Int>()
        val seen = HashSet<Int>()
        for (r in row until row + shape.sizeY) {
            if ((0 until cols).all { !temp[r * cols + it] }) {
                for (c in 0 until cols) {
                    val idx = r * cols + c
                    if (colors[idx] >= 0 && seen.add(idx)) out.add(idx)
                }
            }
        }
        for (c in col until col + shape.sizeX) {
            if ((0 until rows).all { !temp[it * cols + c] }) {
                for (r in 0 until rows) {
                    val idx = r * cols + c
                    if (colors[idx] >= 0 && seen.add(idx)) out.add(idx)
                }
            }
        }
        return out
    }

    // ------------------------------------------------------------ Game over

    fun hasAnyMove(): Boolean {
        val free = freeGrid()
        for (piece in pieces) {
            if (piece == null) continue
            val shape = piece.shape
            for (row in 0..GameConfig.ROWS - shape.sizeY) {
                for (col in 0..GameConfig.COLS - shape.sizeX) {
                    if (canPlaceAt(free, shape, row, col)) return true
                }
            }
        }
        return false
    }

    /** true bila masih ada blok di slot tetapi tidak ada satu pun yang muat. */
    fun isStuck(): Boolean = pieces.any { it != null } && !hasAnyMove()

    // ------------------------------------------------------- Permainan baru

    fun newGame() {
        colors.fill(-1)
        score = 0
        combo = -1
        comboReset = 0
        themeIndex = 0
        gameOver = false
        tutorialMode = false
        for (i in pieces.indices) pieces[i] = null
        spawnPieces()
    }

    fun nextTheme() {
        themeIndex = (themeIndex + 1) % Themes.all.size
    }

    fun loadTutorial(example: TutorialExample) {
        colors.fill(-1)
        val n = Themes.all[themeIndex].blockColors.size
        for (r in 0 until GameConfig.ROWS) for (c in 0 until GameConfig.COLS) {
            if (example.grid[r][c] == '#') colors[r * GameConfig.COLS + c] = (r + c) % n
        }
        for (i in pieces.indices) pieces[i] = null
        pieces[GameConfig.SLOTS / 2] = Piece(example.shapeId, random.nextInt(n))
    }

    // ------------------------------------------- Pembuat blok cerdas (AI)

    /** Mengisi semua slot dengan blok baru. false bila tidak ada blok yang bisa dibuat (game over). */
    fun spawnPieces(): Boolean {
        val generated = generatePieces()
        if (generated == null) {
            gameOver = true
            return false
        }
        for (i in 0 until GameConfig.SLOTS) pieces[i] = generated[i]
        return true
    }

    private fun generatePieces(): List<Piece>? {
        val temp = freeGrid()
        val result = ArrayList<Piece>()
        val colorCount = Themes.all[themeIndex].blockColors.size
        for (i in 0 until GameConfig.SLOTS) {
            val cand = findBlockForField(temp, Shapes.largeIds)
                ?: findBlockForField(temp, Shapes.smallIds)
                ?: return null
            result.add(Piece(cand.shapeId, random.nextInt(colorCount)))
            simulatePlace(temp, Shapes.all[cand.shapeId], cand.row, cand.col)
        }
        result.shuffle(random)
        return result
    }

    private fun findBlockForField(free: BooleanArray, ids: List<Int>): Candidate? {
        val candidates = ids.mapNotNull { bestPositionFor(free, Shapes.all[it]) }
        if (candidates.isEmpty()) return null

        val bestGrade = candidates.maxOf { it.score }
        val busyPercent = free.count { !it } * 100f / free.size
        val probability =
            if (busyPercent >= GameConfig.REQUIRED_BUSY_PERCENT_FOR_BEST_BLOCK) 1f
            else GameConfig.BETTER_BLOCK_PROBABILITY
        val threshold = bestGrade * probability
        val best = candidates.filter { it.score >= threshold }
        return best[random.nextInt(best.size)]
    }

    private fun bestPositionFor(free: BooleanArray, shape: BlockShape): Candidate? {
        var best: Candidate? = null
        var maxGrade = -1
        for (row in 0..GameConfig.ROWS - shape.sizeY) {
            for (col in 0..GameConfig.COLS - shape.sizeX) {
                if (!canPlaceAt(free, shape, row, col)) continue
                val temp = free.copyOf()
                simulatePlace(temp, shape, row, col)
                // Blok yang lebih besar mendapat nilai lebih tinggi.
                val grade = rateField(temp) + shape.count * GameConfig.BLOCK_SIZE_GRADE_MULTIPLIER
                if (grade > maxGrade) {
                    maxGrade = grade
                    best = Candidate(shape.id, row, col, grade)
                }
            }
        }
        return best
    }

    /** Menaruh blok di grid simulasi (true = kosong) lalu menghapus baris/kolom penuh. */
    private fun simulatePlace(free: BooleanArray, shape: BlockShape, row: Int, col: Int) {
        val rows = GameConfig.ROWS
        val cols = GameConfig.COLS
        for (y in 0 until shape.sizeY) for (x in 0 until shape.sizeX) {
            if (shape.filled(x, y)) free[(row + y) * cols + col + x] = false
        }
        val fullRows = BooleanArray(rows) { r -> (0 until cols).all { !free[r * cols + it] } }
        val fullCols = BooleanArray(cols) { c -> (0 until rows).all { !free[it * cols + c] } }
        for (r in 0 until rows) if (fullRows[r]) for (c in 0 until cols) free[r * cols + c] = true
        for (c in 0 until cols) if (fullCols[c]) for (r in 0 until rows) free[r * cols + c] = true
    }

    /** Skor = jumlah kuadrat (1 + tetangga kosong) tiap sel kosong; dikali 2 jika papan bersih. */
    private fun rateField(free: BooleanArray): Int {
        val rows = GameConfig.ROWS
        val cols = GameConfig.COLS
        var score = 0
        var allClear = true
        for (r in 0 until rows) for (c in 0 until cols) {
            if (!free[r * cols + c]) {
                allClear = false
                continue
            }
            var t = 1
            if (r > 0 && free[(r - 1) * cols + c]) t++
            if (r + 1 < rows && free[(r + 1) * cols + c]) t++
            if (c > 0 && free[r * cols + c - 1]) t++
            if (c + 1 < cols && free[r * cols + c + 1]) t++
            score += t * t
        }
        if (allClear) score *= GameConfig.ALL_CLEAR_FIELD_MULTIPLIER
        return score
    }
}
