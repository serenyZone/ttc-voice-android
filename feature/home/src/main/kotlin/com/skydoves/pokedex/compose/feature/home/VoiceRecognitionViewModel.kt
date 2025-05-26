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

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skydoves.pokedex.compose.core.viewmodel.BaseViewModel
import com.skydoves.pokedex.compose.core.viewmodel.ViewModelStateFlow
import com.skydoves.pokedex.compose.feature.home.data.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named


@HiltViewModel
class VoiceRecognitionViewModel @Inject constructor(
  @Named("mic") private val micAudioRecorder: AudioRecorder,
  @Named("uplink") private val uplinkAudioRecorder: AudioRecorder,
  @Named("downlink") private val downlinkAudioRecorder: AudioRecorder,
  private val speechRecognitionApi: SpeechRecognitionApi,
  private val sessionRepository: SessionRepository,
  @ApplicationContext private val context: Context
) : BaseViewModel(), VoiceRecognitionService.ServiceCallback {
  companion object{
    private const val TAG="VoiceRecognitionViewModel"
    // Define Speaker IDs consistently
    const val USER_SPEAKER_ID = -1
    const val AI_SPEAKER_ID = -2
  }
  internal val uiState: ViewModelStateFlow<HomeUiState> = viewModelStateFlow(HomeUiState.Idle)
  
  // 当前会话ID
  private val _currentSessionId = MutableStateFlow<Long?>(null)
  val currentSessionId: StateFlow<Long?> = _currentSessionId.asStateFlow()
  
  // Combined message list for both voice and AI chat (managed here)
  private val _recognitionMessages = MutableStateFlow<List<RecognitionMessage>>(emptyList())
  val recognitionMessages: StateFlow<List<RecognitionMessage>> = _recognitionMessages.asStateFlow()
  
  // Filtered list containing only User and AI messages for the AI Chat tab
  val filteredAiChatMessages: StateFlow<List<RecognitionMessage>> = 
      _recognitionMessages.map { messages ->
          messages.filter { it.speakerId == USER_SPEAKER_ID || it.speakerId == AI_SPEAKER_ID }
      }.stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000), // Keep active for 5s after last subscriber
          initialValue = emptyList()
      )

  // Aggregated messages derived from recognitionMessages (used for API context)
  private val _aggregatedMessages = MutableStateFlow<List<AggregatedMessage>>(emptyList())
  val aggregatedMessages: StateFlow<List<AggregatedMessage>> = _aggregatedMessages.asStateFlow()
  
  private val _isRecording = MutableStateFlow(false)
  val isRecording: StateFlow<Boolean> = _isRecording
  
  private val _isInBackground = MutableStateFlow(false)
  val isInBackground: StateFlow<Boolean> = _isInBackground.asStateFlow()
  
  private val _permissionState = MutableStateFlow(PermissionState.UNKNOWN)
  val permissionState: StateFlow<PermissionState> = _permissionState
  
  // 服务连接相关
  private var voiceRecognitionService: VoiceRecognitionService? = null
  private var isServiceBound = false
  
  private val serviceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
      val binder = service as VoiceRecognitionService.LocalBinder
      voiceRecognitionService = binder.getService()
      voiceRecognitionService?.setServiceCallback(this@VoiceRecognitionViewModel)
      isServiceBound = true
      Log.d(TAG, "服务已连接")
    }
    
    override fun onServiceDisconnected(name: ComponentName?) {
      voiceRecognitionService = null
      isServiceBound = false
      Log.d(TAG, "服务已断开")
    }
  }
  
  init {
    // 将自身注册到Application中供全局访问
    try {
      val app = context.applicationContext
      try {
        // 使用反射调用registerVoiceViewModel方法，无需依赖于特定类型
        val registerMethod = app.javaClass.getMethod("registerVoiceViewModel", VoiceRecognitionViewModel::class.java)
        registerMethod.invoke(app, this)
        Log.d(TAG, "通过反射成功注册ViewModel到Application")
      } catch (e: NoSuchMethodException) {
        Log.e(TAG, "应用类没有registerVoiceViewModel方法", e)
      } catch (e: Exception) {
        Log.e(TAG, "通过反射注册ViewModel失败", e)
      }
    } catch (e: Exception) {
      Log.e(TAG, "注册ViewModel到Application失败", e)
    }
    
    checkPermissions()
    Log.d(TAG, "VoiceRecognitionViewModel初始化，权限状态: ${_permissionState.value}")
    
    // 绑定服务
    bindToService()
    
    // Listen to recognitionMessages changes to update aggregated messages
    viewModelScope.launch {
      _recognitionMessages.collect { messages ->
        _aggregatedMessages.value = aggregateMessages(messages)
        Log.d(TAG, "聚合消息: 原始消息=${messages.joinToString { it.text }}, 聚合后消息=${_aggregatedMessages.value.joinToString { it.text }}")
      }
    }
  }
  
  // 实现ServiceCallback接口
  override fun onRecognitionResult(result: RecognitionResult) {
    val newMessage = RecognitionMessage(
      speakerId = result.speakerId, 
      text = result.text, 
      sentenceId = result.sentenceId,
      beginTime = result.beginTime
    )
    updateOrAddMessage(newMessage)
    
    // 保存识别消息到数据库
    _currentSessionId.value?.let { sessionId ->
      viewModelScope.launch {
        sessionRepository.addRecognitionMessage(
          sessionId = sessionId,
          speakerId = result.speakerId,
          text = result.text,
          sentenceId = result.sentenceId,
          beginTime = result.beginTime
        )
      }
    }
    
    // 如果在后台模式，实时更新浮窗文本
    if (_isInBackground.value) {
      FloatingWindowManager.updateRecognizedText(result.text)
    }
  }
  
  override fun onRecordingStateChanged(isRecording: Boolean) {
    _isRecording.value = isRecording
    
    if (isRecording) {
      // 只有首次启动录音时清空消息列表，后台返回前台时不清空
      if (!_isInBackground.value) {
        _recognitionMessages.value = emptyList() 
      }
    }
    
    uiState.tryEmit(key, if (isRecording) HomeUiState.Recording else HomeUiState.Idle)
  }
  
  override fun onError(error: String) {
    uiState.tryEmit(key, HomeUiState.Error(error))
    _isRecording.value = false
    
    // 结束会话并标记为错误
    _currentSessionId.value?.let { sessionId ->
      viewModelScope.launch {
        sessionRepository.endSession(sessionId, "ERROR")
      }
    }
  }
  
  private fun bindToService() {
    val intent = Intent(context, VoiceRecognitionService::class.java)
    context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
  }
  
  private fun unbindFromService() {
    if (isServiceBound) {
      voiceRecognitionService?.setServiceCallback(null)
      context.unbindService(serviceConnection)
      isServiceBound = false
    }
  }
  
  private fun aggregateMessages(messages: List<RecognitionMessage>): List<AggregatedMessage> {
    if (messages.isEmpty()) return emptyList()
    
    val result = mutableListOf<AggregatedMessage>()
    var currentAggregated: AggregatedMessage? = null
    
    messages.forEach { message ->
        if (currentAggregated == null || message.speakerId != currentAggregated!!.speakerId || !currentAggregated!!.isStreaming) {
            currentAggregated?.let { result.add(it) } 
            currentAggregated = AggregatedMessage.fromRecognitionMessage(message)
        } else {
            currentAggregated = AggregatedMessage.merge(currentAggregated!!, message)
        }
    }
    
    currentAggregated?.let { result.add(it) } 
    
    // Log.d(TAG, "聚合消息: 原始消息数量=${messages.size}, 聚合后消息数量=${result.size}") // Keep log less noisy
    return result
  }
  
  fun checkPermissions() {
    val hasRecordPermission = PermissionHelper.hasRecordAudioPermission(context)
    val hasStoragePermission = PermissionHelper.hasStoragePermissions(context)
    
    _permissionState.value = if (hasRecordPermission) {
      PermissionState.GRANTED
    } else {
      PermissionState.REQUIRED
    }
    
    Log.d(TAG, "权限状态: ${_permissionState.value}")
    Log.d(TAG, "录音权限: $hasRecordPermission, 存储权限: $hasStoragePermission")
    
    if (!hasRecordPermission) {
      Log.e(TAG, "录音权限检查结果: ContextCompat.checkSelfPermission结果为: ${
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
      }, 需要结果为: ${PackageManager.PERMISSION_GRANTED}")
    }
    
    if (!hasStoragePermission) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Log.e(TAG, "存储权限检查结果: Environment.isExternalStorageManager()结果为: ${Environment.isExternalStorageManager()}")
        Log.w(TAG, "存储权限未获取，但这是可选的，不影响基本录音功能")
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Log.e(TAG, "存储权限检查结果: READ_EXTERNAL_STORAGE权限结果为: ${
          ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }")
        Log.w(TAG, "存储权限未获取，但这是可选的，不影响基本录音功能")
      }
    }
    
    Log.d(TAG, "应用包名: ${context.packageName}")
  }
  
  fun setPermissionState(state: PermissionState) {
    _permissionState.value = state
  }
  
  fun startRecording() {
    startRecording(RecordingType.MICROPHONE)
  }
  
  fun startRecording(recordingType: RecordingType) {
    if (_permissionState.value != PermissionState.GRANTED) {
      Log.e(TAG, "没有足够的权限进行录音")
      uiState.tryEmit(key, HomeUiState.Error("没有足够的权限进行录音"))
      return
    }
    if (_isRecording.value) return
    
    // 首先设置为Loading状态
    uiState.tryEmit(key, HomeUiState.Loading)
    
    // 创建新会话并开始录音
    viewModelScope.launch {
      try {
        val sessionId = sessionRepository.createSession(recordingType.name)
        _currentSessionId.value = sessionId
        Log.d(TAG, "创建新会话: $sessionId, 录音类型: ${recordingType.name}")
        
        // 通过服务开始录音
        VoiceRecognitionService.startRecording(context, recordingType)
        
        // 简单地等待3秒，让Loading状态显示足够长时间
        delay(3000)
        
        // 3秒后如果还在录音，设置为Recording状态
        if (_isRecording.value) {
          Log.d(TAG, "Loading状态维持3秒后，切换到Recording状态")
          uiState.tryEmit(key, HomeUiState.Recording)
        }
        
      } catch (e: Exception) {
        Log.e(TAG, "创建会话失败", e)
        uiState.tryEmit(key, HomeUiState.Error("创建会话失败: ${e.message}"))
      }
    }
  }
  
  fun stopRecording() {
    if (!_isRecording.value) return
    
    viewModelScope.launch {
      try {
        // 通过服务停止录音
        VoiceRecognitionService.stopRecording(context)
        
        // 结束当前会话
        _currentSessionId.value?.let { sessionId ->
          sessionRepository.endSession(sessionId, "COMPLETED")
          Log.d(TAG, "结束会话: $sessionId")
        }
        
      } catch (e: Exception) {
        Log.e(TAG, "停止录音失败", e)
      }
    }
  }
  
  /**
   * 设置应用后台状态
   */
  fun setBackgroundState(isInBackground: Boolean) {
    Log.d(TAG, "设置后台状态: $isInBackground, 当前录音状态: ${_isRecording.value}")
    Log.d(TAG, "后台状态变更: isInBackground=$isInBackground, isRecording=${_isRecording.value}, 当前aggregatedMessages数量=${_aggregatedMessages.value.size}")
    
    _isInBackground.value = isInBackground
    
    // 同步更新讯飞SDK的后台状态
    if (speechRecognitionApi is XfyunSpeechRecognitionApi) {
      try {
        (speechRecognitionApi as XfyunSpeechRecognitionApi).setBackgroundState(isInBackground)
        Log.d(TAG, "成功设置讯飞SDK后台状态: $isInBackground")
      } catch (e: Exception) {
        Log.e(TAG, "设置讯飞SDK后台状态失败: ${e.message}", e)
      }
    }
  }
  
  fun addUserMessage(text: String) {
     val userMessage = RecognitionMessage(USER_SPEAKER_ID, text, System.currentTimeMillis().toInt(), System.currentTimeMillis())
     _recognitionMessages.value = _recognitionMessages.value + userMessage
     
     // 保存用户消息到数据库
     _currentSessionId.value?.let { sessionId ->
       viewModelScope.launch {
         sessionRepository.addAiChatMessage(
           sessionId = sessionId,
           speakerId = USER_SPEAKER_ID,
           text = text,
           sentenceId = System.currentTimeMillis().toInt(),
           beginTime = System.currentTimeMillis()
         )
       }
     }
  }

  fun updateOrAddAiMessage(aiMessage: RecognitionMessage) {
      updateOrAddMessage(aiMessage)
      
      // 保存AI消息到数据库
      _currentSessionId.value?.let { sessionId ->
        viewModelScope.launch {
          sessionRepository.addAiChatMessage(
            sessionId = sessionId,
            speakerId = aiMessage.speakerId,
            text = aiMessage.text,
            sentenceId = aiMessage.sentenceId,
            beginTime = aiMessage.beginTime,
            isStreaming = aiMessage.isStreaming
          )
        }
      }
  }

  fun finishAiStreaming(aiMessageId: Int) {
      val index = _recognitionMessages.value.indexOfFirst { it.sentenceId == aiMessageId && it.speakerId == AI_SPEAKER_ID }
      if (index >= 0) {
          val updatedList = _recognitionMessages.value.toMutableList()
          updatedList[index].isStreaming = false
          _recognitionMessages.value = updatedList
      }
  }
  
  /**
   * 加载历史会话
   */
  fun loadSession(sessionId: Long) {
    viewModelScope.launch {
      try {
        sessionRepository.getSessionDetail(sessionId).collect { sessionDetail ->
          sessionDetail?.let { detail ->
            _currentSessionId.value = sessionId
            
            // 合并识别消息和AI聊天消息
            val recognitionMessages = detail.recognitionMessages.map { entity ->
              RecognitionMessage(
                speakerId = entity.speakerId,
                text = entity.text,
                sentenceId = entity.sentenceId,
                beginTime = entity.beginTime,
                isStreaming = entity.isStreaming
              )
            }
            
            val aiChatMessages = detail.aiChatMessages.map { entity ->
              RecognitionMessage(
                speakerId = entity.speakerId,
                text = entity.text,
                sentenceId = entity.sentenceId,
                beginTime = entity.beginTime,
                isStreaming = entity.isStreaming
              )
            }
            
            // 按时间排序合并所有消息
            val allMessages = (recognitionMessages + aiChatMessages)
              .sortedBy { it.beginTime }
            
            _recognitionMessages.value = allMessages
            Log.d(TAG, "加载历史会话: $sessionId, 消息数量: ${allMessages.size}")
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "加载会话失败", e)
        uiState.tryEmit(key, HomeUiState.Error("加载会话失败: ${e.message}"))
      }
    }
  }
  
  /**
   * 开始新会话（清空当前消息）
   */
  fun startNewSession() {
    _currentSessionId.value = null
    _recognitionMessages.value = emptyList()
    Log.d(TAG, "开始新会话，清空消息列表")
  }

  internal fun updateOrAddMessage(newMessage: RecognitionMessage) {
      val existingMessageIndex = _recognitionMessages.value.indexOfFirst { 
          it.sentenceId == newMessage.sentenceId && it.speakerId == newMessage.speakerId
      }
      
      // 添加识别结果日志
      if (_isInBackground.value) {
          Log.d(TAG, "后台模式收到识别结果: speakerId=${newMessage.speakerId}, text='${newMessage.text}'")
      }
      
      _recognitionMessages.value = if (existingMessageIndex >= 0) {
          val updatedList = _recognitionMessages.value.toMutableList()
          updatedList[existingMessageIndex] = newMessage
          updatedList
      } else {
          _recognitionMessages.value + newMessage
      }
      
      // 如果正在录音，更新悬浮窗文本
      if (_isRecording.value) {
          // 使用最新的消息或者最新的几条消息更新悬浮窗
          val latestMessage = _recognitionMessages.value.lastOrNull()
          latestMessage?.let {
              if (_isInBackground.value) {
                  Log.d(TAG, "后台模式更新浮窗: text='${it.text}'")
              }
              FloatingWindowManager.updateRecognizedText(it.text)
      }
    }
  }
  
  override fun onCleared() {
    super.onCleared()
    viewModelScope.launch {
      // 停止录音服务
      if (_isRecording.value) {
        VoiceRecognitionService.stopRecording(context)
      }
    }
    unbindFromService()
  }
}

@Stable
sealed interface HomeUiState {
  data object Idle : HomeUiState
  data object Loading : HomeUiState
  data object Recording : HomeUiState
  data class Error(val message: String?) : HomeUiState
}

// 添加录音类型枚举
enum class RecordingType {
  MICROPHONE,       // 麦克风录音
  SYSTEM_CALL,      // 系统通话音频(同时监听上下行)
  SYSTEM_UPLINK,    // 系统上行音频（自己的声音）- 内部使用
  SYSTEM_DOWNLINK   // 系统下行音频（对方的声音）- 内部使用
}

// 添加扩展函数
fun HomeUiState.toString(): String {
  return when (this) {
    is HomeUiState.Idle -> "Idle"
    is HomeUiState.Loading -> "Loading"
    is HomeUiState.Recording -> "Recording"
    is HomeUiState.Error -> "Error"
  }
}
