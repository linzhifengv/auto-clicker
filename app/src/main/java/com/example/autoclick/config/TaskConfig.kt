package com.example.autoclick.config

import com.example.autoclick.task.ClickStep

/**
 * 任务配置数据类
 */
data class TaskConfig(
    val name: String = "默认任务",
    val steps: List<ClickStep> = emptyList(),
    val loopCount: Int = -1,            // -1 表示无限循环
    val loopDelay: Long = 5000,         // 每轮循环间隔(ms)
    val minRandomDelay: Long = 200,     // 最小随机延迟(ms)
    val maxRandomDelay: Long = 800,     // 最大随机延迟(ms)
    val coordinateOffset: Int = 5,      // 坐标随机偏移范围(px)
    val failureAction: FailureAction = FailureAction.RETRY_THEN_SKIP
)

/**
 * 失败处理策略
 */
enum class FailureAction {
    STOP,              // 停止整个任务
    RETRY_THEN_SKIP,   // 重试后跳过
    RETRY_THEN_STOP,   // 重试后停止
    SKIP               // 直接跳过
}
