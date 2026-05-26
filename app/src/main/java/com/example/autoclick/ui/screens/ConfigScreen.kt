package com.example.autoclick.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ConfigScreen() {
    var loopCount by remember { mutableStateOf("-1") }
    var loopDelay by remember { mutableStateOf("5000") }
    var minDelay by remember { mutableStateOf("300") }
    var maxDelay by remember { mutableStateOf("1000") }
    var coordOffset by remember { mutableStateOf("5") }
    var timeout by remember { mutableStateOf("10000") }
    var retryCount by remember { mutableStateOf("3") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "任务配置",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 基础设置
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "基础设置",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                ConfigTextField(
                    label = "循环次数 (-1为无限)",
                    value = loopCount,
                    onValueChange = { loopCount = it }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ConfigTextField(
                    label = "循环间隔 (ms)",
                    value = loopDelay,
                    onValueChange = { loopDelay = it }
                )
            }
        }

        // 延迟设置
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "延迟设置 (防检测)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                ConfigTextField(
                    label = "最小随机延迟 (ms)",
                    value = minDelay,
                    onValueChange = { minDelay = it }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ConfigTextField(
                    label = "最大随机延迟 (ms)",
                    value = maxDelay,
                    onValueChange = { maxDelay = it }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ConfigTextField(
                    label = "坐标偏移范围 (px)",
                    value = coordOffset,
                    onValueChange = { coordOffset = it }
                )
            }
        }

        // 高级设置
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "高级设置",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                ConfigTextField(
                    label = "步骤超时时间 (ms)",
                    value = timeout,
                    onValueChange = { timeout = it }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ConfigTextField(
                    label = "失败重试次数",
                    value = retryCount,
                    onValueChange = { retryCount = it }
                )
            }
        }

        // 保存按钮
        Button(
            onClick = { /* TODO: 保存配置 */ },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("保存配置")
        }
    }
}

@Composable
private fun ConfigTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}
