package com.karim.foodrun.shared

import kotlin.math.pow

/** Pointer is at 12 o'clock. Slice zero is centered there before the first spin. */
class SpinPlan(
    val winnerIndex: Int,
    count: Int,
    val startRotation: Double,
    turns: Int,
    landingOffset: Double,
) {
    val endRotation: Double

    init {
        require(count > 0 && winnerIndex in 0 until count)
        require(turns >= 0 && landingOffset in -0.49..0.49 && startRotation.isFinite())
        val slice = 360.0 / count
        val target = normalized(-winnerIndex * slice + landingOffset * slice)
        endRotation = startRotation + turns * 360 + normalized(target - normalized(startRotation))
    }

    fun rotationAt(progress: Double): Double {
        val eased = 1 - (1 - progress.coerceIn(0.0, 1.0)).pow(4)
        return startRotation + (endRotation - startRotation) * eased
    }

    companion object {
        fun normalized(angle: Double): Double = ((angle % 360) + 360) % 360

        fun indexAtPointer(rotation: Double, count: Int): Int {
            if (count <= 0) return 0
            val slice = 360.0 / count
            return (normalized(-rotation + slice / 2) / slice).toInt() % count
        }
    }
}
