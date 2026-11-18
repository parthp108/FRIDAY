package com.health.friday.stats

import kotlin.math.abs
import kotlin.math.sqrt

object StatEngine {

    fun mean(v: List<Double>): Double = if (v.isEmpty()) 0.0 else v.average()

    /** Sample standard deviation (n-1). 0.0 for fewer than 2 values. */
    fun stdDev(v: List<Double>): Double {
        if (v.size < 2) return 0.0
        val m = v.average()
        return sqrt(v.sumOf { (it - m) * (it - m) } / (v.size - 1))
    }

    /**
     * Pearson r = sum((x-mx)(y-my)) / sqrt(sum((x-mx)^2) * sum((y-my)^2)).
     * Returns null when it is undefined: unequal sizes, fewer than 3 points,
     * or one series has no variation (e.g. every day identical).
     */
    fun pearson(x: List<Double>, y: List<Double>): Double? {
        if (x.size != y.size || x.size < 3) return null
        val mx = x.average()
        val my = y.average()
        var sxy = 0.0
        var sxx = 0.0
        var syy = 0.0
        for (i in x.indices) {
            val dx = x[i] - mx
            val dy = y[i] - my
            sxy += dx * dy
            sxx += dx * dx
            syy += dy * dy
        }
        if (sxx == 0.0 || syy == 0.0) return null
        return (sxy / sqrt(sxx * syy)).coerceIn(-1.0, 1.0)
    }

    /** Spearman rho = Pearson on average ranks (correct even when values tie). */
    fun spearman(x: List<Double>, y: List<Double>): Double? {
        if (x.size != y.size) return null
        return pearson(ranks(x), ranks(y))
    }

    /** Kept for existing callers; returns 0.0 when undefined. */
    fun spearmanRank(x: List<Double>, y: List<Double>): Double = spearman(x, y) ?: 0.0

    /** Least-squares line y = slope*x + intercept, or null if x has no variation. */
    fun linearRegression(x: List<Double>, y: List<Double>): Pair<Double, Double>? {
        if (x.size != y.size || x.size < 2) return null
        val mx = x.average()
        val my = y.average()
        var num = 0.0
        var den = 0.0
        for (i in x.indices) {
            num += (x[i] - mx) * (y[i] - my)
            den += (x[i] - mx) * (x[i] - mx)
        }
        if (den == 0.0) return null
        val slope = num / den
        return Pair(slope, my - slope * mx)
    }

    private fun ranks(values: List<Double>): List<Double> {
        val sorted = values.mapIndexed { i, v -> i to v }.sortedBy { it.second }
        val ranks = DoubleArray(values.size)
        var i = 0
        while (i < sorted.size) {
            var j = i
            while (j < sorted.size - 1 && sorted[j].second == sorted[j + 1].second) j++
            val avg = (i + j + 2) / 2.0
            for (k in i..j) ranks[sorted[k].first] = avg
            i = j + 1
        }
        return ranks.toList()
    }

    fun interpretCorrelation(r: Double): String {
        val a = abs(r)
        val dir = if (r >= 0) "positive" else "negative"
        return when {
            a >= 0.7 -> "Strong $dir correlation"
            a >= 0.4 -> "Moderate $dir correlation"
            a >= 0.2 -> "Weak $dir correlation"
            else -> "No meaningful correlation"
        }
    }
}
