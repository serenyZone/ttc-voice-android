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
import android.util.Log
import com.iflytek.sparkchain.core.SparkChain;
import com.iflytek.sparkchain.core.SparkChainConfig;
import com.iflytek.sparkchain.core.rtasr.RTASR
import com.iflytek.sparkchain.core.rtasr.RTASRCallbacks
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject
import org.json.JSONException

/**
 * 用于返回四个值的数据类
 */
data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/**
 * 详细状态跟踪计数器
 */
private var audioChunkSizes = mutableListOf<Int>() // 记录音频块大小
private var audioProcessingTimes = mutableListOf<Long>() // 记录处理时间
private var callbackCounts = mapOf(0 to 0, 1 to 0, 2 to 0, 3 to 0).toMutableMap() // 按状态统计回调次数
private var lastCallbackTime = 0L // 上次回调时间
private var isAppInBackground = false // 应用是否在后台

/**
 * 讯飞实时语音识别API的实现
 * 
 * 使用前需要确保以下权限已配置：
 * - INTERNET：必须权限，SDK需要访问网络获取结果
 * - READ_EXTERNAL_STORAGE：必须权限，SDK需要判断日志路径是否存在
 * - WRITE_EXTERNAL_STORAGE：必须权限，SDK写本地日志需要用到该权限
 * - MANAGE_EXTERNAL_STORAGE：可选权限，安卓10以上设备用于动态授权
 * 
 * 同时Android 10.0（API 29）及以上版本需要在application中做如下配置:
 * ```
 * <application android:requestLegacyExternalStorage="true"/>
 * ```
 * 
 * 混淆配置:
 * ```
 * -keep class com.iflytek.sparkchain.** {*;} 
 * -keep class com.iflytek.sparkchain.**
 * ```
 */
