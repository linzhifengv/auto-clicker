package com.example.autoclick.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.autoclick.MainActivity
import com.example.autoclick.R
import com.example.autoclick.config.PresetTemplates
import com.example.autoclick.task.TaskRunInfo
import com.example.autoclick.task.TaskScheduler
import com.example.autoclick.task.TaskState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class FloatingWindowService : Service() {

    companion object {
        private const val CHANNEL_ID = "auto_click_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingWindowService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var dotView: View? = null
    private var isMinimized = false

    private val taskScheduler = TaskScheduler()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        com.example.autoclick.config.CoordinateStore.init(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        acquireWakeLock()
        showFloatingWindow()
        observeTaskState()
    }

    override fun onDestroy() {
        super.onDestroy()
        taskScheduler.stop()
        removeFloatingViews()
        releaseWakeLock()
        serviceScope.cancel()
    }

    private fun showFloatingWindow() {
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.floating_window, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        setupDragListener(floatingView!!, params)
        setupButtons(floatingView!!)
        windowManager.addView(floatingView, params)
    }

    private fun showDotView() {
        val inflater = LayoutInflater.from(this)
        dotView = inflater.inflate(R.layout.floating_dot, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        dotView?.setOnClickListener {
            expandFromDot()
        }

        setupDragListener(dotView!!, params)
        windowManager.addView(dotView, params)
    }

    private fun minimizeToDot() {
        isMinimized = true
        floatingView?.visibility = View.GONE
        showDotView()
    }

    private fun expandFromDot() {
        isMinimized = false
        dotView?.let {
            windowManager.removeView(it)
            dotView = null
        }
        floatingView?.visibility = View.VISIBLE
    }

    private var recordingFor: String? = null  // "lingqu" or "jiangli"

    private fun setupButtons(view: View) {
        val btnStart = view.findViewById<Button>(R.id.btn_start)
        val btnPause = view.findViewById<Button>(R.id.btn_pause)
        val btnStop = view.findViewById<Button>(R.id.btn_stop)
        val btnMinimize = view.findViewById<Button>(R.id.btn_minimize)
        val btnSetLingqu = view.findViewById<Button>(R.id.btn_set_lingqu)
        val btnSetJiangli = view.findViewById<Button>(R.id.btn_set_jiangli)

        btnStart.setOnClickListener {
            val config = PresetTemplates.qishuiMusicAdWatch()
            taskScheduler.start(config)
        }

        btnPause.setOnClickListener {
            val state = taskScheduler.runInfo.value.state
            if (state == TaskState.RUNNING) {
                taskScheduler.pause()
                btnPause.text = "恢复"
            } else if (state == TaskState.PAUSED) {
                taskScheduler.resume()
                btnPause.text = "暂停"
            }
        }

        btnStop.setOnClickListener {
            taskScheduler.stop()
        }

        btnMinimize.setOnClickListener {
            minimizeToDot()
        }

        // 坐标录制按钮
        btnSetLingqu.setOnClickListener {
            startRecording("lingqu")
        }

        btnSetJiangli.setOnClickListener {
            startRecording("jiangli")
        }

        updateCoordDisplay()
    }

    private fun startRecording(type: String) {
        recordingFor = type
        floatingView?.let { view ->
            val tvCoords = view.findViewById<TextView>(R.id.tv_coords)
            tvCoords.text = "🎯 点击屏幕设置${if (type == "lingqu") "领取" else "奖励"}坐标"
            tvCoords.setTextColor(android.graphics.Color.RED)
        }

        // 延迟100ms后开始监听屏幕触摸
        floatingView?.postDelayed({
            setupTouchRecording()
        }, 100)
    }

    private fun setupTouchRecording() {
        // 降低悬浮窗透明度，让用户看到底层内容
        floatingView?.alpha = 0.3f

        val overlayView = View(this).apply {
            layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0x01000000) // 几乎透明，但可以接收触摸
        }

        val overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        overlayView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val x = event.rawX
                val y = event.rawY

                when (recordingFor) {
                    "lingqu" -> {
                        com.example.autoclick.config.CoordinateStore.saveLingqu(x, y)
                        android.util.Log.d("FloatingWindow", "保存领取坐标: ($x, $y)")
                    }
                    "jiangli" -> {
                        com.example.autoclick.config.CoordinateStore.saveJiangli(x, y)
                        android.util.Log.d("FloatingWindow", "保存奖励坐标: ($x, $y)")
                    }
                }

                recordingFor = null
                windowManager.removeView(overlayView)
                floatingView?.alpha = 1.0f
                updateCoordDisplay()
                true
            } else {
                false
            }
        }

        windowManager.addView(overlayView, overlayParams)
    }

    private fun updateCoordDisplay() {
        floatingView?.let { view ->
            val tvCoords = view.findViewById<TextView>(R.id.tv_coords)
            val lingqu = com.example.autoclick.config.CoordinateStore.getLingqu()
            val jiangli = com.example.autoclick.config.CoordinateStore.getJiangli()

            val lingquStr = if (lingqu.first > 0) "领取(${lingqu.first.toInt()},${lingqu.second.toInt()})" else "领取(未设)"
            val jiangliStr = if (jiangli.first > 0) "奖励(${jiangli.first.toInt()},${jiangli.second.toInt()})" else "奖励(未设)"

            tvCoords.text = "📍 $lingquStr | $jiangliStr"
            tvCoords.setTextColor(0xFFAAAAAA.toInt())
        }
    }

    private fun setupDragListener(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (dx * dx + dy * dy > 25) {
                        isDragging = true
                    }
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun observeTaskState() {
        serviceScope.launch {
            taskScheduler.runInfo.collectLatest { info ->
                updateUI(info)
            }
        }
    }

    private fun updateUI(info: TaskRunInfo) {
        floatingView?.let { view ->
            val tvStatus = view.findViewById<TextView>(R.id.tv_status)
            val tvStepInfo = view.findViewById<TextView>(R.id.tv_step_info)
            val tvCount = view.findViewById<TextView>(R.id.tv_count)
            val tvCountdown = view.findViewById<TextView>(R.id.tv_countdown)

            val statusText = when (info.state) {
                TaskState.IDLE -> "就绪"
                TaskState.RUNNING -> "运行中"
                TaskState.PAUSED -> "已暂停"
                TaskState.STOPPED -> "已停止"
            }
            tvStatus?.text = statusText
            tvStepInfo?.text = "步骤: ${info.currentStepName}"
            tvCount?.text = "循环: ${info.currentLoop} | 成功: ${info.successCount}"

            // 倒计时显示
            if (info.countdownText.isNotEmpty()) {
                tvCountdown?.text = info.countdownText
                tvCountdown?.visibility = View.VISIBLE
            } else {
                tvCountdown?.visibility = View.GONE
            }
        }
    }

    private fun removeFloatingViews() {
        floatingView?.let { windowManager.removeView(it) }
        dotView?.let { windowManager.removeView(it) }
        floatingView = null
        dotView = null
    }

    // ========== 通知与电源管理 ==========

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "自动点击服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持自动点击服务在后台运行"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("自动点击器运行中")
                .setContentText("点击返回应用")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("自动点击器运行中")
                .setContentText("点击返回应用")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AutoClick::WakeLock"
        ).apply {
            acquire(60 * 60 * 1000L) // 最长1小时
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }
}
