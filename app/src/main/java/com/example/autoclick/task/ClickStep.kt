package com.example.autoclick.task

/**
 * 步骤类型枚举
 */
enum class StepType {
    FIND_AND_CLICK,      // 通过文本查找并点击
    COORDINATE_CLICK,    // 坐标点击
    WAIT,                // 等待指定时间
    WAIT_FOR_TEXT,       // 等待指定文本出现
    WAIT_FOR_TEXT_GONE,  // 等待指定文本消失
    SWIPE,               // 滑动操作
    BACK,                // 返回操作
    FIND_AND_CLICK_ANY,  // 查找多个文本中的任一并点击
    WAIT_AND_CLICK,      // 等待文本出现后立即点击（无延迟）
    WAIT_AND_CLICK_ANY,  // 等待任一文本出现后立即点击
    REPEAT_COORDINATE_CLICK, // 重复坐标点击直到超时（用于倒计时按钮）
    WAIT_COUNTDOWN           // 等待倒计时结束（监控屏幕倒计时文本消失）
}

/**
 * 点击步骤数据模型
 */
data class ClickStep(
    val id: Int,
    val name: String = "",               // 步骤名称描述
    val type: StepType,
    val targetTexts: List<String> = emptyList(), // 目标文本列表
    val x: Float = 0f,
    val y: Float = 0f,
    val endX: Float = 0f,               // 滑动结束X
    val endY: Float = 0f,               // 滑动结束Y
    val delayAfter: Long = 1000,        // 执行后延迟(ms)
    val timeout: Long = 10000,          // 超时时间(ms)
    val retryCount: Int = 3,            // 重试次数
    val optional: Boolean = false,      // 是否可选步骤（失败不中断流程）
    val exactMatch: Boolean = false     // 是否使用精确文本匹配（而非子串匹配）
)
