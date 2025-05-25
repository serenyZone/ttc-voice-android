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
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skydoves.pokedex.compose.core.viewmodel.BaseViewModel
import com.skydoves.pokedex.compose.core.viewmodel.ViewModelStateFlow
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
  @ApplicationContext private val context: Context
) : BaseViewModel() {
  companion object{
    private const val TAG="VoiceRecognitionViewModel"
    // Define Speaker IDs consistently
    const val USER_SPEAKER_ID = -1
    const val AI_SPEAKER_ID = -2
  }
  internal val uiState: ViewModelStateFlow<HomeUiState> = viewModelStateFlow(HomeUiState.Idle)
  
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
  
  private var recordingJob: Job? = null
  
  // 当前使用的录音器
  private var currentAudioRecorder: AudioRecorder = micAudioRecorder
  
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
    
    // Listen to recognitionMessages changes to update aggregated messages
    viewModelScope.launch {
      _recognitionMessages.collect { messages ->
        _aggregatedMessages.value = aggregateMessages(messages)
        Log.d(TAG, "聚合消息: 原始消息=${messages.joinToString { it.text }}, 聚合后消息=${_aggregatedMessages.value.joinToString { it.text }}")
      }
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
    
    // 系统通话录音需要特殊处理（同时监听上下行）
    if (recordingType == RecordingType.SYSTEM_CALL) {
      startSystemCallRecording()
      return
    }
    
    // 设置当前录音器
    currentAudioRecorder = when (recordingType) {
      RecordingType.MICROPHONE -> micAudioRecorder
      RecordingType.SYSTEM_UPLINK -> uplinkAudioRecorder
      RecordingType.SYSTEM_DOWNLINK -> downlinkAudioRecorder
      RecordingType.SYSTEM_CALL -> micAudioRecorder // 不会执行到这里
    }
    
    Log.d(TAG, "开始录音，类型: $recordingType")
    
    // 首先设置为Loading状态
    uiState.tryEmit(key, HomeUiState.Loading)
    
    recordingJob = viewModelScope.launch {
      try {
        val audioFlow = currentAudioRecorder.startRecording()
        
        // 先设置录音状态，但还不更新UI状态
        _isRecording.value = true
        
        // 只有首次启动录音时清空消息列表，后台返回前台时不清空
        if (!_isInBackground.value) {
          _recognitionMessages.value = emptyList() 
        }
        
        // 启动语音识别
        speechRecognitionApi.streamAudioForRecognition(audioFlow)
          .onEach { result ->
            val newMessage = RecognitionMessage(
              speakerId = result.speakerId, 
              text = result.text, 
              sentenceId = result.sentenceId,
              beginTime = result.beginTime
            )
            updateOrAddMessage(newMessage)
            
            // 如果在后台模式，实时更新浮窗文本
            if (_isInBackground.value) {
              FloatingWindowManager.updateRecognizedText(result.text)
            }
          }
          .launchIn(this)
        
        // 简单地等待3秒，让Loading状态显示足够长时间
        delay(3000)
        
        // 3秒后设置为Recording状态
        Log.d(TAG, "Loading状态维持3秒后，切换到Recording状态")
        uiState.tryEmit(key, HomeUiState.Recording)
        
      } catch (e: Exception) {
        Log.e(TAG,"record error",e)
        uiState.tryEmit(key, HomeUiState.Error(e.message))
        _isRecording.value = false 
      }
    }
  }
  
  /**
   * 同时启动系统上行和下行通道的录音并分别识别
   */
  private fun startSystemCallRecording() {
    Log.d(TAG, "开始系统通话录音（同时监听上下行）")
    
    // 首先设置为Loading状态
    uiState.tryEmit(key, HomeUiState.Loading)
    
    recordingJob = viewModelScope.launch {
      try {
        // 只有首次启动录音时清空消息列表，后台返回前台时不清空
        if (!_isInBackground.value) {
          _recognitionMessages.value = emptyList() 
        }
        
        // 设置录音状态
        _isRecording.value = true
        
        // 启动上行通道录音和识别
        val uplinkJob = launch {
          try {
            val uplinkAudioFlow = uplinkAudioRecorder.startRecording()
            speechRecognitionApi.streamAudioForRecognition(uplinkAudioFlow)
              .onEach { result ->
                // 强制设置上行通道的识别结果角色ID为1（我方）
                val newMessage = RecognitionMessage(
                  speakerId = 1, // 固定为角色1 - 我方
                  text = result.text,
                  sentenceId = result.sentenceId,
                  beginTime = result.beginTime
                )
                updateOrAddMessage(newMessage)
                
                // 如果在后台模式，实时更新浮窗文本
                if (_isInBackground.value) {
                  FloatingWindowManager.updateRecognizedText(result.text)
                }
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
                val newMessage = RecognitionMessage(
                  speakerId = 2, // 固定为角色2 - 对方
                  text = result.text,
                  sentenceId = result.sentenceId,
                  beginTime = result.beginTime
                )
                updateOrAddMessage(newMessage)
              }
              .launchIn(this)
          } catch (e: Exception) {
            Log.e(TAG, "下行通道录音错误", e)
          }
        }
        
        // 简单地等待3秒，让Loading状态显示足够长时间
        delay(3000)
        
        // 3秒后设置为Recording状态
        Log.d(TAG, "Loading状态维持3秒后，切换到Recording状态")
        uiState.tryEmit(key, HomeUiState.Recording)
        
      } catch (e: Exception) {
        Log.e(TAG, "系统通话录音错误", e)
        uiState.tryEmit(key, HomeUiState.Error(e.message))
        _isRecording.value = false
      }
    }
  }
  
  fun stopRecording() {
    if (!_isRecording.value) return
    recordingJob?.cancel()
    recordingJob = null
    viewModelScope.launch {
      try {
        // 尝试停止所有可能的录音器
        try { micAudioRecorder.stopRecording() } catch (e: Exception) { }
        try { uplinkAudioRecorder.stopRecording() } catch (e: Exception) { }
        try { downlinkAudioRecorder.stopRecording() } catch (e: Exception) { }
      } finally {
        _isRecording.value = false
        uiState.tryEmit(key, HomeUiState.Idle)
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
  }

  fun updateOrAddAiMessage(aiMessage: RecognitionMessage) {
      updateOrAddMessage(aiMessage)
  }

  fun finishAiStreaming(aiMessageId: Int) {
      val index = _recognitionMessages.value.indexOfFirst { it.sentenceId == aiMessageId && it.speakerId == AI_SPEAKER_ID }
      if (index >= 0) {
          val updatedList = _recognitionMessages.value.toMutableList()
          updatedList[index].isStreaming = false
          _recognitionMessages.value = updatedList
      }
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
    recordingJob?.cancel()
    viewModelScope.launch {
      // 停止所有可能的录音器
      try { micAudioRecorder.stopRecording() } catch (e: Exception) { }
      try { uplinkAudioRecorder.stopRecording() } catch (e: Exception) { }
      try { downlinkAudioRecorder.stopRecording() } catch (e: Exception) { }
    }
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
