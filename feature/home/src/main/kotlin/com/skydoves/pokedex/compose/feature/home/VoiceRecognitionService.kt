package com.skydoves.pokedex.compose.feature.home

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

@AndroidEntryPoint
class VoiceRecognitionService : Service() {
    
    companion object {
        private const val TAG = "VoiceRecognitionService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "voice_recognition_channel"
        private const val CHANNEL_NAME = "语音识别服务"
        
        const val ACTION_START_RECORDING = "start_recording"
        const val ACTION_STOP_RECORDING = "stop_recording"
        const val EXTRA_RECORDING_TYPE = "recording_type"
        
        fun startRecording(context: Context, recordingType: RecordingType) {
            val intent = Intent(context, VoiceRecognitionService::class.java).apply {
                action = ACTION_START_RECORDING
                putExtra(EXTRA_RECORDING_TYPE, recordingType.name)
            }
            ContextCompat.startForegroundService(context, intent)
        }
        
        fun stopRecording(context: Context) {
            val intent = Intent(context, VoiceRecognitionService::class.java).apply {
                action = ACTION_STOP_RECORDING
            }
            context.startService(intent)
        }
    }
    
    @Inject
    @Named("mic")
    lateinit var micAudioRecorder: AudioRecorder
    
    @Inject
    @Named("uplink")
    lateinit var uplinkAudioRecorder: AudioRecorder
    
    @Inject
    @Named("downlink")
    lateinit var downlinkAudioRecorder: AudioRecorder
    
    @Inject
    lateinit var speechRecognitionApi: SpeechRecognitionApi
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var recordingJob: Job? = null
    private var currentRecordingType: RecordingType? = null
    private var isRecording = false
    
    // 服务回调接口
    interface ServiceCallback {
        fun onRecognitionResult(result: RecognitionResult)
        fun onRecordingStateChanged(isRecording: Boolean)
        fun onError(error: String)
    }
    
    private var serviceCallback: ServiceCallback? = null
    
    // Binder类用于与Activity通信
    inner class LocalBinder : Binder() {
        fun getService(): VoiceRecognitionService = this@VoiceRecognitionService
    }
    
