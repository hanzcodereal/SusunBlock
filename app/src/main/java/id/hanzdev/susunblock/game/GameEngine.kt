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

    private class Candidate(val shapeId: Int, val row: Int, val col: Int, val score: Float, val lines: Int)

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

    // ------------------------------------------- Pembuat blok cerdas

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

    /**
     * Membuat 3 blok yang masuk akal:
     *  - ketiganya pasti bisa ditempatkan berurutan di papan saat ini,
     *  - bentuknya berbeda-beda dan warnanya tidak kembar,
     *  - papan longgar -> lebih banyak blok besar, papan padat -> lebih banyak blok kecil,
     *  - papan mulai penuh -> blok pertama kadang diusahakan bisa menghapus garis,
     *  - penempatan yang rapi (menempel, tanpa lubang terkurung) lebih sering dipilih,
     *  - makin tinggi skor, makin sulit: bantuan berkurang, blok kecil lebih jarang,
     *    dan bentuk yang canggung (diagonal, L besar, garis 5) lebih sering muncul.
     */
    private fun generatePieces(): List<Piece>? {
        val temp = freeGrid()
        val chosen = ArrayList<Int>()
        val result = ArrayList<Piece>()
        val colorPool = (0 until Themes.all[themeIndex].blockColors.size).shuffled(random)
        val difficulty = currentDifficulty()

        for (slot in 0 until GameConfig.SLOTS) {
            val cand = pickShape(temp, chosen, slot, difficulty) ?: return null
            chosen.add(cand.shapeId)
            result.add(Piece(cand.shapeId, colorPool[slot % colorPool.size]))
            simulatePlace(temp, Shapes.all[cand.shapeId], cand.row, cand.col)
        }
        result.shuffle(random)
        return result
    }

    /** Tingkat kesulitan 0..1: naik bertahap mengikuti skor. */
    private fun currentDifficulty(): Float {
        val progress = (score / GameConfig.DIFFICULTY_FULL_SCORE).coerceIn(0f, 1f)
        return GameConfig.DIFFICULTY_BASE + (1f - GameConfig.DIFFICULTY_BASE) * progress
    }

    private fun pickShape(free: BooleanArray, chosen: List<Int>, slot: Int, difficulty: Float): Candidate? {
        var candidates = Shapes.all
            .filter { it.id !in chosen }
            .mapNotNull { bestPlacement(free, it) }
        if (candidates.isEmpty()) candidates = Shapes.all.mapNotNull { bestPlacement(free, it) }
        if (candidates.isEmpty()) return null

        val busyFraction = free.count { !it } / free.size.toFloat()

        // Papan mulai penuh: kadang beri kesempatan menghapus garis (makin jarang saat kesulitan naik)
        val helperChance = 0.8f - 0.5f * difficulty
        if (slot == 0 && busyFraction >= GameConfig.HELPER_BUSY_FRACTION && random.nextFloat() < helperChance) {
            val clearing = candidates.filter { it.lines > 0 }
            if (clearing.isNotEmpty()) candidates = clearing
        }

        val minScore = candidates.minOf { it.score }
        val maxScore = candidates.maxOf { it.score }
        val range = (maxScore - minScore).coerceAtLeast(1f)

        val d = difficulty.toDouble()
        val fit = GameConfig.FIT_WEIGHT * (1.0 - 0.6 * d)
        val weights = DoubleArray(candidates.size) { i ->
            val c = candidates[i]
            val shape = Shapes.all[c.shapeId]
            val norm = ((c.score - minScore) / range).toDouble()
            sizeWeight(shape.count, busyFraction, d) * (1.0 + d * awkwardness(shape)) * (1.0 + fit * norm)
        }

        var r = random.nextDouble() * weights.sum()
        for (i in candidates.indices) {
            r -= weights[i]
            if (r <= 0) return candidates[i]
        }
        return candidates.last()
    }

    /** Bobot menurut ukuran blok: papan kosong -> blok besar, papan padat -> blok kecil. */
    private fun sizeWeight(count: Int, busyFraction: Float, difficulty: Double): Double {
        val t = (busyFraction / 0.7f).coerceIn(0f, 1f).toDouble()
        return when {
            count >= 8 -> 0.8 * (1.0 - 0.65 * t)
            count >= 5 -> 1.0 - 0.65 * t
            count >= 3 -> 1.0
            else -> (0.35 + 0.95 * t) * (1.0 - 0.5 * difficulty)
        }
    }

    /** Seberapa canggung bentuknya: makin banyak ruang kosong di kotak pembungkusnya, makin canggung. */
    private fun awkwardness(shape: BlockShape): Double {
        if (shape.count < 3) return 0.0
        var a = (1.0 - shape.count.toDouble() / (shape.sizeX * shape.sizeY)) * 1.2
        if (shape.id == 14 || shape.id == 15) a += 0.5 // garis lurus 5 sel
        return a
    }

    /** Posisi terbaik untuk [shape] di papan [free], atau null bila tidak muat di mana pun. */
    private fun bestPlacement(free: BooleanArray, shape: BlockShape): Candidate? {
        var best: Candidate? = null
        for (row in 0..GameConfig.ROWS - shape.sizeY) {
            for (col in 0..GameConfig.COLS - shape.sizeX) {
                if (!canPlaceAt(free, shape, row, col)) continue
                val (score, lines) = evaluate(free, shape, row, col)
                if (best == null || score > best.score) {
                    best = Candidate(shape.id, row, col, score, lines)
                }
            }
        }
        return best
    }

    /** Nilai sebuah penempatan: hapus garis (besar), menempel rapi (bagus), lubang terkurung (jelek). */
    private fun evaluate(free: BooleanArray, shape: BlockShape, row: Int, col: Int): Pair<Float, Int> {
        var contact = 0
        var exposed = 0
        for (y in 0 until shape.sizeY) {
            for (x in 0 until shape.sizeX) {
                if (!shape.filled(x, y)) continue
                val r = row + y
                val c = col + x
                for (d in 0 until 4) {
                    val nr = r + DR[d]
                    val nc = c + DC[d]
                    val ly = nr - row
                    val lx = nc - col
                    if (ly in 0 until shape.sizeY && lx in 0 until shape.sizeX && shape.filled(lx, ly)) continue
                    val blocked = nr < 0 || nr >= GameConfig.ROWS || nc < 0 || nc >= GameConfig.COLS ||
                        !free[nr * GameConfig.COLS + nc]
                    if (blocked) contact++ else exposed++
                }
            }
        }
        val temp = free.copyOf()
        val lines = simulatePlace(temp, shape, row, col)
        val score = lines * GameConfig.LINE_CLEAR_SCORE + contact - 0.5f * exposed + holePenalty(temp)
        return Pair(score, lines)
    }

    /** Hukuman untuk area kosong kecil yang terkurung (sulit terisi blok mana pun). */
    private fun holePenalty(free: BooleanArray): Float {
        val visited = BooleanArray(free.size)
        val stack = IntArray(free.size)
        var penalty = 0f
        for (start in free.indices) {
            if (!free[start] || visited[start]) continue
            var sp = 0
            stack[sp++] = start
            visited[start] = true
            var size = 0
            while (sp > 0) {
                val cur = stack[--sp]
                size++
                val r = cur / GameConfig.COLS
                val c = cur % GameConfig.COLS
                for (d in 0 until 4) {
                    val nr = r + DR[d]
                    val nc = c + DC[d]
                    if (nr < 0 || nr >= GameConfig.ROWS || nc < 0 || nc >= GameConfig.COLS) continue
                    val ni = nr * GameConfig.COLS + nc
                    if (free[ni] && !visited[ni]) {
                        visited[ni] = true
                        stack[sp++] = ni
                    }
                }
            }
            penalty -= when (size) {
                1 -> GameConfig.HOLE_1_PENALTY
                2 -> GameConfig.HOLE_2_PENALTY
                3 -> GameConfig.HOLE_3_PENALTY
                else -> 0f
            }
        }
        return penalty
    }

    /** Menaruh blok di grid simulasi (true = kosong), menghapus baris/kolom penuh, mengembalikan jumlah garis. */
    private fun simulatePlace(free: BooleanArray, shape: BlockShape, row: Int, col: Int): Int {
        val rows = GameConfig.ROWS
        val cols = GameConfig.COLS
        for (y in 0 until shape.sizeY) for (x in 0 until shape.sizeX) {
            if (shape.filled(x, y)) free[(row + y) * cols + col + x] = false
        }
        val fullRows = BooleanArray(rows) { r -> (0 until cols).all { !free[r * cols + it] } }
        val fullCols = BooleanArray(cols) { c -> (0 until rows).all { !free[it * cols + c] } }
        for (r in 0 until rows) if (fullRows[r]) for (c in 0 until cols) free[r * cols + c] = true
        for (c in 0 until cols) if (fullCols[c]) for (r in 0 until rows) free[r * cols + c] = true
        return fullRows.count { it } + fullCols.count { it }
    }
}

private val DR = intArrayOf(-1, 1, 0, 0)
private val DC = intArrayOf(0, 0, -1, 1)
