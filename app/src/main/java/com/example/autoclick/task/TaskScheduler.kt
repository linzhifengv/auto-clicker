package com.example.autoclick.task

import android.util.Log
import com.example.autoclick.config.FailureAction
import com.example.autoclick.config.TaskConfig
import com.example.autoclick.service.AutoClickService
import com.example.autoclick.util.RandomUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 任务执行状态
 */
enum class TaskState {
    IDLE,       // 空闲
    RUNNING,    // 运行中
    PAUSED,     // 已暂停
    STOPPED     // 已停止
}

/**
 * 任务运行信息
 */
data class TaskRunInfo(
    val state: TaskState = TaskState.IDLE,
    val currentLoop: Int = 0,
    val currentStepIndex: Int = 0,
    val currentStepName: String = "",
    val totalLoops: Int = 0,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val lastLog: String = "",
    val countdownText: String = ""   // 倒计时相关文本，供UI显示
)

/**
 * 任务调度器 - 管理点击任务的执行
 */
class TaskScheduler {

    companion object {
        private const val TAG = "TaskScheduler"
    }

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _runInfo = MutableStateFlow(TaskRunInfo())
    val runInfo: StateFlow<TaskRunInfo> = _runInfo.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private var isPaused = false
    private var config: TaskConfig? = null

    /**
     * 启动任务
     */
    fun start(taskConfig: TaskConfig) {
        if (_runInfo.value.state == TaskState.RUNNING) {
            log("任务已在运行中")
            return
        }

        config = taskConfig
        isPaused = false

        job = scope.launch {
            _runInfo.value = TaskRunInfo(
                state = TaskState.RUNNING,
                totalLoops = taskConfig.loopCount
            )

            // 强制输出初始日志，确保能看到
            log("========== 任务开始 ==========")
            log("任务名称: ${taskConfig.name}")
            log("步骤数量: ${taskConfig.steps.size}")
            log("无障碍服务: ${if (AutoClickService.isConnected) "已连接" else "未连接"}")
            log("================================")

            try {
                executeTask(taskConfig)
            } catch (e: CancellationException) {
                log("任务被取消")
            } catch (e: Exception) {
                log("任务异常: ${e.message}")
                Log.e(TAG, "Task error", e)
            } finally {
                _runInfo.value = _runInfo.value.copy(state = TaskState.STOPPED)
                log("========== 任务结束 ==========")
            }
        }
    }

    /**
     * 暂停任务
     */
    fun pause() {
        isPaused = true
        _runInfo.value = _runInfo.value.copy(state = TaskState.PAUSED)
        log("任务已暂停")
    }

    /**
     * 恢复任务
     */
    fun resume() {
        isPaused = false
        _runInfo.value = _runInfo.value.copy(state = TaskState.RUNNING)
        log("任务已恢复")
    }

    /**
     * 停止任务
     */
    fun stop() {
        job?.cancel()
        job = null
        isPaused = false
        _runInfo.value = _runInfo.value.copy(state = TaskState.STOPPED)
        log("任务已停止")
    }

    /**
     * 清除日志
     */
    fun clearLogs() {
        _logs.value = emptyList()
    }

    private suspend fun executeTask(taskConfig: TaskConfig) {
        var loopIndex = 0
        val maxLoops = if (taskConfig.loopCount == -1) Int.MAX_VALUE else taskConfig.loopCount

        log("任务开始: ${taskConfig.name}")

        while (loopIndex < maxLoops && isActive) {
            loopIndex++
            _runInfo.value = _runInfo.value.copy(currentLoop = loopIndex)
            log("=== 第 $loopIndex 轮开始 ===")

            var allStepsSuccess = true
            for ((stepIndex, step) in taskConfig.steps.withIndex()) {
                while (isPaused && isActive) {
                    delay(200)
                }
                if (!isActive) break

                _runInfo.value = _runInfo.value.copy(
                    currentStepIndex = stepIndex + 1,
                    currentStepName = step.name
                )

                val success = executeStep(step, taskConfig)
                if (!success && !step.optional) {
                    allStepsSuccess = false
                    log("步骤失败: ${step.name}")
                }

                val randomDelay = RandomUtil.getRandomDelay(
                    taskConfig.minRandomDelay,
                    taskConfig.maxRandomDelay
                )
                delay(randomDelay)
            }

            if (allStepsSuccess) {
                _runInfo.value = _runInfo.value.copy(successCount = _runInfo.value.successCount + 1)
            } else {
                _runInfo.value = _runInfo.value.copy(failureCount = _runInfo.value.failureCount + 1)
            }

            if (loopIndex < maxLoops && isActive) {
                log("等待下一轮...")
                delay(taskConfig.loopDelay)
            }
        }
        log("任务结束")
    }

