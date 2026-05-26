package com.example.autoclick.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class AutoClickService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoClickService"
        var instance: AutoClickService? = null
            private set
        var isConnected: Boolean = false
            private set
        var onConnectionChanged: ((Boolean) -> Unit)? = null
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isConnected = true
        onConnectionChanged?.invoke(true)
        Log.d(TAG, "无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 监听窗口变化事件，用于状态检测
        event?.let {
            when (it.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    val packageName = it.packageName?.toString() ?: ""
                    val className = it.className?.toString() ?: ""
                    Log.d(TAG, "窗口变化: $packageName / $className")
                    onWindowChanged(packageName, className)
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    // 内容变化时可用于检测广告倒计时等
                }
                else -> {}
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isConnected = false
        onConnectionChanged?.invoke(false)
        Log.d(TAG, "无障碍服务已销毁")
    }

    // ========== 点击操作 ==========

    /**
     * 在指定坐标执行点击
     */
    fun clickAt(x: Float, y: Float, callback: ((Boolean) -> Unit)? = null) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "点击成功: ($x, $y)")
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.d(TAG, "点击取消: ($x, $y)")
                callback?.invoke(false)
            }
        }, handler)
    }

    /**
     * 在指定坐标执行滑动
     */
    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 300, callback: ((Boolean) -> Unit)? = null) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                callback?.invoke(false)
            }
        }, handler)
    }

    // ========== 节点查找 ==========

    /**
     * 通过文本查找节点
     */
    fun findNodesByText(text: String): List<AccessibilityNodeInfo> {
        val root = rootInActiveWindow ?: return emptyList()
        val nodes = root.findAccessibilityNodeInfosByText(text)
        return nodes?.toList() ?: emptyList()
    }

    /**
     * 通过ViewId查找节点
     */
    fun findNodesById(viewId: String): List<AccessibilityNodeInfo> {
        val root = rootInActiveWindow ?: return emptyList()
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        return nodes?.toList() ?: emptyList()
    }

    /**
     * 通过精确文本查找节点（完全相等，而非子串匹配）
     * 解决 Android findAccessibilityNodeInfosByText 子串匹配导致误触发的问题
     */
    fun findExactNodesByText(text: String): List<AccessibilityNodeInfo> {
        val candidates = findNodesByText(text)
        if (candidates.isEmpty()) return emptyList()

        return candidates.filter { node ->
            val nodeText = node.text?.toString()?.trim()
            nodeText != null && nodeText.equals(text, ignoreCase = false)
        }
    }

    /**
     * 查找并返回第一个精确匹配的文本（完全相等）
     * 用于 WAIT_FOR_TEXT + exactMatch=true 的场景
     */
    fun findFirstExactMatchText(vararg texts: String): String? {
        Log.d(TAG, "🔍 精确搜索文本: [${texts.joinToString(", ")}]")

        for (text in texts) {
            val exactNodes = findExactNodesByText(text)
            if (exactNodes.isNotEmpty()) {
                Log.d(TAG, "✅ 精确匹配到文本 \"$text\": ${exactNodes.size}个节点")
                return text
            }
            Log.d(TAG, "   ❌ 未精确匹配到文本 \"$text\"")
        }

        Log.d(TAG, "❌ 精确搜索完成: 未找到任何精确匹配")
        return null
    }

    /**
     * 查找包含指定文本的节点并点击
     * 增强版：支持更精确的文本匹配
     * @param exactMatch 如果为 true，只接受文本完全相等的节点
     */
    fun findAndClick(text: String, exactMatch: Boolean = false): Boolean {
        Log.d(TAG, "🔍 查找文本: \"$text\" (精确=$exactMatch)")

        val nodes = if (exactMatch) {
            findExactNodesByText(text)
        } else {
            findNodesByText(text)
        }

        if (nodes.isEmpty()) {
            Log.d(TAG, "   ❌ 未找到${if (exactMatch) "精确" else ""}匹配文本 \"$text\" 的节点")
            return false
        }

        Log.d(TAG, "   📌 找到 ${nodes.size} 个${if (exactMatch) "精确" else ""}匹配节点")

        // 过滤出可点击或可交互的节点
        val clickableNodes = nodes.filter { node ->
            node.isClickable || node.isCheckable || node.isLongClickable
        }
        Log.d(TAG, "   📌 其中 ${clickableNodes.size} 个节点可点击")

        // 优先选择完全匹配或精确包含文本的节点
        val preciseMatchNode = nodes.firstOrNull { node ->
            val nodeText = node.text?.toString()?.trim()
            nodeText != null && nodeText.equals(text, ignoreCase = false)
        }

        // 1. 先尝试精确匹配的节点（即使不可点击，通过父节点点击）
        if (preciseMatchNode != null) {
            Log.d(TAG, "   🎯 尝试精确匹配节点...")
            if (performClick(preciseMatchNode)) {
                Log.d(TAG, "   ✅ 精确匹配点击成功: \"$text\"")
                return true
            } else {
                Log.d(TAG, "   ⚠️ 精确匹配节点点击失败")
            }
        }

        // 2. 尝试可点击的节点
        Log.d(TAG, "   🎯 尝试可点击节点...")
        for (node in clickableNodes) {
            if (performClick(node)) {
                Log.d(TAG, "   ✅ 可点击节点点击成功: \"$text\"")
                return true
            }
        }

        // 3. 尝试所有找到的节点
        Log.d(TAG, "   🎯 尝试所有节点...")
        for (node in nodes) {
            if (performClick(node)) {
                Log.d(TAG, "   ✅ 任意节点点击成功: \"$text\"")
                return true
            }
        }

        Log.d(TAG, "   ❌ 文本匹配但点击失败: \"$text\"")
        return false
    }

    /**
     * 查找包含任一文本的节点并点击
     * 增强版：增加多次查找尝试和更精确的节点匹配
     * @param exactMatch 如果为 true，使用精确匹配
     */
    fun findAndClickAny(vararg texts: String, exactMatch: Boolean = false): Boolean {
        Log.d(TAG, "🎯 开始查找并点击任一文本: [${texts.joinToString(", ")}] (精确=$exactMatch)")

        // 多次尝试查找，最多尝试3次
        for (attempt in 1..3) {
            Log.d(TAG, "🔄 尝试次数: $attempt/3")

            for (text in texts) {
                if (findAndClick(text, exactMatch)) {
                    Log.d(TAG, "✅ 成功找到并点击: \"$text\" (尝试次数: $attempt)")
                    return true
                }
            }

            // 如果第一次尝试失败，短暂延迟后重试
            if (attempt < 3) {
                Log.d(TAG, "⏳ 第${attempt}次尝试失败，等待500ms后重试...")
                try {
                    Thread.sleep(500) // 增加到500ms
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    Log.e(TAG, "❌ 等待被中断: ${e.message}")
                    return false
                }
            }
        }

        Log.d(TAG, "❌ 所有尝试失败，未找到任何匹配的文本: [${texts.joinToString(", ")}]")
        return false
    }

    /**
     * 查找并点击特定文本的增强版本
     * 支持更精确的匹配和边界检查
     */
    fun findAndClickEnhanced(text: String): Boolean {
        val nodes = findNodesByText(text)
        if (nodes.isEmpty()) {
            return false
        }

        // 优先选择完全匹配的节点
        val exactMatchNode = nodes.firstOrNull { node ->
            val nodeText = node.text?.toString()?.trim()
            nodeText != null && (nodeText == text || nodeText.startsWith("$text "))
        }

        // 如果找到完全匹配的节点，优先点击它
        if (exactMatchNode != null) {
            if (performClick(exactMatchNode)) {
                Log.d(TAG, "精确匹配并点击: $text")
                return true
            }
        }

        // 否则点击第一个找到的节点
        for (node in nodes) {
            if (performClick(node)) {
                Log.d(TAG, "模糊匹配并点击: $text")
                return true
            }
        }

        return false
    }

    /**
     * 检查屏幕上是否存在指定文本
     * 增加详细日志
     */
    fun hasText(text: String): Boolean {
        val nodes = findNodesByText(text)
        val found = nodes.isNotEmpty()
        if (found) {
            Log.d(TAG, "✓ 文本 \"$text\" 存在 (${nodes.size}个节点)")
        }
        return found
    }

    /**
     * 查找并返回第一个匹配的文本
     * 支持多个候选文本，返回实际找到的文本
     * 增加详细的日志记录用于调试
     */
    fun findFirstMatchText(vararg texts: String): String? {
        Log.d(TAG, "🔍 开始搜索文本: [${texts.joinToString(", ")}]")

        for (text in texts) {
            val nodes = findNodesByText(text)
            if (nodes.isNotEmpty()) {
                Log.d(TAG, "✅ 找到文本 \"$text\": ${nodes.size}个匹配节点")

                // 记录第一个节点的详细信息
                try {
                    val firstNode = nodes.first()
                    val nodeText = firstNode.text?.toString() ?: "null"
                    val isClickable = firstNode.isClickable
                    val isCheckable = firstNode.isCheckable
                    val className = firstNode.className?.toString() ?: "null"
                    Log.d(TAG, "   📌 首个节点详情: 文本=\"$nodeText\", 可点击=$isClickable, 可选=$isCheckable, 类=$className")
                } catch (e: Exception) {
                    Log.e(TAG, "   ⚠️ 获取节点详情出错: ${e.message}")
                }

                return text
            } else {
                Log.d(TAG, "   ❌ 未找到文本 \"$text\"")
            }
        }

        Log.d(TAG, "❌ 搜索完成: 未找到任何匹配的文本")
        return null
    }

    /**
     * 检查屏幕上是否存在任一指定文本
     * 增加日志记录
     */
    fun hasAnyText(vararg texts: String): Boolean {
        val found = texts.any { hasText(it) }
        if (!found) {
            Log.d(TAG, "⚠️ 未找到任何文本: [${texts.joinToString(", ")}]")
        }
        return found
    }

    /**
     * 获取当前屏幕上所有文本节点的文本
     * 同时检查 text 和 contentDescription（很多App把按钮文字放在contentDescription里）
     */
    fun getAllTextOnScreen(): List<String> {
        val root = rootInActiveWindow ?: return emptyList()
        val allTexts = mutableListOf<String>()

        try {
            fun collectText(node: AccessibilityNodeInfo) {
                // 检查 text 属性
                val text = node.text?.toString()?.trim()
                if (!text.isNullOrEmpty() && text.length > 1) {
                    allTexts.add(text)
                }
                // 检查 contentDescription 属性（很多App用这个来设置按钮文字）
                val desc = node.contentDescription?.toString()?.trim()
                if (!desc.isNullOrEmpty() && desc.length > 1 && desc != text) {
                    allTexts.add("[desc]$desc")
                }

                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    collectText(child)
                    child.recycle()
                }
            }

            collectText(root)
        } catch (e: Exception) {
            Log.e(TAG, "获取屏幕文本时出错: ${e.message}")
        }

        return allTexts.distinct().take(50)
    }

    /**
     * 通过文本或contentDescription查找节点
     * 增强版：同时搜索 text 和 contentDescription
     */
    fun findNodesByTextEnhanced(text: String): List<AccessibilityNodeInfo> {
        val root = rootInActiveWindow ?: return emptyList()
        val results = mutableListOf<AccessibilityNodeInfo>()

        // 1. 先用系统API搜索（能搜到 text 和 contentDescription）
        val systemNodes = root.findAccessibilityNodeInfosByText(text)
        if (systemNodes != null) {
            results.addAll(systemNodes)
        }

        // 2. 如果系统API没找到，手动遍历搜索（更彻底）
        if (results.isEmpty()) {
            try {
                fun searchNode(node: AccessibilityNodeInfo) {
                    val nodeText = node.text?.toString() ?: ""
                    val nodeDesc = node.contentDescription?.toString() ?: ""
                    if (nodeText.contains(text, ignoreCase = true) ||
                        nodeDesc.contains(text, ignoreCase = true)) {
                        results.add(node)
                    }
                    for (i in 0 until node.childCount) {
                        val child = node.getChild(i) ?: continue
                        searchNode(child)
                        child.recycle()
                    }
                }
                searchNode(root)
            } catch (e: Exception) {
                Log.e(TAG, "手动搜索节点出错: ${e.message}")
            }
        }

        if (results.isNotEmpty()) {
            Log.d(TAG, "🔍 findNodesByTextEnhanced(\"$text\"): 找到 ${results.size} 个节点")
            results.forEachIndexed { i, node ->
                val t = node.text?.toString() ?: "null"
                val d = node.contentDescription?.toString() ?: "null"
                Log.d(TAG, "   节点$i: text=\"$t\", desc=\"$d\", clickable=${node.isClickable}")
            }
        } else {
            Log.d(TAG, "🔍 findNodesByTextEnhanced(\"$text\"): 未找到任何节点")
        }

        return results
    }

    /**
     * 记录当前屏幕上的所有文本到日志
     * 用于调试，帮助识别可用的文本
     */
    fun logCurrentScreenTexts() {
        val texts = getAllTextOnScreen()
        Log.d(TAG, "📋 当前屏幕上的所有文本 (${texts.size}个):")
        texts.forEachIndexed { index, text ->
            Log.d(TAG, "   ${index + 1}. \"$text\"")
        }

        if (texts.isEmpty()) {
            Log.d(TAG, "   (未找到任何文本)")
        }
    }

    /**
     * 获取节点的屏幕坐标中心点并通过手势点击
     * 增加详细的日志记录
     */
    fun clickNodeByGesture(node: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val centerX = rect.centerX().toFloat()
        val centerY = rect.centerY().toFloat()

        Log.d(TAG, "   🎯 手势点击坐标: ($centerX, $centerY)")
        Log.d(TAG, "   📏 节点区域: ${rect.left},${rect.top} - ${rect.right},${rect.bottom}")

        clickAt(centerX, centerY)
        return true
    }

    // ========== 辅助方法 ==========

    /**
     * 执行节点点击（先尝试节点ACTION_CLICK，失败则用手势点击）
     * 增加详细的日志记录
     */
    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        Log.d(TAG, "👆 开始执行点击...")

        // 记录节点信息
        val nodeText = node.text?.toString() ?: "无文本"
        val isClickable = node.isClickable
        Log.d(TAG, "   节点: 文本=\"$nodeText\", 可点击=$isClickable")

        // 先尝试直接点击节点
        if (node.isClickable) {
            Log.d(TAG, "   尝试 ACTION_CLICK...")
            val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "   ACTION_CLICK 结果: $success")
            if (success) {
                Log.d(TAG, "   ✅ ACTION_CLICK 成功")
                return true
            }
            Log.d(TAG, "   ⚠️ ACTION_CLICK 失败，尝试其他方式")
        }

        // 节点不可点击则尝试点击父节点
        Log.d(TAG, "   查找可点击的父节点...")
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 5) {
            if (parent.isClickable) {
                Log.d(TAG, "   找到可点击父节点 (深度: $depth)")
                val success = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "   父节点 ACTION_CLICK 结果: $success")
                if (success) {
                    Log.d(TAG, "   ✅ 父节点点击成功")
                    return true
                }
            }
            parent = parent.parent
            depth++
        }

        // 最后用手势点击坐标
        Log.d(TAG, "   使用手势点击...")
        val success = clickNodeByGesture(node)
        Log.d(TAG, "   手势点击结果: $success")
        return success
    }

    // ========== 同步手势点击（suspend 版本） ==========

    /**
     * 同步版本的手势点击 - 挂起直到手势完成或超时
     */
    suspend fun clickAtSync(x: Float, y: Float): Boolean {
        return withTimeoutOrNull(5000L) {
            suspendCancellableCoroutine { continuation ->
                clickAt(x, y) { success ->
                    if (continuation.isActive) {
                        continuation.resume(success)
                    }
                }
            }
        } ?: false
    }

    /**
     * 同步版本的节点手势点击 - 挂起直到手势完成
     * 优先使用节点自身坐标；若节点太小则用父节点
     */
    suspend fun clickNodeByGestureSync(node: AccessibilityNodeInfo): Boolean {
        val nodeRect = Rect()
        node.getBoundsInScreen(nodeRect)

        var clickX: Float
        var clickY: Float
        var usedParent = false

        // 如果节点区域太小（<30px），尝试用父节点的中心
        if (nodeRect.width() < 30 || nodeRect.height() < 30) {
            val parentNode = node.parent
            if (parentNode != null) {
                val parentRect = Rect()
                parentNode.getBoundsInScreen(parentRect)
                if (parentRect.width() >= 30 && parentRect.height() >= 30) {
                    clickX = parentRect.centerX().toFloat()
                    clickY = parentRect.centerY().toFloat()
                    usedParent = true
                    Log.d(TAG, "   📐 节点太小(${nodeRect.width()}x${nodeRect.height()})，使用父节点区域: ${parentRect.left},${parentRect.top}-${parentRect.right},${parentRect.bottom}")
                } else {
                    clickX = nodeRect.centerX().toFloat()
                    clickY = nodeRect.centerY().toFloat()
                }
            } else {
                clickX = nodeRect.centerX().toFloat()
                clickY = nodeRect.centerY().toFloat()
            }
        } else {
            clickX = nodeRect.centerX().toFloat()
            clickY = nodeRect.centerY().toFloat()
        }

        Log.d(TAG, "   🎯 手势点击坐标(同步): ($clickX, $clickY), 使用${if (usedParent) "父节点" else "自身节点"}")
        val result = clickAtSync(clickX, clickY)
        Log.d(TAG, "   手势点击结果: $result")
        return result
    }

    /**
     * 执行节点点击的 suspend 版本
     * 优先使用手势点击（更可靠，能触发实际UI响应），
     * ACTION_CLICK 对很多App虽然返回true但不触发UI
     */
    suspend fun performClickSync(node: AccessibilityNodeInfo): Boolean {
        Log.d(TAG, "👆 开始执行点击(同步版)...")
        val nodeText = node.text?.toString() ?: "null"
        val nodeDesc = node.contentDescription?.toString() ?: "null"
        val isClickable = node.isClickable
        Log.d(TAG, "   节点: text=\"$nodeText\", desc=\"$nodeDesc\", 可点击=$isClickable")

        // 1. 优先手势点击坐标（最可靠，模拟真实触摸）
        Log.d(TAG, "   🎯 尝试手势点击（首选）...")
        val gestureResult = clickNodeByGestureSync(node)
        if (gestureResult) {
            Log.d(TAG, "   ✅ 手势点击成功: \"$nodeText\"")
            return true
        }
        Log.d(TAG, "   ⚠️ 手势点击失败，尝试 ACTION_CLICK")

        // 2. 尝试 ACTION_CLICK
        if (node.isClickable) {
            val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (success) {
                Log.d(TAG, "   ✅ ACTION_CLICK 成功")
                return true
            }
        }

        // 3. 尝试父节点
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 5) {
            if (parent.isClickable) {
                val success = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) {
                    Log.d(TAG, "   ✅ 父节点 ACTION_CLICK 成功 (深度: $depth)")
                    return true
                }
            }
            parent = parent.parent
            depth++
        }

        Log.d(TAG, "   ❌ 所有点击方式均失败")
        return false
    }

    /**
     * 查找并点击（同步版本，手势点击等待完成）
     */
    suspend fun findAndClickSync(text: String, exactMatch: Boolean = false): Boolean {
        val nodes = if (exactMatch) findExactNodesByText(text) else findNodesByText(text)
        if (nodes.isEmpty()) return false

        // 优先精确匹配
        val preciseNode = nodes.firstOrNull { node ->
            node.text?.toString()?.trim()?.equals(text, ignoreCase = false) == true
        }
        if (preciseNode != null && performClickSync(preciseNode)) return true

        // 可点击节点
        for (node in nodes.filter { it.isClickable }) {
            if (performClickSync(node)) return true
        }

        // 所有节点
        for (node in nodes) {
            if (performClickSync(node)) return true
        }
        return false
    }

    /**
     * 查找并点击（增强同步版本）
     * 使用 findNodesByTextEnhanced 搜索，同时查找 text 和 contentDescription
     */
    suspend fun findAndClickSyncEnhanced(text: String): Boolean {
        Log.d(TAG, "🔍 findAndClickSyncEnhanced: \"$text\"")
        val nodes = findNodesByTextEnhanced(text)
        if (nodes.isEmpty()) {
            Log.d(TAG, "   ❌ 增强搜索未找到节点")
            return false
        }

        Log.d(TAG, "   📌 增强搜索找到 ${nodes.size} 个节点，开始点击...")

        // 优先点击可点击节点
        for (node in nodes.filter { it.isClickable }) {
            Log.d(TAG, "   🎯 尝试可点击节点: text=${node.text}, desc=${node.contentDescription}")
            if (performClickSync(node)) return true
        }

        // 尝试所有节点
        for (node in nodes) {
            Log.d(TAG, "   🎯 尝试任意节点: text=${node.text}, desc=${node.contentDescription}")
            if (performClickSync(node)) return true
        }

        Log.d(TAG, "   ❌ 增强搜索所有节点点击失败")
        return false
    }

    /**
     * 查找并点击任一文本（同步版本）
     */
    suspend fun findAndClickAnySync(vararg texts: String, exactMatch: Boolean = false): Boolean {
        Log.d(TAG, "🎯 开始查找并点击任一文本(同步): [${texts.joinToString(", ")}] (精确=$exactMatch)")

        for (attempt in 1..3) {
            Log.d(TAG, "🔄 同步尝试次数: $attempt/3")
            for (text in texts) {
                if (findAndClickSync(text, exactMatch)) {
                    Log.d(TAG, "✅ 同步成功找到并点击: \"$text\"")
                    return true
                }
            }
            if (attempt < 3) {
                kotlinx.coroutines.delay(500)
            }
        }

        Log.d(TAG, "❌ 同步所有尝试失败")
        return false
    }

    /**
     * 返回上一页
     */
    fun goBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * 回到主页
     */
    fun goHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /**
     * 获取当前窗口包名
     */
    fun getCurrentPackage(): String? {
        return rootInActiveWindow?.packageName?.toString()
    }

    // ========== 事件回调 ==========

    private var windowChangeListener: ((String, String) -> Unit)? = null

    fun setWindowChangeListener(listener: ((String, String) -> Unit)?) {
        windowChangeListener = listener
    }

    private fun onWindowChanged(packageName: String, className: String) {
        windowChangeListener?.invoke(packageName, className)
    }
}
