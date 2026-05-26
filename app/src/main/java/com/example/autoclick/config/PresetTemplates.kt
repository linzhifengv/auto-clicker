package com.example.autoclick.config

import com.example.autoclick.task.ClickStep
import com.example.autoclick.task.StepType

/**
 * 预设任务模板
 */
object PresetTemplates {

    /**
     * 汽水音乐 - 广告观看获取免费时长
     * 极简测试版：只有3个步骤
     */
    fun qishuiMusicAdWatch(): TaskConfig {
        // 从存储中读取用户设置的坐标
        val lingqu = CoordinateStore.getLingqu()
        val jiangli = CoordinateStore.getJiangli()

        val hasLingqu = lingqu.first > 0 && lingqu.second > 0
        val hasJiangli = jiangli.first > 0 && jiangli.second > 0

        val steps = mutableListOf<ClickStep>()

        if (hasLingqu) {
            // 步骤1: 先等倒计时结束（不点击，避免打断倒计时）
            steps.add(ClickStep(
                id = 1,
                name = "等待倒计时结束",
                type = StepType.WAIT_COUNTDOWN,
                delayAfter = 300,
                timeout = 120000,
                retryCount = 1
            ))
            // 步骤2: 倒计时结束了，单击"领取"坐标
            steps.add(ClickStep(
                id = 2,
                name = "点击领取坐标",
                type = StepType.COORDINATE_CLICK,
                x = lingqu.first,
                y = lingqu.second,
                delayAfter = 500,
                timeout = 10000,
                retryCount = 1
            ))
        } else {
            steps.add(ClickStep(
                id = 1,
                name = "等待并点击领取",
                type = StepType.WAIT_AND_CLICK,
                targetTexts = listOf("领取成功"),
                delayAfter = 2000,
                timeout = 120000,
                retryCount = 1
            ))
        }

        if (hasJiangli) {
            // 步骤3: 单击"奖励"坐标（弹窗已出现）
            steps.add(ClickStep(
                id = 3,
                name = "点击奖励坐标",
                type = StepType.COORDINATE_CLICK,
                x = jiangli.first,
                y = jiangli.second,
                delayAfter = 500,
                timeout = 10000,
                retryCount = 1
            ))
        } else {
            steps.add(ClickStep(
                id = 3,
                name = "点击奖励",
                type = StepType.FIND_AND_CLICK_ANY,
                targetTexts = listOf("领取奖励", "确认", "确定"),
                delayAfter = 2000,
                timeout = 15000,
                retryCount = 3
            ))
        }

        return TaskConfig(
            name = "汽水音乐-测试",
            steps = steps,
            loopCount = -1,
            loopDelay = 2000,
            minRandomDelay = 100,
            maxRandomDelay = 300
        )
    }

    /**
     * 通用广告观看模板
     */
    fun genericAdWatch(): TaskConfig {
        return TaskConfig(
            name = "通用广告观看",
            steps = listOf(
                ClickStep(
                    id = 1,
                    name = "查找广告入口",
                    type = StepType.FIND_AND_CLICK_ANY,
                    targetTexts = listOf("看广告", "免费获取", "观看广告", "领取奖励"),
                    delayAfter = 2000,
                    timeout = 15000
                ),
                ClickStep(
                    id = 2,
                    name = "等待广告",
                    type = StepType.WAIT,
                    delayAfter = 30000,
                    timeout = 30000
                ),
                ClickStep(
                    id = 3,
                    name = "等待广告完成",
                    type = StepType.WAIT_FOR_TEXT,
                    targetTexts = listOf("领取", "领取奖励", "关闭", "跳过", "×", "X"),
                    delayAfter = 500,
                    timeout = 10000,
                    optional = true
                ),
                ClickStep(
                    id = 4,
                    name = "领取奖励",
                    type = StepType.FIND_AND_CLICK_ANY,
                    targetTexts = listOf("领取奖励", "领取", "确认", "知道了", "完成"),
                    delayAfter = 1500,
                    timeout = 8000,
                    retryCount = 3
                ),
                ClickStep(
                    id = 5,
                    name = "关闭广告",
                    type = StepType.FIND_AND_CLICK_ANY,
                    targetTexts = listOf("关闭", "跳过", "×", "X", "关闭广告", "跳过广告"),
                    delayAfter = 2000,
                    timeout = 10000,
                    optional = true
                )
            ),
            loopCount = -1,
            loopDelay = 5000
        )
    }

    /**
     * 获取所有预设模板
     */
    fun getAllTemplates(): List<TaskConfig> {
        return listOf(
            qishuiMusicAdWatch(),
            genericAdWatch()
        )
    }
}