    private suspend fun executeStep(step: ClickStep, config: TaskConfig): Boolean {
        log("📌 执行步骤: ${step.name}")
        val service = AutoClickService.instance
        if (service == null) {
            log("❌ 错误: 无障碍服务未连接")
            return false
        }

        var retries = 0
        while (retries <= step.retryCount && isActive) {
            if (retries > 0) {
                log("🔄 重试第 $retries 次...")
            }

            val result = when (step.type) {
                StepType.FIND_AND_CLICK -> {
                    val text = step.targetTexts.firstOrNull() ?: ""
                    log("🔍 查找文本: $text (精确=${step.exactMatch})")
                    val found = service.findAndClickSync(text, step.exactMatch)
                    if (!found) {
                        log("❌ 未找到或点击失败: $text")
                    } else {
                        log("✅ 点击成功: $text")
                    }
                    found
                }
                StepType.FIND_AND_CLICK_ANY -> {
                    log("🔍 查找任一文本: ${step.targetTexts.joinToString(", ")} (精确=${step.exactMatch})")
                    // 先检查是否存在目标文本
                    val existsText = if (step.exactMatch) {
                        service.findFirstExactMatchText(*step.targetTexts.toTypedArray())
                    } else {
                        service.findFirstMatchText(*step.targetTexts.toTypedArray())
                    }
                    if (existsText != null) {
                        log("📱 找到文本: \"$existsText\"，尝试点击...")
                    } else {
                        log("⚠️ 未找到任何目标文本")
                    }

                    val found = service.findAndClickAnySync(
                        *step.targetTexts.toTypedArray(),
                        exactMatch = step.exactMatch
                    )
                    if (!found) {
                        log("❌ 点击失败: 所有文本都未找到或点击不成功")
                    } else {
                        log("✅ 点击操作执行完成")
                    }
                    found
                }
                StepType.COORDINATE_CLICK -> {
                    val offsetX = RandomUtil.getOffsetCoordinate(step.x, config.coordinateOffset)
                    val offsetY = RandomUtil.getOffsetCoordinate(step.y, config.coordinateOffset)
                    log("👆 坐标点击: ($offsetX, $offsetY)")
                    val clicked = service.clickAtSync(offsetX, offsetY)
                    if (clicked) {
                        log("✅ 坐标点击成功: ($offsetX, $offsetY)")
                    } else {
                        log("⚠️ 坐标点击回调失败，但手势已发送: ($offsetX, $offsetY)")
                    }
                    // 无论回调如何，都返回true（手势已发出）
                    true
                }
                StepType.REPEAT_COORDINATE_CLICK -> {
                    log("👆 重复坐标点击: (${step.x.toInt()}, ${step.y.toInt()}), 超时: ${step.timeout/1000}秒")
                    val result = repeatCoordinateClick(service, step.x, step.y, step.timeout, config.coordinateOffset)
                    if (result) {
                        log("✅ 重复坐标点击完成")
                    } else {
                        log("⚠️ 重复坐标点击超时")
                    }
                    result
                }
                StepType.WAIT -> {
                    log("⏳ 等待: ${step.delayAfter}ms")
                    delay(step.delayAfter)
                    true
                }
                StepType.WAIT_COUNTDOWN -> {
                    log("⏳ 等待倒计时结束...")
                    val found = waitForCountdownEnd(service, step.timeout)
                    if (!found) {
                        log("⚠️ 等待倒计时超时")
                    }
                    found
                }
                StepType.WAIT_FOR_TEXT -> {
                    log("⏳ 等待文本出现... (精确匹配=${step.exactMatch})")
                    val found = waitForText(service, step.targetTexts, step.timeout, step.exactMatch)
                    if (!found) {
                        log("❌ 等待文本超时")
                    }
                    found
                }
                StepType.WAIT_AND_CLICK -> {
                    val text = step.targetTexts.firstOrNull() ?: ""
                    log("⏳ 等待并点击: \"$text\" (找到后立即点击)")
                    val found = waitForTextAndClick(service, text, step.timeout)
                    if (!found) {
                        log("❌ 等待并点击失败: \"$text\"")
                    }
                    found
                }
                StepType.WAIT_AND_CLICK_ANY -> {
                    log("⏳ 等待并点击任一: ${step.targetTexts.joinToString(", ")}")
                    val found = waitForTextAndClickAny(service, step.targetTexts, step.timeout)
                    if (!found) {
                        log("❌ 等待并点击任一失败")
                    }
                    found
                }
                StepType.WAIT_FOR_TEXT_GONE -> {
                    log("⏳ 等待文本消失...")
                    waitForTextGone(service, step.targetTexts, step.timeout)
                }
                StepType.SWIPE -> {
                    log("👆 滑动操作")
                    service.swipe(step.x, step.y, step.endX, step.endY)
                    true
                }
                StepType.BACK -> {
                    log("👆 返回操作")
                    service.goBack()
                }
            }

            if (result) {
                log("✅ 步骤完成: ${step.name}")
                delay(step.delayAfter)
                return true
            }

            retries++
            if (retries <= step.retryCount) {
                log("⚠️ 步骤失败，准备重试 $retries/${step.retryCount}")
                delay(1000)
            }
        }

        log("❌ 步骤最终失败: ${step.name}")
        return false
    }

