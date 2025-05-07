/*
 * Designed and developed by 2024 skydoves (Jaewoong Eum)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.skydoves.pokedex.compose.feature.home

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.lzf.easyfloat.EasyFloat
import com.lzf.easyfloat.enums.ShowPattern
import com.lzf.easyfloat.enums.SidePattern
import android.graphics.Color
import android.widget.LinearLayout
import android.text.TextUtils
import android.util.Log
import android.widget.HorizontalScrollView
import java.lang.ref.WeakReference
import android.os.Handler
import android.os.Looper
import android.app.PendingIntent
import android.content.Intent
import android.graphics.PixelFormat
import android.text.method.ScrollingMovementMethod
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.widget.NestedScrollView
import com.lzf.easyfloat.interfaces.OnInvokeView

/**
 * 悬浮窗管理器，处理应用在后台时显示悬浮窗
 */
class FloatingWindowManager private constructor() {

    companion object {
        private const val TAG = "FloatingWindowManager"
        private const val FLOATING_TAG = "recording_window"
        
        // 悬浮窗消息相关常量
        private const val MAX_MESSAGES = 3 // 最多显示3条消息
        private const val UPDATE_INTERVAL = 500L // 定期更新检查间隔(毫秒)
        
        @Volatile
        private var instance: FloatingWindowManager? = null
        
        fun getInstance(): FloatingWindowManager {
            return instance ?: synchronized(this) {
                instance ?: FloatingWindowManager().also { instance = it }
            }
        }
        
        /**
         * 创建生命周期观察者，用于处理应用切换到后台时显示悬浮窗
         */
        fun createLifecycleObserver(
            context: Context,
            isRecording: () -> Boolean
        ): LifecycleEventObserver {
            return getInstance().createLifecycleObserverImpl(context, isRecording)
        }
        
        /**
         * 隐藏录音悬浮窗
         */
        fun hideRecordingFloatingWindow() {
            getInstance().hideRecordingFloatingWindowImpl()
        }
        
        /**
         * 更新悬浮窗中显示的文本
         */
        fun updateRecognizedText(text: String) {
            getInstance().updateRecognizedTextImpl(text)
        }
    }
    
    // 增加存储最近消息的列表
    private val latestMessages = ArrayList<String>()
    
    private var recognizedText = ""
    private var pendingText = ""
    private var pendingUpdate = false
    private var isShowingFloat = false
    private var textViewRef: WeakReference<TextView>? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val updateInterval = UPDATE_INTERVAL
    
    private var updateHandler: Handler? = null
    private var updateRunnable: Runnable? = null
    
    /**
     * 显示录音悬浮窗
     */
    fun showRecordingFloatingWindow(context: Context) {
        if (isShowingFloat) return

        try {
            // 获取屏幕宽度，设置悬浮窗宽度为屏幕的一半
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val floatWidth = screenWidth / 2
            
            // 使用传统View创建悬浮窗，避免在ComposeView上使用生命周期问题
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.argb(160, 0, 0, 0)) // 半透明黑色背景
                setPadding(dpToPx(context, 16), dpToPx(context, 8), dpToPx(context, 16), dpToPx(context, 8))
                layoutParams = ViewGroup.LayoutParams(
                    floatWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                
                // 添加点击事件，点击悬浮窗打开应用
                setOnClickListener {
                    try {
                        // 获取包名
                        val packageName = context.packageName
                        // 获取启动Intent
                        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                        if (intent != null) {
                            // 设置新任务标志
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            
                            // 隐藏悬浮窗
                            hideRecordingFloatingWindow()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "打开应用失败", e)
                    }
                }
            }
            
            // 添加"录音中"文本标签
            val labelView = TextView(context).apply {
                text = "录音中 (点击打开)"
                setTextColor(Color.WHITE)
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            layout.addView(labelView)
            
            // 创建可滚动的文本视图（垂直滚动）
            val scrollView = androidx.core.widget.NestedScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also {
                    it.height = dpToPx(context, 120) // 设置固定高度，适合3行文字加一点余量
                }
                isVerticalScrollBarEnabled = true
            }
            
            // 文本内容视图容器
            val textContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            
            // 文本内容视图
            val textView = TextView(context).apply {
                text = getLatestMessagesText()
                setTextColor(Color.WHITE)
                textSize = 16f
                maxLines = 3 // 最多显示3行
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            textContainer.addView(textView)
            scrollView.addView(textContainer)
            layout.addView(scrollView)
            
            // 使用弱引用存储TextView，避免内存泄漏
            textViewRef = WeakReference(textView)
            
            Log.d(TAG, "创建悬浮窗")
            
            EasyFloat.with(context)
                .setLayout(layout)
                .setShowPattern(ShowPattern.BACKGROUND) // 仅在应用处于后台时显示
                .setTag(FLOATING_TAG)
                .setSidePattern(SidePattern.RESULT_HORIZONTAL)
                .setGravity(Gravity.TOP, 0, 0) // 放置在顶部
                .setDragEnable(true) // 允许拖动
                .show()
            
            isShowingFloat = true
            
            // 启动定期更新检查
            startPeriodicUpdate()
            
        } catch (e: Exception) {
            Log.e(TAG, "显示悬浮窗失败", e)
        }
    }
    
    /**
     * 获取最新消息文本
     */
    fun getLatestMessagesText(): String {
        return latestMessages.joinToString("\n")
    }
    