    private val binder = LocalBinder()
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VoiceRecognitionService创建")
        createNotificationChannel()
    }
    
    @RequiresApi(Build.VERSION_CODES.N)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                val recordingTypeName = intent.getStringExtra(EXTRA_RECORDING_TYPE)
                val recordingType = recordingTypeName?.let { 
                    RecordingType.valueOf(it) 
                } ?: RecordingType.MICROPHONE
                
                startRecording(recordingType)
            }
            ACTION_STOP_RECORDING -> {
                stopRecording()
            }
        }
        
        return START_NOT_STICKY
    }
    
    @RequiresApi(Build.VERSION_CODES.N)
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "VoiceRecognitionService销毁")
        stopRecording()
        serviceScope.cancel()
    }
    
    fun setServiceCallback(callback: ServiceCallback?) {
        this.serviceCallback = callback
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "语音识别后台服务通知"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(isRecording: Boolean): Notification {
        val title = if (isRecording) "正在录音..." else "语音识别服务"
        val content = when (currentRecordingType) {
            RecordingType.MICROPHONE -> "麦克风录音中"
            RecordingType.SYSTEM_CALL -> "通话录音中"
            RecordingType.SYSTEM_UPLINK -> "上行录音中"
            RecordingType.SYSTEM_DOWNLINK -> "下行录音中"
            null -> "准备就绪"
        }
        
        // 创建停止录音的PendingIntent
        val stopIntent = Intent(this, VoiceRecognitionService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(isRecording)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                if (isRecording) {
                    addAction(
                        android.R.drawable.ic_media_pause,
                        "停止录音",
                        stopPendingIntent
                    )
                }
            }
            .build()
    }
    
    @RequiresApi(Build.VERSION_CODES.N)
    private fun startRecording(recordingType: RecordingType) {
        if (isRecording) {
            Log.w(TAG, "已在录音中，忽略新的录音请求")
            return
        }
        
        Log.d(TAG, "开始录音，类型: $recordingType")
        currentRecordingType = recordingType
        isRecording = true
        
        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification(true))
        
        // 通知状态变化
        serviceCallback?.onRecordingStateChanged(true)
        
        // 系统通话录音需要特殊处理（同时监听上下行）
        if (recordingType == RecordingType.SYSTEM_CALL) {
            startSystemCallRecording()
        } else {
            startSingleChannelRecording(recordingType)
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.N)
    private fun startSingleChannelRecording(recordingType: RecordingType) {
        val audioRecorder = when (recordingType) {
            RecordingType.MICROPHONE -> micAudioRecorder
            RecordingType.SYSTEM_UPLINK -> uplinkAudioRecorder
            RecordingType.SYSTEM_DOWNLINK -> downlinkAudioRecorder
            RecordingType.SYSTEM_CALL -> micAudioRecorder // 不会执行到这里
        }
        
        recordingJob = serviceScope.launch {
            try {
                val audioFlow = audioRecorder.startRecording()
                
                speechRecognitionApi.streamAudioForRecognition(audioFlow)
                    .onEach { result ->
                        Log.d(TAG, "识别结果: ${result.text}")
                        serviceCallback?.onRecognitionResult(result)
                    }
                    .launchIn(this)
                    
            } catch (e: Exception) {
                Log.e(TAG, "录音错误", e)
                serviceCallback?.onError(e.message ?: "录音错误")
                stopRecording()
            }
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.N)
    private fun startSystemCallRecording() {
        recordingJob = serviceScope.launch {
            try {
                // 启动上行通道录音和识别
                val uplinkJob = launch {
                    try {
                        val uplinkAudioFlow = uplinkAudioRecorder.startRecording()
                        speechRecognitionApi.streamAudioForRecognition(uplinkAudioFlow)
                            .onEach { result ->
                                // 强制设置上行通道的识别结果角色ID为1（我方）
                                val modifiedResult = result.copy(speakerId = 1)
                                Log.d(TAG, "上行识别结果: ${modifiedResult.text}")
                                serviceCallback?.onRecognitionResult(modifiedResult)
                            }
                            .launchIn(this)
                    } catch (e: Exception) {
                        Log.e(TAG, "上行通道录音错误", e)
                    }
                }
                
                // 启动下行通道录音和识别
                val downlinkJob = launch {
                    try {
                        val downlinkAudioFlow = downlinkAudioRecorder.startRecording()
                        speechRecognitionApi.streamAudioForRecognition(downlinkAudioFlow)
                            .onEach { result ->
                                // 强制设置下行通道的识别结果角色ID为2（对方）
                                val modifiedResult = result.copy(speakerId = 2)
                                Log.d(TAG, "下行识别结果: ${modifiedResult.text}")
                                serviceCallback?.onRecognitionResult(modifiedResult)
                            }
                            .launchIn(this)
                    } catch (e: Exception) {
                        Log.e(TAG, "下行通道录音错误", e)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "系统通话录音错误", e)
                serviceCallback?.onError(e.message ?: "系统通话录音错误")
                stopRecording()
            }
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.N)
    private fun stopRecording() {
        if (!isRecording) {
            Log.w(TAG, "未在录音中，忽略停止录音请求")
            return
        }
        
        Log.d(TAG, "停止录音")
        
        recordingJob?.cancel()
        recordingJob = null
        
        serviceScope.launch {
            try {
                // 尝试停止所有可能的录音器
                try { micAudioRecorder.stopRecording() } catch (e: Exception) { }
                try { uplinkAudioRecorder.stopRecording() } catch (e: Exception) { }
                try { downlinkAudioRecorder.stopRecording() } catch (e: Exception) { }
            } finally {
                isRecording = false
                currentRecordingType = null
                
                // 通知状态变化
                serviceCallback?.onRecordingStateChanged(false)
                
                // 停止前台服务
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }
    
    fun getCurrentRecordingType(): RecordingType? = currentRecordingType
    fun isCurrentlyRecording(): Boolean = isRecording
} 