    /**
     * 从屏幕文本中提取倒计时信息
     * 匹配模式如: "20秒后可领取奖励", "倒计时 15s", "10秒" 等
     * 返回: 如 "20秒后可领取奖励"（完整文本，供UI显示）
     */
    private fun extractCountdownText(screenTexts: List<String>): String {
        val countdownPatterns = listOf(
            Regex("\\d+秒后"),         // "20秒后可领取奖励" 或 "20秒后"
            Regex("倒计时[：:]?\\s*\\d+"), // "倒计时：15"
            Regex("\\d+s后"),          // "20s后"
            Regex("\\d+秒")            // "10秒"
        )
        for (text in screenTexts) {
            for (pattern in countdownPatterns) {
                val match = pattern.find(text)
                if (match != null) {
                    return text.trim()
                }
            }
        }
        return ""
    }

    /**
     * 从倒计时文本中提取剩余秒数
     * 例如 "20秒后可领取奖励" → "20秒"
     */
    private fun extractCountdownSeconds(text: String): String {
        val secondsMatch = Regex("(\\d+)秒").find(text)
        if (secondsMatch != null) {
            return "${secondsMatch.groupValues[1]}秒"
        }
        val sMatch = Regex("(\\d+)s").find(text)
        if (sMatch != null) {
            return "${sMatch.groupValues[1]}秒"
        }
        return text
    }

