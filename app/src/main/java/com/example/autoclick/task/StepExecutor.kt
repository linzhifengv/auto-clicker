package com.example.autoclick.task

/**
 * 步骤执行结果
 */
data class StepResult(
    val success: Boolean,
    val message: String = "",
    val stepId: Int = 0
)

/**
 * 任务执行监听器
 */
interface TaskExecutionListener {
    fun onTaskStarted(config: String)
    fun onStepExecuted(stepId: Int, stepName: String, success: Boolean)
    fun onLoopCompleted(loopIndex: Int, success: Boolean)
    fun onTaskCompleted(totalLoops: Int, successCount: Int, failureCount: Int)
    fun onTaskError(error: String)
}
