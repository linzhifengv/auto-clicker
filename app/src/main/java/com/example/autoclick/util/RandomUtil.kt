package com.example.autoclick.util

import kotlin.random.Random

/**
 * 随机化工具类 - 用于模拟人类操作行为
 */
object RandomUtil {

    /**
     * 获取随机延迟时间（正态分布近似）
     */
    fun getRandomDelay(min: Long, max: Long): Long {
        if (min >= max) return min
        // 使用Box-Muller变换产生近似正态分布
        val mean = (min + max) / 2.0
        val stdDev = (max - min) / 6.0  // 99.7%落在范围内
        val gaussian = Random.nextDouble() + Random.nextDouble() + Random.nextDouble() - 1.5
        val value = (mean + gaussian * stdDev * 2).toLong()
        return value.coerceIn(min, max)
    }

    /**
     * 获取随机坐标偏移
     */
    fun getRandomOffset(maxOffset: Int): Float {
        return Random.nextInt(-maxOffset, maxOffset + 1).toFloat()
    }

    /**
     * 获取带偏移的坐标
     */
    fun getOffsetCoordinate(original: Float, maxOffset: Int): Float {
        return original + getRandomOffset(maxOffset)
    }

    /**
     * 随机布尔值（按概率）
     */
    fun randomChance(probability: Double = 0.5): Boolean {
        return Random.nextDouble() < probability
    }

    /**
     * 从列表中随机选取
     */
    fun <T> randomPick(list: List<T>): T? {
        if (list.isEmpty()) return null
        return list[Random.nextInt(list.size)]
    }
}