    /**
     * 等待倒计时结束
     * 监控屏幕上的倒计时文本（如"16秒后可领取奖励"），等它消失后返回
     * 这期间不点击任何东西，避免打断倒计时
     */
    private suspend fun waitForCountdownEnd(
        service: AutoClickService,
        timeout: Long
    ): Boolean {
        val startTime = System.currentTimeMillis()
        var lastLogTime = 0
        var hadCountdown = false

        val maxSecondsInit = (timeout / 1000).toInt()
        _runInfo.value = _runInfo.value.copy(countdownText = "⏳ 等待倒计时 0s / ${maxSecondsInit}s")

        while (System.currentTimeMillis() - startTime < timeout && isActive) {
            val elapsed = System.currentTimeMillis() - startTime
            val elapsedSeconds = (elapsed / 1000).toInt()

            if (elapsedSeconds % 10 == 0 && elapsedSeconds > lastLogTime) {
                log("⏳ 等待倒计时... ${elapsedSeconds}秒")
                lastLogTime = elapsedSeconds
            }

            try {
                val screenTexts = service.getAllTextOnScreen()
                val countdownText = extractCountdownText(screenTexts)
                if (countdownText.isNotEmpty()) {
                    // 倒计时还在，继续等
                    hadCountdown = true
                    val secondsDisplay = extractCountdownSeconds(countdownText)
                    _runInfo.value = _runInfo.value.copy(countdownText = "⏳ $secondsDisplay")
                    if (elapsedSeconds % 10 == 0) {
                        log("⏳ 倒计时进行中: $countdownText")
                    }
                } else {
                    // 倒计时消失了
                    if (hadCountdown) {
                        log("🔔 倒计时结束！等待了${elapsedSeconds}秒，按钮已激活！")
                        _runInfo.value = _runInfo.value.copy(countdownText = "")
                        return true
                    }
                    // 还没看到过倒计时，可能还没开始，继续等
                    _runInfo.value = _runInfo.value.copy(countdownText = "⏳ 等待倒计时 ${elapsedSeconds}s / ${maxSecondsInit}s")
                    if (elapsedSeconds == 1) {
                        log("📱 初始屏幕: ${screenTexts.take(8).joinToString(", ")}")
                    }
                }
            } catch (_: Exception) {}

            delay(500)
        }

        log("⚠️ 等待倒计时超时 (${timeout/1000}秒)")
        _runInfo.value = _runInfo.value.copy(countdownText = "")
        // 超时也返回true，让流程继续
        return true
    }

    /**
     * 重复坐标点击，直到超时
     * 用于倒计时按钮：不断点击同一个位置，倒计时结束后按钮自然变成可点击状态
     * @return true 表示在超时时间内完成了点击（通过判断倒计时文本消失来判断）
     */
    private suspend fun repeatCoordinateClick(
        service: AutoClickService,
        x: Float,
        y: Float,
        timeout: Long,
        offset: Int
    ): Boolean {
        val startTime = System.currentTimeMillis()
        var clickCount = 0
        var lastLogTime = 0
        var hadCountdown = false

        val maxSecondsInit = (timeout / 1000).toInt()
        _runInfo.value = _runInfo.value.copy(countdownText = "⏳ 点击中 0s / ${maxSecondsInit}s")

        while (System.currentTimeMillis() - startTime < timeout && isActive) {
            val elapsed = System.currentTimeMillis() - startTime
            val elapsedSeconds = (elapsed / 1000).toInt()

            // 执行一次坐标点击
            val clickX = RandomUtil.getOffsetCoordinate(x, offset)
            val clickY = RandomUtil.getOffsetCoordinate(y, offset)
            service.clickAtSync(clickX, clickY)
            clickCount++

            // 日志
            if (elapsedSeconds % 10 == 0 && elapsedSeconds > lastLogTime) {
                log("👆 已点击${clickCount}次... ${elapsedSeconds}秒")
                lastLogTime = elapsedSeconds
            }

            // 更新倒计时显示
            val maxSeconds = (timeout / 1000).toInt()
            var countdownDisplay = "⏳ 已点击${clickCount}次 ${elapsedSeconds}s / ${maxSeconds}s"
            try {
                val screenTexts = service.getAllTextOnScreen()
                val countdownText = extractCountdownText(screenTexts)
                if (countdownText.isNotEmpty()) {
                    hadCountdown = true
                    countdownDisplay = "⏳ ${extractCountdownSeconds(countdownText)} (已点${clickCount}次)"
                } else if (hadCountdown) {
                    // 倒计时消失了！说明按钮已激活并被点击，等待一小会确保点击生效
                    log("🔔 倒计时结束！已点击${clickCount}次，按钮已激活！")
                    _runInfo.value = _runInfo.value.copy(countdownText = "")
                    // 多点几次确保生效
                    repeat(3) {
                        service.clickAtSync(
                            RandomUtil.getOffsetCoordinate(x, offset),
                            RandomUtil.getOffsetCoordinate(y, offset)
                        )
                        delay(300)
                    }
                    log("✅ 额外点击3次，确保领取成功")
                    return true
                }
                if (clickCount == 1 && screenTexts.isNotEmpty()) {
                    log("📱 初始屏幕: ${screenTexts.take(8).joinToString(", ")}")
                }
            } catch (_: Exception) {}
            _runInfo.value = _runInfo.value.copy(countdownText = countdownDisplay)

            // 每次点击间隔
            delay(800)
        }

        log("⚠️ 重复坐标点击超时，共点击${clickCount}次")
        _runInfo.value = _runInfo.value.copy(countdownText = "")
        return true
    }

