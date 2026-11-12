package id.hanzdev.susunblock.game

import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.random.Random

/** Mode permainan: klasik (tanpa batas) atau tantangan (urutan blok sama untuk semua pemain). */
enum class Mode { CLASSIC, CHALLENGE }

/**
 * Logika inti permainan: papan 8x8, skor ala Block Blast, kombo, dan pembuat blok.
 * Tidak bergantung pada Android sehingga mudah diuji.
 */
class GameEngine(
    val mode: Mode = Mode.CLASSIC,
    /** Tanggal tantangan (yyyymmdd). Hanya dipakai di mode CHALLENGE. */
    val challengeKey: Int = 0
) {
    private val replay: ReplayRandom? = if (mode == Mode.CHALLENGE) ReplayRandom(challengeKey.toLong()) else null
    private val random: Random = replay ?: Random.Default

    /** Target skor tantangan (0 di mode klasik). */
    val target: Int = if (mode == Mode.CHALLENGE) Challenge.targetFor(challengeKey) else 0

    /** Jumlah pemanggilan acak sejauh ini, disimpan agar tantangan bisa dilanjutkan persis. */
    val randomDraws: Int get() = replay?.draws ?: 0

    /** Memajukan generator acak sebanyak [n] langkah saat memuat permainan. */
    fun fastForwardRandom(n: Int) {
        replay?.skip(n)
    }

    /** Indeks warna tiap sel, -1 = kosong. */
    val colors = IntArray(GameConfig.ROWS * GameConfig.COLS) { -1 }
    val pieces = arrayOfNulls<Piece>(GameConfig.SLOTS)

    var score = 0
    var bestScore = 0
    /** Jumlah langkah beruntun yang menghapus garis. 0 = tidak ada kombo. */
    var streak = 0
    var gameOver = false
    var tutorialMode = false
    var themeIndex = 0

    // Hasil pencarian tiga blok (lihat findTriplet)
    private val seq = IntArray(GameConfig.SLOTS)
    private val bestSeq = IntArray(GameConfig.SLOTS)
    private var bestDepth = 0
    private var budget = 0

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
        // (supaya baris dan kolom yang berpotongan tidak dihitung dua kali).
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

        var gained = 0
        var streakShown = 0
        if (!tutorialMode) {
            // Pola Block Blast: poin per sel yang diletakkan, lalu poin per sel yang terhapus
            // dikali jumlah garis, dikali pengali kombo. Kombo putus bila langkah tidak menghapus apa pun.
            gained += n
            if (lines > 0) {
                streak++
                val comboMult = 1f + GameConfig.COMBO_STEP * (streak - 1)
                gained += (cleared.size * GameConfig.POINTS_PER_CLEARED_CELL * lines * comboMult).roundToInt()
                if (allClear) gained += GameConfig.ALL_CLEAR_BONUS
                streakShown = streak
            } else {
                streak = 0
            }
            score += gained
            if (mode == Mode.CLASSIC && score > bestScore) bestScore = score
        }

        return MoveResult(
            placedCount = n,
            placedRows = placedRows,
            placedCols = placedCols,
            colorIdx = piece.colorIdx,
            rowsCleared = fullRows.size,
            colsCleared = fullCols.size,
            cleared = cleared,
            allClear = allClear && !tutorialMode,
            gained = gained,
            streak = streakShown
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
            if (fittingSpots(free, piece.shape).isNotEmpty()) return true
        }
        return false
    }

    /** true bila masih ada blok di slot tetapi tidak ada satu pun yang muat. */
    fun isStuck(): Boolean = pieces.any { it != null } && !hasAnyMove()

    // ------------------------------------------------------- Permainan baru

    fun newGame() {
        colors.fill(-1)
        score = 0
        streak = 0
        themeIndex = 0
        gameOver = false
        tutorialMode = false
        for (i in pieces.indices) pieces[i] = null
        if (mode == Mode.CHALLENGE) prefillBoard()
        spawnPieces()
    }

    /**
     * Papan awal tantangan: beberapa blok acak sudah terpasang.
     * Penempatan yang langsung menghapus garis dilewati, supaya tidak ada baris yang hilang di awal.
     */
    private fun prefillBoard() {
        val target = PREFILL_MIN_CELLS + random.nextInt(PREFILL_MAX_CELLS - PREFILL_MIN_CELLS + 1)
        var filled = 0
        var attempts = 0
        while (filled < target && attempts < 200) {
            attempts++
            val shape = Shapes.all[randomShapeId()]
            if (filled + shape.count > target) continue
            val spots = fittingSpots(freeGrid(), shape).filter { spot ->
                simulatePlace(freeGrid(), shape, spot / GameConfig.COLS, spot % GameConfig.COLS) == 0
            }
            if (spots.isEmpty()) continue
            val spot = spots[random.nextInt(spots.size)]
            val row = spot / GameConfig.COLS
            val col = spot % GameConfig.COLS
            val color = random.nextInt(Themes.all[themeIndex].blockColors.size)
            for (y in 0 until shape.sizeY) for (x in 0 until shape.sizeX) {
                if (shape.filled(x, y)) colors[(row + y) * GameConfig.COLS + col + x] = color
            }
            filled += shape.count
        }
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

    // ------------------------------------------------------ Pembuat blok

    /**
     * Mengisi semua slot dengan blok baru.
     * Mengembalikan false bila tidak ada satu pun blok yang bisa ditempatkan di papan (game over).
     */
    fun spawnPieces(): Boolean {
        val generated = if (mode == Mode.CHALLENGE) generateHeuristic() else generateBlockBlast()
        if (generated == null) {
            gameOver = true
            return false
        }
        for (i in 0 until GameConfig.SLOTS) pieces[i] = generated[i]
        return true
    }

    /** Mode klasik: acak berbobot, tiga blok dijamin bisa ditempatkan berurutan. null bila tidak ada yang muat. */
    private fun generateBlockBlast(): List<Piece>? {
        findTriplet()
        if (bestDepth == 0) return null
        val ids = IntArray(GameConfig.SLOTS) { i -> if (i < bestDepth) bestSeq[i] else randomShapeId() }
        ids.shuffle(random)
        val colorPool = (0 until Themes.all[themeIndex].blockColors.size).shuffled(random)
        return List(GameConfig.SLOTS) { i -> Piece(ids[i], colorPool[i % colorPool.size]) }
    }

    /**
     * Mode tantangan: heuristik kesulitan. Tiap blok dipilih dengan mempertimbangkan kecocokan penempatan,
     * ukuran terhadap kepadatan papan, dan tingkat kesulitan yang naik mengikuti skor.
     */
    private fun generateHeuristic(): List<Piece>? {
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
            Shapes.baseWeight(c.shapeId) * sizeWeight(shape.count, busyFraction, d) *
                (1.0 + d * awkwardness(shape)) * (1.0 + fit * norm)
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

    private class Candidate(val shapeId: Int, val row: Int, val col: Int, val score: Float, val lines: Int)

    /**
     * Mencari tiga blok yang bisa ditempatkan berurutan di papan saat ini.
     *
     * Bentuk dipilih acak berbobot [Shapes.baseWeight], posisinya juga acak. Bila jalan buntu,
     * pencarian mundur (backtracking) mencoba posisi atau bentuk lain, dibatasi [GameConfig.SEARCH_BUDGET].
     * Bentuk boleh berulang, seperti di Block Blast. Bila tidak ada tiga blok yang muat,
     * [bestDepth] berisi jumlah blok terbanyak yang masih bisa dimainkan; sisanya diisi acak.
     */
    private fun findTriplet() {
        bestDepth = 0
        budget = GameConfig.SEARCH_BUDGET
        search(freeGrid(), 0)
    }

    private fun search(free: BooleanArray, depth: Int): Boolean {
        if (depth > bestDepth) {
            bestDepth = depth
            seq.copyInto(bestSeq, 0, 0, depth)
        }
        if (depth == GameConfig.SLOTS) return true
        for (shape in weightedOrder()) {
            if (budget <= 0) return false
            for (spot in fittingSpots(free, shape).shuffled(random)) {
                if (--budget <= 0) return false
                val next = free.copyOf()
                simulatePlace(next, shape, spot / GameConfig.COLS, spot % GameConfig.COLS)
                seq[depth] = shape.id
                if (search(next, depth + 1)) return true
            }
        }
        return false
    }

    /** Semua bentuk dalam urutan acak berbobot (exponential race: peluang pertama sebanding dengan bobot). */
    private fun weightedOrder(): List<BlockShape> =
        Shapes.all
            .map { s -> s to -ln(1.0 - random.nextDouble()) / Shapes.baseWeight(s.id) }
            .sortedBy { it.second }
            .map { it.first }

    private fun randomShapeId(): Int = weightedOrder().first().id

    /** Semua posisi (indeks row*COLS+col) tempat [shape] muat di papan [free]. */
    private fun fittingSpots(free: BooleanArray, shape: BlockShape): List<Int> {
        val out = ArrayList<Int>()
        for (row in 0..GameConfig.ROWS - shape.sizeY) {
            for (col in 0..GameConfig.COLS - shape.sizeX) {
                if (canPlaceAt(free, shape, row, col)) out.add(row * GameConfig.COLS + col)
            }
        }
        return out
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

private const val PREFILL_MIN_CELLS = 10
private const val PREFILL_MAX_CELLS = 18
