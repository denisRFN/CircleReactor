package com.pricelens.circlereactor.vision

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class CircleTarget(
    val id: Int,
    val x: Float,
    val y: Float,
    val radius: Float,
    var baselineR: Float,
    var baselineG: Float,
    var baselineB: Float,
    var lastTapAtMs: Long = 0L
)

data class ActiveTarget(val target: CircleTarget, val score: Float)

class CircleDetector(
    private val minTapIntervalMs: Long = 140L,
    private val activationThreshold: Float = 54f
) {
    private val targets = mutableListOf<CircleTarget>()

    fun targetCount(): Int = targets.size

    fun reset() = targets.clear()

    fun process(bitmap: Bitmap, nowMs: Long): ActiveTarget? {
        if (targets.size != 9) {
            detectTargets(bitmap)
            return null
        }

        var best: ActiveTarget? = null
        for (target in targets) {
            val sample = sampleColor(bitmap, target.x.toInt(), target.y.toInt(), max(4, (target.radius * 0.28f).toInt()))
            val distance = colorDistance(sample, target)
            val brightDelta = brightness(sample.r, sample.g, sample.b) - brightness(target.baselineR, target.baselineG, target.baselineB)
            val score = distance + max(0f, brightDelta) * 0.35f

            if (score < activationThreshold) {
                target.baselineR = target.baselineR * 0.94f + sample.r * 0.06f
                target.baselineG = target.baselineG * 0.94f + sample.g * 0.06f
                target.baselineB = target.baselineB * 0.94f + sample.b * 0.06f
            } else if (nowMs - target.lastTapAtMs >= minTapIntervalMs) {
                if (best == null || score > best.score) best = ActiveTarget(target, score)
            }
        }
        return best
    }

    fun markTapped(target: CircleTarget, nowMs: Long) {
        target.lastTapAtMs = nowMs
    }

    private fun detectTargets(bitmap: Bitmap) {
        val step = 4
        val width = bitmap.width
        val height = bitmap.height
        val gridW = width / step
        val gridH = height / step
        val hot = BooleanArray(gridW * gridH)

        for (gy in 0 until gridH) {
            for (gx in 0 until gridW) {
                val color = bitmap.getPixel(gx * step, gy * step)
                val r = ((color ushr 16) and 0xff).toFloat()
                val g = ((color ushr 8) and 0xff).toFloat()
                val b = (color and 0xff).toFloat()
                val maxC = max(r, max(g, b))
                val minC = min(r, min(g, b))
                val sat = if (maxC == 0f) 0f else (maxC - minC) / maxC
                val br = brightness(r, g, b)
                hot[gy * gridW + gx] = br > 75f && sat > 0.16f
            }
        }

        val seen = BooleanArray(hot.size)
        val components = mutableListOf<Component>()
        val queueX = IntArray(hot.size)
        val queueY = IntArray(hot.size)

        for (gy in 0 until gridH) {
            for (gx in 0 until gridW) {
                val idx = gy * gridW + gx
                if (!hot[idx] || seen[idx]) continue
                var head = 0
                var tail = 0
                queueX[tail] = gx
                queueY[tail] = gy
                tail++
                seen[idx] = true
                var count = 0
                var sumX = 0
                var sumY = 0
                var minX = gx
                var maxX = gx
                var minY = gy
                var maxY = gy

                while (head < tail) {
                    val x = queueX[head]
                    val y = queueY[head]
                    head++
                    count++
                    sumX += x
                    sumY += y
                    minX = min(minX, x)
                    maxX = max(maxX, x)
                    minY = min(minY, y)
                    maxY = max(maxY, y)

                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            if (abs(dx) + abs(dy) != 1) continue
                            val nx = x + dx
                            val ny = y + dy
                            if (nx !in 0 until gridW || ny !in 0 until gridH) continue
                            val ni = ny * gridW + nx
                            if (hot[ni] && !seen[ni]) {
                                seen[ni] = true
                                queueX[tail] = nx
                                queueY[tail] = ny
                                tail++
                            }
                        }
                    }
                }

                if (count >= 18) {
                    val boxW = (maxX - minX + 1) * step
                    val boxH = (maxY - minY + 1) * step
                    val ratio = boxW.toFloat() / max(1, boxH).toFloat()
                    if (ratio in 0.55f..1.85f) {
                        components.add(Component(sumX.toFloat() / count * step, sumY.toFloat() / count * step, max(boxW, boxH) / 2f, count))
                    }
                }
            }
        }

        val selected = components
            .sortedByDescending { it.count }
            .take(14)
            .distinctByDistance(38f)
            .take(9)

        if (selected.size == 9) {
            targets.clear()
            selected.sortedWith(compareBy<Component> { it.cy }.thenBy { it.cx }).forEachIndexed { index, component ->
                val sample = sampleColor(bitmap, component.cx.toInt(), component.cy.toInt(), max(4, (component.radius * 0.25f).toInt()))
                targets.add(CircleTarget(index, component.cx, component.cy, component.radius, sample.r, sample.g, sample.b))
            }
        }
    }

    private fun List<Component>.distinctByDistance(minDistance: Float): List<Component> {
        val out = mutableListOf<Component>()
        for (component in this) {
            if (out.none { distance(it.cx, it.cy, component.cx, component.cy) < minDistance }) out.add(component)
        }
        return out
    }

    private fun sampleColor(bitmap: Bitmap, cx: Int, cy: Int, radius: Int): RgbSample {
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0L
        val minX = max(0, cx - radius)
        val maxX = min(bitmap.width - 1, cx + radius)
        val minY = max(0, cy - radius)
        val maxY = min(bitmap.height - 1, cy + radius)
        var y = minY
        while (y <= maxY) {
            var x = minX
            while (x <= maxX) {
                val color = bitmap.getPixel(x, y)
                r += ((color ushr 16) and 0xff)
                g += ((color ushr 8) and 0xff)
                b += (color and 0xff)
                count++
                x += 2
            }
            y += 2
        }
        return RgbSample(r.toFloat() / count, g.toFloat() / count, b.toFloat() / count)
    }

    private fun colorDistance(sample: RgbSample, target: CircleTarget): Float {
        val dr = sample.r - target.baselineR
        val dg = sample.g - target.baselineG
        val db = sample.b - target.baselineB
        return sqrt(dr * dr + dg * dg + db * db)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }

    private fun brightness(r: Float, g: Float, b: Float): Float = r * 0.299f + g * 0.587f + b * 0.114f

    private data class RgbSample(val r: Float, val g: Float, val b: Float)
    private data class Component(val cx: Float, val cy: Float, val radius: Float, val count: Int)
}