    /**
     * 等待文本出现后立即点击（核心：找到即点，无延迟）
     */
    private suspend fun waitForTextAndClick(
        service: AutoClickService,
        text: String,
        timeout: Long
    ): Boolean {
        val startTime = System.currentTimeMillis()
        var lastLogTime = 0
        var checkCount = 0
        var hadCountdown = false

        log("🔍 等待并点击: \"$text\"")

        val maxSecondsInit = (timeout / 1000).toInt()
        _runInfo.value = _runInfo.value.copy(countdownText = "⏳ 等待中 0s / ${maxSecondsInit}s")

        while (System.currentTimeMillis() - startTime < timeout && isActive) {
            checkCount++
            val elapsed = System.currentTimeMillis() - startTime
            val elapsedSeconds = (elapsed / 1000).toInt()

            if (elapsedSeconds % 10 == 0 && elapsedSeconds > lastLogTime) {
                log("⏳ 等待中... ${elapsedSeconds}秒")
                lastLogTime = elapsedSeconds
            }

            // 更新倒计时显示
            val maxSeconds = (timeout / 1000).toInt()
            var countdownDisplay = "⏳ 已等待 ${elapsedSeconds}s / ${maxSeconds}s"
            try {
                val screenTexts = service.getAllTextOnScreen()
                val countdownText = extractCountdownText(screenTexts)
                if (countdownText.isNotEmpty()) {
                    hadCountdown = true
                    countdownDisplay = "⏳ ${extractCountdownSeconds(countdownText)}"
                } else if (hadCountdown) {
                    hadCountdown = false
                    log("🔔 倒计时结束！当前屏幕: ${screenTexts.take(10).joinToString(", ")}")
                }
                if (checkCount == 1 && screenTexts.isNotEmpty()) {
                    log("📱 初始屏幕: ${screenTexts.take(8).joinToString(", ")}")
                }
            } catch (_: Exception) {}
            _runInfo.value = _runInfo.value.copy(countdownText = countdownDisplay)

            // 尝试查找文本并立即点击（使用增强搜索，包含contentDescription）
            val nodes = service.findNodesByTextEnhanced(text)
            if (nodes.isNotEmpty()) {
                log("✅ 找到 \"$text\" (${nodes.size}个节点)，立即点击！")
                _runInfo.value = _runInfo.value.copy(countdownText = "")

                val clicked = service.findAndClickSyncEnhanced(text)
                if (clicked) {
                    log("✅ 点击成功: \"$text\" (等待了${elapsedSeconds}秒)")
                    return true
                } else {
                    log("⚠️ 找到但点击失败，继续重试...")
                }
            }

            val checkInterval = when {
                elapsedSeconds < 10 -> 300L
                elapsedSeconds < 30 -> 500L
                else -> 1000L
            }
            delay(checkInterval)
        }

        log("❌ 等待超时: ${timeout/1000}秒内未找到并点击 \"$text\"")
        _runInfo.value = _runInfo.value.copy(countdownText = "")
        return false
    }