    /**
     * 更新悬浮窗中显示的文本的实现
     */
    private fun updateRecognizedTextImpl(text: String) {
        if (text.isNotBlank() && text != recognizedText) {
            pendingText = text
            pendingUpdate = true
            
            // 添加到最近消息列表
            if (!latestMessages.contains(text)) {
                latestMessages.add(text)
                // 保持最多3条消息
                while (latestMessages.size > MAX_MESSAGES) {
                    latestMessages.removeAt(0)
                }
            }
            
            Log.d(TAG, "收到新文本更新: $text, 当前浮窗状态: $isShowingFloat, 消息数量: ${latestMessages.size}")
            
            // 立即尝试一次更新
            updateFloatingTextIfNeeded()
        }
    }
    
    /**
     * 启动定期更新机制
     */
    private fun startPeriodicUpdate() {
        stopPeriodicUpdate() // 先停止之前的
        
        // 创建定期更新任务
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                updateFloatingTextIfNeeded()
                // 继续下一轮检查
                if (isShowingFloat) {
                    mainHandler.postDelayed(this, updateInterval)
                }
            }
        }, updateInterval)
        
        Log.d(TAG, "启动定期更新检查机制")
    }
    
    /**
     * 停止定期更新机制
     */
    private fun stopPeriodicUpdate() {
        // 移除所有更新任务
        mainHandler.removeCallbacksAndMessages(null)
        Log.d(TAG, "停止定期更新检查机制")
    }
    
    /**
     * 检查并执行浮窗文本更新
     */
    private fun updateFloatingTextIfNeeded() {
        if (pendingUpdate && isShowingFloat) {
            val textView = textViewRef?.get()
            if (textView != null) {
                mainHandler.post {
                    try {
                        // 使用最新消息列表的内容
                        val displayText = getLatestMessagesText()
                        textView.text = displayText
                        recognizedText = pendingText
                        pendingUpdate = false
                        Log.d(TAG, "成功更新浮窗文本: $displayText")
                    } catch (e: Exception) {
                        Log.e(TAG, "更新浮窗文本失败", e)
                    }
                }
            } else {
                Log.w(TAG, "TextView引用已失效，无法更新文本")
            }
        }
    }
    
    /**
     * 隐藏录音悬浮窗的实现
     */
    private fun hideRecordingFloatingWindowImpl() {
        if (isShowingFloat) {
            try {
                // 停止定期更新检查
                stopPeriodicUpdate()
                
                EasyFloat.dismiss(FLOATING_TAG)
            } catch (e: Exception) {
                Log.e(TAG, "隐藏悬浮窗失败", e)
            } finally {
                isShowingFloat = false
                textViewRef = null // 释放弱引用
            }
        }
    }
    
    /**
     * 创建生命周期观察者的实现，用于处理应用切换到后台时显示悬浮窗
     */
    private fun createLifecycleObserverImpl(
        context: Context,
        isRecording: () -> Boolean
    ): LifecycleEventObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_PAUSE -> {
                try {
                    Log.d(TAG, "生命周期事件: ON_PAUSE, 录音状态: ${isRecording()}")
                    if (isRecording()) {
                        // 使用完全反射获取ViewModel
                        try {
                            val app = context.applicationContext
                            // 通过反射调用getVoiceViewModel方法
                            val getViewModelMethod = app.javaClass.getMethod("getVoiceViewModel")
                            val viewModel = getViewModelMethod.invoke(app)
                            
                            if (viewModel != null) {
                                // 通过反射调用setBackgroundState方法
                                val setBackgroundMethod = viewModel.javaClass.getMethod("setBackgroundState", Boolean::class.java)
                                setBackgroundMethod.invoke(viewModel, true)
                                Log.d(TAG, "通过反射成功设置后台状态为true")
                            } else {
                                Log.e(TAG, "无法获取ViewModel实例")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "通过反射调用方法失败", e)
                        }
                        
                        // 显示悬浮窗
                        showRecordingFloatingWindow(context)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "处理ON_PAUSE事件时出错", e)
                }
            }
            Lifecycle.Event.ON_RESUME -> {
                try {
                    Log.d(TAG, "生命周期事件: ON_RESUME")
                    
                    // 使用完全反射获取ViewModel
                    try {
                        val app = context.applicationContext
                        // 通过反射调用getVoiceViewModel方法
                        val getViewModelMethod = app.javaClass.getMethod("getVoiceViewModel")
                        val viewModel = getViewModelMethod.invoke(app)
                        
                        if (viewModel != null) {
                            // 通过反射调用setBackgroundState方法
                            val setBackgroundMethod = viewModel.javaClass.getMethod("setBackgroundState", Boolean::class.java)
                            setBackgroundMethod.invoke(viewModel, false)
                            Log.d(TAG, "通过反射成功设置后台状态为false")
                        } else {
                            Log.e(TAG, "无法获取ViewModel实例")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "通过反射调用方法失败", e)
                    }
                    
                    // 隐藏悬浮窗
                    hideRecordingFloatingWindowImpl()
                } catch (e: Exception) {
                    Log.e(TAG, "处理ON_RESUME事件时出错", e)
                }
            }
            else -> { /* 其他生命周期事件不处理 */ }
        }
    }
    
    // 工具方法：dp转px
    private fun dpToPx(context: Context, dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }
} 