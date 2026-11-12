package id.hanzdev.susunblock.game

import kotlin.random.Random

/**
 * Generator acak berseed yang menghitung jumlah pemanggilannya.
 * Dengan begitu, tantangan bisa dilanjutkan persis dari posisi terakhir setelah aplikasi ditutup.
 */
class ReplayRandom(seed: Long) : Random() {
    private val base = java.util.Random(seed)

    /** Berapa kali generator sudah dipakai. */
    var draws = 0
        private set

    override fun nextBits(bitCount: Int): Int {
        draws++
        return base.nextInt() ushr (32 - bitCount)
    }

    /** Memajukan generator sebanyak [n] langkah, dipakai saat memuat permainan yang tersimpan. */
    fun skip(n: Int) {
        repeat(n) { nextBits(32) }
    }
}