    /**
     * 等待任一文本出现后立即点击
     */
    private suspend fun waitForTextAndClickAny(
        service: AutoClickService,
        texts: List<String>,
        timeout: Long
    ): Boolean {
        val startTime = System.currentTimeMillis()
        var lastLogTime = 0
        var checkCount = 0
        var hadCountdown = false

        log("🔍 等待并点击任一: ${texts.joinToString(", ")}")

        val maxSecondsInit = (timeout / 1000).toInt()
        _runInfo.value = _runInfo.value.copy(countdownText = "⏳ 等待中 0s / ${maxSecondsInit}s")

        while (System.currentTimeMillis() - startTime < timeout && isActive) {
            checkCount++
            val elapsed = System.currentTimeMillis() - startTime
            val elapsedSeconds = (elapsed / 1000).toInt()

            if (elapsedSeconds % 10 == 0 && elapsedSeconds > lastLogTime) {
                log("⏳ 等待中... ${elapsedSeconds}秒")
                lastLogTime = elapsedSeconds
            }

            // 更新倒计时显示
            val maxSeconds = (timeout / 1000).toInt()
            var countdownDisplay = "⏳ 已等待 ${elapsedSeconds}s / ${maxSeconds}s"
            try {
                val screenTexts = service.getAllTextOnScreen()
                val countdownText = extractCountdownText(screenTexts)
                if (countdownText.isNotEmpty()) {
                    hadCountdown = true
                    countdownDisplay = "⏳ ${extractCountdownSeconds(countdownText)}"
                } else if (hadCountdown) {
                    hadCountdown = false
                    log("🔔 倒计时结束！当前屏幕: ${screenTexts.take(10).joinToString(", ")}")
                }
                if (checkCount == 1 && screenTexts.isNotEmpty()) {
                    log("📱 初始屏幕: ${screenTexts.take(8).joinToString(", ")}")
                }
            } catch (_: Exception) {}
            _runInfo.value = _runInfo.value.copy(countdownText = countdownDisplay)

            // 尝试查找任一文本并立即点击（使用增强搜索）
            for (text in texts) {
                val nodes = service.findNodesByTextEnhanced(text)
                if (nodes.isNotEmpty()) {
                    log("✅ 找到 \"$text\" (${nodes.size}个节点)，立即点击！")
                    _runInfo.value = _runInfo.value.copy(countdownText = "")

                    val clicked = service.findAndClickSyncEnhanced(text)
                    if (clicked) {
                        log("✅ 点击成功: \"$text\" (等待了${elapsedSeconds}秒)")
                        return true
                    } else {
                        log("⚠️ 找到 \"$text\" 但点击失败，尝试下一个...")
                    }
                }
            }

            val checkInterval = when {
                elapsedSeconds < 10 -> 300L
                elapsedSeconds < 30 -> 500L
                else -> 1000L
            }
            delay(checkInterval)
        }

        log("❌ 等待超时: 未找到并点击任何文本")
        _runInfo.value = _runInfo.value.copy(countdownText = "")
        return false
    }

