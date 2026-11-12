package id.hanzdev.susunblock.game

import java.util.Calendar

/** Tantangan: tanggal yang sama menghasilkan seed dan target yang sama untuk semua pemain. */
object Challenge {

    /** Kunci tanggal lokal berformat yyyymmdd, mis. 20261112. */
    fun todayKey(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
    }

    /** Target skor untuk tanggal [key]: 1500 sampai 3000, kelipatan 250. */
    fun targetFor(key: Int): Int = 1500 + 250 * java.util.Random(key.toLong()).nextInt(7)
}
