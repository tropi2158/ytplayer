package com.example.playlist

import kotlin.math.pow

/**
 * 오디오 비주얼라이저용 스펙트럼 (PC).
 * JavaFX 재생기가 알려주는 주파수별 세기(dB)를, 귀에 맞는 로그 간격 48칸으로 묶어요.
 */
object Spectrum {
    const val BANDS = 48
    const val RAW = 256
    private val levels = FloatArray(BANDS)
    @Volatile private var stamp = 0L

    /** JavaFX 스레드에서 호출 */
    fun update(mags: FloatArray) {
        val nyq = 22050f
        val per = nyq / RAW
        val fMin = 40f
        val fMax = 16000f
        val out = FloatArray(BANDS)
        for (k in 0 until BANDS) {
            val f0 = fMin * (fMax / fMin).pow(k / BANDS.toFloat())
            val f1 = fMin * (fMax / fMin).pow((k + 1) / BANDS.toFloat())
            val i0 = (f0 / per).toInt().coerceIn(0, RAW - 1)
            val i1 = (f1 / per).toInt().coerceIn(i0 + 1, RAW)
            var peak = -80f
            for (i in i0 until i1) if (mags[i] > peak) peak = mags[i]
            val tilt = 6f * k / BANDS
            out[k] = ((peak + tilt + 66f) / 58f).coerceIn(0f, 1f)
        }
        synchronized(levels) { System.arraycopy(out, 0, levels, 0, BANDS) }
        stamp = System.nanoTime()
    }

    fun current(out: FloatArray) {
        // 0.3초 넘게 새 값이 없으면 (멈춤 등) 0으로
        if (System.nanoTime() - stamp > 300_000_000L) { java.util.Arrays.fill(out, 0f); return }
        synchronized(levels) { System.arraycopy(levels, 0, out, 0, BANDS) }
    }
}