    private suspend fun waitForText(
        service: AutoClickService,
        texts: List<String>,
        timeout: Long,
        exactMatch: Boolean = false
    ): Boolean {
        val startTime = System.currentTimeMillis()
        var lastLogTime = 0
        var checkCount = 0
        var hadCountdown = false  // 用于检测倒计时消失的瞬间

        log("开始等待文本: ${texts.joinToString(", ")} (精确匹配=$exactMatch)")

        // 立即显示初始倒计时
        val maxSecondsInit = (timeout / 1000).toInt()
        _runInfo.value = _runInfo.value.copy(countdownText = "⏳ 等待中 0s / ${maxSecondsInit}s")

        while (System.currentTimeMillis() - startTime < timeout && isActive) {
            checkCount++
            val elapsed = System.currentTimeMillis() - startTime
            val elapsedSeconds = (elapsed / 1000).toInt()

            // 每10秒记录一次等待状态
            if (elapsedSeconds % 10 == 0 && elapsedSeconds > lastLogTime) {
                log("等待中... ${elapsedSeconds}秒")
                lastLogTime = elapsedSeconds
            }

            // 更新UI倒计时显示 —— 始终显示已等待时间
            val maxSeconds = (timeout / 1000).toInt()
            var countdownDisplay = "⏳ 已等待 ${elapsedSeconds}s / ${maxSeconds}s"

            // 尝试从屏幕读取倒计时文本
            try {
                val screenTexts = service.getAllTextOnScreen()
                val countdownText = extractCountdownText(screenTexts)
                if (countdownText.isNotEmpty()) {
                    hadCountdown = true
                    val secondsDisplay = extractCountdownSeconds(countdownText)
                    countdownDisplay = "⏳ $secondsDisplay"
                    if (checkCount % 15 == 0) {
                        log("⏳ 倒计时: $countdownText")
                    }
                } else {
                    // 倒计时刚消失时，打印屏幕文本用于调试
                    if (hadCountdown) {
                        hadCountdown = false
                        log("🔔 倒计时结束！当前屏幕文本: ${screenTexts.take(10).joinToString(", ")}")
                    }
                }

                // 定期显示当前屏幕文本（调试用）
                if (checkCount % 20 == 0 && screenTexts.isNotEmpty()) {
                    log("📱 当前屏幕: ${screenTexts.take(8).joinToString(", ")}")
                }
                // 首次检查时显示屏幕文本
                if (checkCount == 1 && screenTexts.isNotEmpty()) {
                    log("📱 初始屏幕文本: ${screenTexts.take(8).joinToString(", ")}")
                }
            } catch (e: Exception) {
                log("⚠️ 获取屏幕文本失败: ${e.message}")
            }

            // 无论如何都更新倒计时文本到 UI
            _runInfo.value = _runInfo.value.copy(countdownText = countdownDisplay)

            // 检查是否出现目标文本（根据 exactMatch 决定匹配方式）
            val foundText = if (exactMatch) {
                service.findFirstExactMatchText(*texts.toTypedArray())
            } else {
                service.findFirstMatchText(*texts.toTypedArray())
            }

            if (foundText != null) {
                log("✅ 找到目标文本: \"$foundText\" (等待了${elapsedSeconds}秒)")
                _runInfo.value = _runInfo.value.copy(countdownText = "") // 清除倒计时
                delay(2000)
                return true
            }

            // 根据等待时间调整检查频率
            val checkInterval = when {
                elapsedSeconds < 10 -> 300L   // 前10秒，每300ms检查
                elapsedSeconds < 30 -> 500L   // 10-30秒，每500ms检查
                else -> 1000L                  // 30秒后，每1秒检查
            }
            delay(checkInterval)
        }

        log("❌ 等待超时: ${timeout/1000}秒内未检测到文本")
        _runInfo.value = _runInfo.value.copy(countdownText = "")
        return false
    }

    private suspend fun waitForTextGone(
        service: AutoClickService,
        texts: List<String>,
        timeout: Long
    ): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeout && isActive) {
            if (!service.hasAnyText(*texts.toTypedArray())) {
                return true
            }
            delay(500)
        }
        return false
    }

    private val CoroutineScope.isActive: Boolean
        get() = this.coroutineContext[Job]?.isActive ?: false

    private val isActive: Boolean
        get() = job?.isActive ?: false

    private fun log(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())
        val logEntry = "[$timestamp] $message"

        // 多种输出方式确保日志可见
        android.util.Log.d("AutoClick", message)  // Android Logcat
        println("AutoClick: $logEntry")  // 系统控制台

        // 更新UI日志
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(logEntry)
        if (currentLogs.size > 200) {
            currentLogs.removeAt(0)
        }
        _logs.value = currentLogs

        // 更新运行信息
        _runInfo.value = _runInfo.value.copy(lastLog = message)

        // 强制刷新
        println("LOG_DEBUG: $logEntry")
    }
}