@Singleton
class XfyunSpeechRecognitionApi @Inject constructor(
  @ApplicationContext private val context: Context
) : SpeechRecognitionApi {
  
  // 讯飞语音识别的配置参数
  companion object {
    private const val APP_ID = "42a11618" // 应用ID
    private const val API_KEY = "3a2d0c58ce8ccc93003473c35435fe38" // API Key
    private const val API_SECRET = "OUY5RkY0RTZFMzAyM0EzNkRGOTlDODY5Q0U3QUQ2OEY=" // API Secret
    private const val TAG = "XfyunSpeechRecognition"
    
    // 日志级别定义
    private const val LOG_LEVEL_VERBOSE = 0
    private const val LOG_LEVEL_DEBUG = 1
    private const val LOG_LEVEL_INFO = 2
    private const val LOG_LEVEL_WARN = 3
    private const val LOG_LEVEL_ERROR = 4
    private const val LOG_LEVEL_FATAL = 5
    private const val LOG_LEVEL_OFF = 100
  }
  
  // 语音识别状态
  private var isRunning = false
  
  // 当前识别会话ID
  private var sessionCount = 0
  
  // 存储最终识别结果
  private val asrFinalResult = StringBuilder()
  private val transFinalResult = StringBuilder("翻译结果：\n")
  
  // 当前使用的识别模式 (文件或麦克风)
  private var startMode = "NONE"
  
  // 角色ID生成器，用于模拟多人对话场景下的角色分配
  private val speakerIdGenerator = sequence {
    var id = 0
    while(true) {
      yield((id++ % 4) + 1)
    }
  }.iterator()
  
  // 讯飞实时语音识别实例
  private var rtasr: RTASR? = null
  
  // 当前正在使用的Flow生产者作用域，用于在回调中发送数据
  private var currentProducerScope: ProducerScope<RecognitionResult>? = null
  
  // 标记SDK是否已初始化
  private var isSDKInitialized = false
  
  // 跟踪当前说话的角色ID
  private var currentRoleId = 1
  private var currentSentenceId = 0
  private var lastSentenceType = -1 // -1: 初始状态, 0: 最终结果, 1: 中间结果
  
  // 存储句子ID与说话人ID的映射关系
  private val sentenceSpeakerMap = mutableMapOf<Int, Int>()
  
  // 会话状态
  private var isMessageInProgress = false
  private val currentMessageBuilder = StringBuilder()
  
  // 添加音频数据计数器
  private var audioChunkCount = 0
  private var lastLogTime = 0L
  
  /**
   * 初始化SparkChain SDK
   */
  private fun initSparkChainSDK() {
    if (!isSDKInitialized) {
      try {
        Log.d(TAG, "讯飞SDK: 开始初始化SDK")
        // 检查权限
        if (!PermissionHelper.hasStoragePermissions(context)) {
          Log.w(TAG, "没有完整的存储权限，讯飞SDK可能无法正常工作")
        }
        
        // 获取应用内部存储目录
        val workDir = context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath
        Log.d(TAG, "使用工作目录: $workDir")
        
        // 配置应用信息
        val config = SparkChainConfig.builder()
          .appID(APP_ID)
          .apiKey(API_KEY)
          .apiSecret(API_SECRET)
          .workDir(workDir) // 使用应用内部存储路径，确保应用有访问权限
          .logLevel(LOG_LEVEL_DEBUG)
        
        // 初始化SDK
        val ret = SparkChain.getInst().init(context, config)
        if (ret == 0) {
          Log.d(TAG, "SparkChain SDK初始化成功")
          isSDKInitialized = true
        } else {
          Log.e(TAG, "SparkChain SDK初始化失败，错误码: $ret")
        }
      } catch (e: Exception) {
        Log.e(TAG, "SparkChain SDK初始化异常: ${e.message}", e)
      }
    }
  }
  
  /**
   * 初始化讯飞语音识别引擎
   */
  private fun initRTASR() {
    // 首先初始化SDK
    initSparkChainSDK()
    
    if (rtasr == null && isSDKInitialized) {
      Log.d(TAG, "讯飞SDK: 创建RTASR实例")
      rtasr = RTASR(API_KEY)
    }
  }
  
  /**
   * 创建讯飞语音识别回调
   */
  private fun createRtasrCallbacks(producerScope: ProducerScope<RecognitionResult>): RTASRCallbacks {
    lastCallbackTime = System.currentTimeMillis()
    return object : RTASRCallbacks {
      override fun onResult(result: RTASR.RtAsrResult, usrTag: Any?) {
        val data = result.data  // 识别结果
        val status = result.status  // 数据状态
        val rawResult = result.rawResult // 原始结果
        val dst = result.transResult.dst  // 翻译结果
        val src = result.transResult.src  // 翻译源文本
        val transStatus = result.transResult.status  // 翻译状态
        val sid = result.sid  // 交互sid
        
        // 更新回调统计
        callbackCounts[status] = (callbackCounts[status] ?: 0) + 1
        val currentTime = System.currentTimeMillis()
        val timeSinceLastCallback = currentTime - lastCallbackTime
        lastCallbackTime = currentTime
        
        when (status) {
          1 -> {
            // 详细记录中间结果
            try {
              // 尝试解析中间结果，与status=2同样处理
              val parsedInfo = extractRoleIdAndText(rawResult)
              if (parsedInfo != null) {
                val (roleId, text, type, beginTime) = parsedInfo
                
                // 中间结果在后台模式也要发送，保持引擎活跃
                if (isAppInBackground && text.isNotEmpty()) {
                  val effectiveRoleId = if (roleId > 0) roleId else currentRoleId
                  
                  // 发送中间结果，但不修改UI（通过其他逻辑控制）
                  val recognitionResult = RecognitionResult(
                    speakerId = effectiveRoleId,
                    text = text,
                    sentenceId = currentSentenceId,
                    beginTime = beginTime
                  )
                  
                  val sendResult = producerScope.trySend(recognitionResult)
                } else {
                  Log.d(TAG, "前台模式中间结果不发送，待最终结果")
                }
              } else {
                Log.w(TAG, "中间结果解析失败")
              }
            } catch (e: Exception) {
              Log.e(TAG, "解析中间结果异常: ${e.message}")
            }
          }
          2 -> {
            // 子句流式结果和plain结果都走相同的处理逻辑
            // status=1为中间结果，status=2为最终结果
            if (status == 2) {
              // 为status=2的情况添加日志
              asrFinalResult.append(data)
            }
            
            try {
              // 提取角色ID、文本和句子类型
              val parsedInfo = extractRoleIdAndText(rawResult)
              if (parsedInfo != null) {
                val (roleId, text, type, beginTime) = parsedInfo
                
                // 确定有效的角色ID，roleId===0 代表角色无变化，继续使用currentRoleId
                val effectiveRoleId = if (roleId > 0) {
                  roleId // 使用API返回的角色ID，包括0
                } else  {
                  currentRoleId // 使用当前记录的角色ID
                }
                
                // 处理句子ID逻辑
                var sentenceId = currentSentenceId
                var isNewSentence = false
                
                if (type == 0) { // 最终结果
                  if (lastSentenceType == 1) {
                    // 前一条是中间结果，这是同一句话的最终结果
                    // 保持当前句子ID不变
                  } else if (lastSentenceType == 0) {
                    // 上一条是最终结果，创建新ID
                    sentenceId = ++currentSentenceId
                    isNewSentence = true
                    sentenceSpeakerMap[sentenceId] = effectiveRoleId
                  } else {
                    // 初始状态，这是新句子的开始
                    sentenceId = ++currentSentenceId
                    isNewSentence = true
                    sentenceSpeakerMap[sentenceId] = effectiveRoleId
                  }
                } else if (type == 1) { // 中间结果,中间结果的角色id是无效的，使用0表示
                  if (lastSentenceType == 0) {
                    // 前一条是最终结果，创建新ID
                    sentenceId = ++currentSentenceId
                    isNewSentence = true
                    sentenceSpeakerMap[sentenceId] = effectiveRoleId
                  } else if (lastSentenceType == -1) {
                    // 初始状态，这是新句子的开始
                    sentenceId = ++currentSentenceId
                    isNewSentence = true
                    sentenceSpeakerMap[sentenceId] = effectiveRoleId
                  } else {
                    // 前一条也是中间结果，保持当前ID不变
                  }
                }
                
                // 更新上一次句子类型
                lastSentenceType = type
                
                // 更新角色ID（仅当提取到角色ID大于0时）
                if (roleId > 0) {  // 保持大于0的条件，以便区分0和有效的正整数角色ID
                  currentRoleId = roleId
                }
                
                // 直接发送识别结果，不做文本积累处理
                // 这样UI层收到的总是最新的结果，而不是积累的结果
                val recognitionResult = RecognitionResult(
                  speakerId = effectiveRoleId, 
                  text = text,
                  sentenceId = sentenceId,
                  beginTime = beginTime
                )
                
                val sendResult = producerScope.trySend(recognitionResult)
              } else {
                Log.w(TAG, "提取角色和文本失败，")
              }
            } catch (e: Exception) {
              Log.e(TAG, "解析原始结果失败: ${e.message}", e)
            }
          }
          3 -> {
            // end结果 - 整个识别过程结束
            stopRecognition()
          }
          0 -> {
            // 翻译结果
            if (transStatus == 2) {
              // 翻译end结果
            } else {
              // 翻译中间结果
            }
          }
          else -> {
            Log.d(TAG, "未知状态: $status")
          }
        }
      }
      
      override fun onError(error: RTASR.RtAsrError, usrTag: Any?) {
        val code = error.code  // 错误码
        val msg = error.errMsg  // 错误信息
        val sid = error.sid  // 交互sid
        
        val errorMessage = "语音识别错误: $msg (错误码: $code, sid: $sid)"
        producerScope.close(CancellationException(errorMessage))
      }
      
      override fun onBeginOfSpeech() {
        Log.d(TAG, "开始检测到语音")
      }
      
      override fun onEndOfSpeech() {
        Log.d(TAG, "检测到语音结束")
      }
    }
  }
  
  /**
   * 从原始JSON结果中提取角色ID、文本和句子类型
   * @return Quadruple<Int, String, Int, Long> 角色ID、文本内容、句子类型（0最终结果，1中间结果）、开始时间
   */
  private fun extractRoleIdAndText(jsonStr: String): Quadruple<Int, String, Int, Long>? {
    try {
      val jsonObject = JSONObject(jsonStr)
      
      // 从外层解析出data字段，注意这是字符串格式
      val dataStr = jsonObject.optString("data")
      if (dataStr.isBlank()) {
        return null
      }
      
      // 将data字段内容解析为JSON对象
      val data = JSONObject(dataStr)
      
      // 获取cn和st对象
      val cnObj = data.optJSONObject("cn")
      if (cnObj == null) {
        return null
      }
      
      val stObj = cnObj.optJSONObject("st")
      if (stObj == null) {
        return null
      }
      
      // 获取类型（0最终结果，1中间结果）
      val typeStr = stObj.optString("type")
      if (typeStr.isBlank()) {
        return null
      }
      val type = typeStr.toIntOrNull() ?: -1
      
      // 获取开始时间
      val bgStr = stObj.optString("bg")
      val beginTime = bgStr.toLongOrNull() ?: 0L
      
      // 提取文本和角色ID
      val sb = StringBuilder()
      var roleId = 0 
      
      // 获取rt数组
      val rtArray = stObj.optJSONArray("rt")
      if (rtArray == null || rtArray.length() == 0) {
        return null
      }
      
      for (i in 0 until rtArray.length()) {
        val rtObj = rtArray.optJSONObject(i)
        if (rtObj != null) {
          // 获取ws数组
          val wsArray = rtObj.optJSONArray("ws")
          if (wsArray != null) {
            for (j in 0 until wsArray.length()) {
              val wsObj = wsArray.optJSONObject(j)
              if (wsObj != null) {
                // 获取cw数组
                val cwArray = wsObj.optJSONArray("cw")
                if (cwArray != null && cwArray.length() > 0) {
                  for (k in 0 until cwArray.length()) {
                    val cwObj = cwArray.optJSONObject(k)
                    if (cwObj != null) {
                      // 提取word
                      val word = cwObj.optString("w", "")
                      sb.append(word)
                      
                      // 检查有无rl字段
                      val hasRl = cwObj.has("rl")
                      if (hasRl) {
                        if (roleId == 0) {
                          val rlStr = cwObj.optString("rl", "-1")
                          roleId = rlStr.toIntOrNull() ?: 0
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
      
      val text = sb.toString()
      return Quadruple(roleId, text, type, beginTime)
    } catch (e: Exception) {
      return null
    }
  }
  
  /**
   * 停止语音识别
   */
  private fun stopRecognition() {
    if (isRunning) {
      when (startMode) {
        "AUDIO" -> rtasr?.stopListener()
        "FILE" -> rtasr?.stop()
      }
      startMode = "NONE"
      isRunning = false
    }
  }
  
  /**
   * 从音频流中获取语音识别结果
   */
  override fun streamAudioForRecognition(audioBytes: Flow<AudioData>): Flow<RecognitionResult> = callbackFlow {
    var networkInfo: String = "未知"
    try {
      val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
      val netInfo = connectivityManager?.activeNetworkInfo
      val isConnected = netInfo?.isConnected ?: false
      val networkType = when(netInfo?.type) {
        android.net.ConnectivityManager.TYPE_WIFI -> "WiFi"
        android.net.ConnectivityManager.TYPE_MOBILE -> "移动数据"
        else -> "未知"
      }
      networkInfo = "$networkType(${if(isConnected) "已连接" else "断开"})"
    } catch (e: Exception) {
    }
    
    Log.d(TAG, "开始音频流处理")
    
    if (rtasr == null) {
      initRTASR()
    }
    
    if (isRunning) {
      stopRecognition()
    }
    
    currentProducerScope = this
    
    // 设置回调
    val callbacks = createRtasrCallbacks(this)
    rtasr?.registerCallbacks(callbacks)
    
    // 设置识别参数
    rtasr?.apply {
      // 设置转写参数
      lang("cn")            // 设置语言为中文
      roleType(2)           // 开启角色分离
    }
    
    // 重置结果缓存
    asrFinalResult.clear().append("识别结果：\n")
    transFinalResult.clear().append("翻译结果：\n")
    
    // 重置句子管理状态
    currentRoleId = 1
    currentSentenceId = 0
    lastSentenceType = -1
    sentenceSpeakerMap.clear()
    
    // 重置音频计数器和统计信息
    audioChunkCount = 0
    lastLogTime = System.currentTimeMillis()
    audioChunkSizes.clear()
    audioProcessingTimes.clear()
    callbackCounts.keys.forEach { callbackCounts[it] = 0 }
    
    try {
      // 开始语音识别
      startMode = "FILE" // 使用FILE模式表示手动提供音频数据
      isRunning = true
      sessionCount++
      
      // 使用start而不是startListener，因为我们要手动处理传入的音频数据
      val ret = rtasr?.start(sessionCount.toString()) ?: -1

      if (ret != 0) {
        val errorMsg = "语音识别启动失败，错误码: $ret"
        throw Exception(errorMsg)
      }
      
      // 处理传入的音频数据
      val audioProcessingJob = launch(Dispatchers.IO) {
        try {
          audioBytes.collect { audioData ->
            if (!isRunning) {
              return@collect
            }
            
            when (audioData) {
              is AudioData.AudioChunk -> {
                // 记录处理开始时间
                val processStartTime = System.currentTimeMillis()
                
                // 处理普通音频数据
                rtasr?.write(audioData.bytes)
                audioChunkCount++
                
                // 记录音频块大小
                audioChunkSizes.add(audioData.bytes.size)
                
                // 计算处理耗时
                val processingTime = System.currentTimeMillis() - processStartTime
                audioProcessingTimes.add(processingTime)
                
                // 每100个音频块或10秒记录一次
                val now = System.currentTimeMillis()
                if (audioChunkCount % 100 == 0 || now - lastLogTime > 10000) {
                  lastLogTime = now
                  // 计算平均处理时间和大小
                  val avgSize = if (audioChunkSizes.isNotEmpty()) 
                    audioChunkSizes.average() else 0.0
                  val avgTime = if (audioProcessingTimes.isNotEmpty()) 
                    audioProcessingTimes.average() else 0.0
                  val maxTime = audioProcessingTimes.maxOrNull() ?: 0
                  
                  // 清空统计数据
                  audioChunkSizes.clear()
                  audioProcessingTimes.clear()
                }
                
                // 建议音频流每40ms发送1280字节，发送过快可能导致引擎出错
                delay(40)
              }
              is AudioData.EndOfStream -> {
                // 收到结束标记，停止识别
                if (isRunning) {
                  rtasr?.stop()
                }
              }
            }
          }
        } catch (e: Exception) {
          if (e is CancellationException) throw e
        }
      }
      
      // 等待识别结束
      awaitClose {
        audioProcessingJob.cancel()
        stopRecognition()
        currentProducerScope = null
      }
    } catch (e: Exception) {
      throw e
    }
  }.catch { e ->
    throw e
  }.flowOn(Dispatchers.IO)

  
  /**
   * 释放资源
   */
  fun release() {
    stopRecognition()
    currentProducerScope = null
    
    if (isSDKInitialized) {
      try {
        SparkChain.getInst().unInit()
        isSDKInitialized = false
      } catch (e: Exception) {
        Log.e(TAG, "释放SDK资源异常: ${e.message}", e)
      }
    }
  }
  
  /**
   * 设置应用后台状态
   */
  fun setBackgroundState(isInBackground: Boolean) {
    val previousState = isAppInBackground
    isAppInBackground = isInBackground
  }
} 
