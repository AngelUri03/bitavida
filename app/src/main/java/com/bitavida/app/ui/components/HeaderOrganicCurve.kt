package com.bitavida.app.ui.components

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val HeaderOrganicAmplitude = 0.22f

/**
 * Six cubic-Hermite segments with fixed horizontal endpoint tangents.
 * All basis arrays are prepared once; each frame evaluates only four trigonometric phase values
 * and reuses the same point/slope buffers. Coordinates are normalized to the header's height.
 */
internal class HeaderOrganicCurve {
    val segments = 6
    val y = FloatArray(segments + 1)
    val slope = FloatArray(segments + 1)
    private val baseline = FloatArray(segments + 1)
    private val baselineSlope = FloatArray(segments + 1)
    private val envelope = FloatArray(segments + 1)
    private val envelopeSlope = FloatArray(segments + 1)
    private val staticWave = FloatArray(segments + 1)
    private val staticSlope = FloatArray(segments + 1)
    private val firstSin = FloatArray(segments + 1)
    private val firstCos = FloatArray(segments + 1)
    private val secondSin = FloatArray(segments + 1)
    private val secondCos = FloatArray(segments + 1)
    private val leftBend = FloatArray(segments + 1)
    private val leftBendSlope = FloatArray(segments + 1)
    private val rightBend = FloatArray(segments + 1)
    private val rightBendSlope = FloatArray(segments + 1)

    init {
        for (index in 0..segments) {
            val t = index.toDouble() / segments
            val rest = 1.0 - t
            baseline[index] = (0.58 - 0.06 * t * t * (3.0 - 2.0 * t)).toFloat()
            baselineSlope[index] = (-0.36 * t * rest).toFloat()
            envelope[index] = (16.0 * t * t * rest * rest).toFloat()
            envelopeSlope[index] = (32.0 * t * rest * (1.0 - 2.0 * t)).toFloat()
            staticWave[index] = (0.58 * sin(4.0 * PI * t + 0.35) +
                0.26 * sin(7.0 * PI * t - 0.8)).toFloat()
            staticSlope[index] = (0.58 * 4.0 * PI * cos(4.0 * PI * t + 0.35) +
                0.26 * 7.0 * PI * cos(7.0 * PI * t - 0.8)).toFloat()
            firstSin[index] = sin(2.0 * PI * t).toFloat()
            firstCos[index] = cos(2.0 * PI * t).toFloat()
            secondSin[index] = sin(5.0 * PI * t + 0.2).toFloat()
            secondCos[index] = cos(5.0 * PI * t + 0.2).toFloat()
            leftBend[index] = (32.0 * t * t * rest * rest * rest * rest * rest * rest).toFloat()
            leftBendSlope[index] = (64.0 * t * rest * rest * rest * rest * rest * rest -
                192.0 * t * t * rest * rest * rest * rest * rest).toFloat()
            rightBend[index] = (32.0 * rest * rest * t * t * t * t * t * t).toFloat()
            rightBendSlope[index] = (-64.0 * rest * t * t * t * t * t * t +
                192.0 * rest * rest * t * t * t * t * t).toFloat()
        }
        sample(0f)
    }

    fun sample(phase: Float) {
        val cycle = if (phase.isFinite()) ((phase % 2f) + 2f) % 2f else 0f
        val angle = PI * cycle
        val sine = sin(angle).toFloat()
        val cosine = cos(angle).toFloat()
        val secondSine = sin(2.0 * angle).toFloat()
        val secondCosine = cos(2.0 * angle).toFloat()
        val head = headerFlowRoundTripPosition(cycle)
        val left = (1f - head) * (1f - head) * (1f - head) * (1f - head)
        val right = head * head * head * head
        for (index in 0..segments) {
            // Relative to phase zero, so the reduced-motion shape is the exact resting geometry.
            val first = firstSin[index] * (cosine - 1f) - firstCos[index] * sine
            val firstDerivative = (2f * PI.toFloat()) *
                (firstCos[index] * (cosine - 1f) + firstSin[index] * sine)
            val second = secondSin[index] * (secondCosine - 1f) + secondCos[index] * secondSine
            val secondDerivative = (5f * PI.toFloat()) *
                (secondCos[index] * (secondCosine - 1f) - secondSin[index] * secondSine)
            val wave = staticWave[index] + 0.14f * first + 0.08f * second
            val derivative = staticSlope[index] + 0.14f * firstDerivative + 0.08f * secondDerivative
            // A small nearby curve gesture acknowledges the reversal; endpoint coordinates never move.
            val bend = 0.5f * secondSine * (left * leftBend[index] - right * rightBend[index])
            val bendDerivative = 0.5f * secondSine *
                (left * leftBendSlope[index] - right * rightBendSlope[index])
            y[index] = baseline[index] + HeaderOrganicAmplitude * (envelope[index] * wave + bend)
            slope[index] = baselineSlope[index] +
                HeaderOrganicAmplitude * (envelopeSlope[index] * wave + envelope[index] * derivative + bendDerivative)
        }
    }

    fun firstControlY(segment: Int): Float = y[segment] + slope[segment] / (3f * segments)
    fun secondControlY(segment: Int): Float = y[segment + 1] - slope[segment + 1] / (3f * segments)
}
