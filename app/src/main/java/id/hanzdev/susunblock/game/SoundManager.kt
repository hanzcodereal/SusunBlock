package id.hanzdev.susunblock.game

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

/**
 * Efek suara sederhana yang dibuat langsung lewat kode (tanpa berkas audio),
 * sehingga tidak ada masalah lisensi aset suara.
 */
class SoundManager(private val save: SaveManager) {
    private val pool = Executors.newCachedThreadPool()
    private val sampleRate = 22050

    fun pickup() = play(intArrayOf(700), 35)
    fun place() = play(intArrayOf(330), 70)
    fun button() = play(intArrayOf(520), 50)
    fun lines(count: Int) {
        val n = (count + 2).coerceAtMost(6)
        play(IntArray(n) { 440 + it * 110 }, 70)
    }
    fun allClear() = play(intArrayOf(523, 659, 784, 1047, 1319), 90)
    fun gameOver() = play(intArrayOf(392, 330, 262, 196), 170)
    fun newBest() = play(intArrayOf(523, 659, 784, 1047), 110)

    private fun play(freqs: IntArray, msEach: Int) {
        if (!save.sfxOn) return
        pool.execute {
            try {
                val perNote = sampleRate * msEach / 1000
                val samples = ShortArray(perNote * freqs.size)
                var pos = 0
                for (f in freqs) {
                    for (i in 0 until perNote) {
                        val t = i.toDouble() / sampleRate
                        val attack = (i / (sampleRate * 0.005)).coerceAtMost(1.0)
                        val release = ((perNote - i) / (perNote * 0.4)).coerceAtMost(1.0)
                        val env = attack * release
                        samples[pos++] = (sin(2.0 * PI * f * t) * 0.3 * 32767 * env).toInt().toShort()
                    }
                }
                @Suppress("DEPRECATION")
                val track = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    samples.size * 2,
                    AudioTrack.MODE_STATIC
                )
                track.write(samples, 0, samples.size)
                track.play()
                Thread.sleep(msEach.toLong() * freqs.size + 80)
                track.release()
            } catch (e: Exception) {
                // Suara bersifat opsional, abaikan kesalahan.
            }
        }
    }
}
