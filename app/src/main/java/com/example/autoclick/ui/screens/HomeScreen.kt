package com.example.autoclick.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.autoclick.service.AutoClickService
import com.example.autoclick.service.FloatingWindowService
import com.example.autoclick.util.PermissionHelper

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isServiceEnabled by remember { mutableStateOf(false) }
    var hasOverlayPermission by remember { mutableStateOf(false) }
    var isAccessibilityConnected by remember { mutableStateOf(false) }
    var isRunning by remember { mutableStateOf(false) }

    // 每次 onResume 时刷新权限状态
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceEnabled = PermissionHelper.isAccessibilityServiceEnabled(context)
                hasOverlayPermission = PermissionHelper.canDrawOverlays(context)
                isAccessibilityConnected = AutoClickService.isConnected
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "自动点击器",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // 服务状态卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "服务状态",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(onClick = {
                        isServiceEnabled = PermissionHelper.isAccessibilityServiceEnabled(context)
                        hasOverlayPermission = PermissionHelper.canDrawOverlays(context)
                        isAccessibilityConnected = AutoClickService.isConnected
                        Toast.makeText(context, "状态已刷新", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新状态")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                PermissionRow(
                    label = "无障碍服务",
                    isEnabled = isServiceEnabled,
                    onClickEnable = {
                        PermissionHelper.openAccessibilitySettings(context)
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                PermissionRow(
                    label = "无障碍服务已连接",
                    isEnabled = isAccessibilityConnected,
                    onClickEnable = {
                        PermissionHelper.openAccessibilitySettings(context)
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                PermissionRow(
                    label = "悬浮窗权限",
                    isEnabled = hasOverlayPermission,
                    onClickEnable = {
                        PermissionHelper.openOverlaySettings(context)
                    }
                )
            }
        }

        // 快速操作
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "快速操作",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            // 检查所有前置条件
                            if (!isServiceEnabled) {
                                Toast.makeText(context, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (!AutoClickService.isConnected) {
                                Toast.makeText(context, "无障碍服务未连接，请检查是否已正确开启", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            if (!hasOverlayPermission) {
                                Toast.makeText(context, "请先开启悬浮窗权限", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            FloatingWindowService.start(context)
                            isRunning = true
                            Toast.makeText(context, "服务已启动，请切换到目标应用", Toast.LENGTH_LONG).show()
                        },
                        enabled = !isRunning,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("启动服务")
                    }

                    Button(
                        onClick = {
                            FloatingWindowService.stop(context)
                            isRunning = false
                            Toast.makeText(context, "服务已停止", Toast.LENGTH_SHORT).show()
                        },
                        enabled = isRunning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("停止服务")
                    }
                }
            }
        }

        // 使用提示
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = if (!isServiceEnabled || !hasOverlayPermission)
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            else
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (!isServiceEnabled || !hasOverlayPermission) {
                    Text(
                        text = "请先开启所有必需权限后再启动服务",
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                } else if (!isAccessibilityConnected) {
                    Text(
                        text = "无障碍服务已开启但未连接，请尝试关闭后重新开启",
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                } else {
                    Text(
                        text = "使用步骤：\n1. 点击\"启动服务\"\n2. 切换到汽水音乐应用\n3. 通过悬浮窗控制面板点击\"开始\"",
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    isEnabled: Boolean,
    onClickEnable: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label)
        if (isEnabled) {
            Text(
                text = "✓ 已开启",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        } else {
            TextButton(onClick = onClickEnable) {
                Text("去开启")
            }
        }
    }
}
