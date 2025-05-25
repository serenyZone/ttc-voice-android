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
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 音频录制接口
 */
interface AudioRecorder {
  /**
   * 开始录音并返回音频流
   * @return 音频数据流，包含音频数据和结束标记
   */
  fun startRecording(): Flow<AudioData>
  
  /**
   * 停止录音
   */
  suspend fun stopRecording()
  
  /**
   * 检查是否正在录音
   * @return 是否正在录音
   */
  fun isRecording(): Boolean
}

/**
 * 音频录制的实现类
 * 实际项目中应该根据需求进行优化
 */
@Singleton
class AudioRecorderImpl @Inject constructor(
  @ApplicationContext private val context: Context
) : AudioRecorder {
  
  private var audioRecord: AudioRecord? = null
  private var isRecordingActive = false
  
  // 音频配置参数
  private val sampleRate = 16000 // 44100Hz也是一个常用值
  private val channelConfig = AudioFormat.CHANNEL_IN_MONO
  private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
  
  override fun startRecording(): Flow<AudioData> = flow {
    try {
      // 权限检查，增加详细日志
      val hasRecordPermission = PermissionHelper.hasRecordAudioPermission(context)
      val hasStoragePermission = PermissionHelper.hasStoragePermissions(context)
      
      Log.d("AudioRecorder", "开始录音 - 录音权限: $hasRecordPermission, 存储权限: $hasStoragePermission")
      
      if (!hasRecordPermission) {
        Log.e("AudioRecorder", "没有录音权限 (RECORD_AUDIO)")
        throw SecurityException("没有录音权限 (RECORD_AUDIO)")
      }
      
      // 对于讯飞SDK，还需要检查存储权限
      if (!hasStoragePermission) {
        Log.w("AudioRecorder", "没有完整的存储权限，讯飞SDK可能无法正常工作")
      }
      
      val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
      Log.d("AudioRecorder", "AudioRecord缓冲区大小: $bufferSize")
      
      if (bufferSize == AudioRecord.ERROR_BAD_VALUE) {
        Log.e("AudioRecorder", "无效的录音参数：sampleRate=$sampleRate, channelConfig=$channelConfig, audioFormat=$audioFormat")
        throw IllegalStateException("无效的录音参数")
      }
      
      if (bufferSize == AudioRecord.ERROR) {
        Log.e("AudioRecorder", "无法获取推荐的录音缓冲区大小")
        throw IllegalStateException("无法获取推荐的录音缓冲区大小")
      }
      
      Log.d("AudioRecorder", "创建AudioRecord实例...")
      
      try {
        audioRecord = AudioRecord(
          MediaRecorder.AudioSource.MIC,
          sampleRate,
          channelConfig,
          audioFormat,
          bufferSize,

        )
      } catch (e: Exception) {
        Log.e("AudioRecorder", "创建AudioRecord实例失败: ${e.message}", e)
        throw e
      }
      
      val state = audioRecord?.state ?: -1
      Log.d("AudioRecorder", "AudioRecord状态: $state (${if (state == AudioRecord.STATE_INITIALIZED) "已初始化" else "未初始化"})")
      
      if (state != AudioRecord.STATE_INITIALIZED) {
        Log.e("AudioRecorder", "AudioRecord初始化失败：状态=$state")
        throw IllegalStateException("AudioRecord初始化失败：状态=$state")
      }
      
      val buffer = ByteArray(bufferSize)
      
      try {
        Log.d("AudioRecorder", "开始录音...")
        audioRecord?.startRecording()
        isRecordingActive = true
        
        try {
          Log.d("AudioRecorder", "开始读取音频数据...")
          while (isRecordingActive) {
            val bytesRead = audioRecord?.read(buffer, 0, bufferSize) ?: 0
            
            if (bytesRead > 0) {
              // 复制缓冲区，以避免多线程访问问题
              val audioChunk = buffer.copyOfRange(0, bytesRead)
              emit(AudioData.AudioChunk(audioChunk))
            } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
              Log.e("AudioRecorder", "读取音频数据失败：无效操作")
              break
            } else if (bytesRead == AudioRecord.ERROR_BAD_VALUE) {
              Log.e("AudioRecorder", "读取音频数据失败：无效参数")
              break
            } else if (bytesRead == AudioRecord.ERROR_DEAD_OBJECT) {
              Log.e("AudioRecorder", "读取音频数据失败：AudioRecord对象已失效")
              break
            } else if (bytesRead <= 0) {
              Log.w("AudioRecorder", "读取音频数据：无数据，bytesRead=$bytesRead")
            }
          }
        } catch (e: Exception) {
          Log.e("AudioRecorder", "读取音频数据过程中出错: ${e.message}", e)
          throw e
        } finally {
          Log.d("AudioRecorder", "停止录音并释放资源")
          try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
          } catch (e: Exception) {
            Log.e("AudioRecorder", "停止录音或释放资源失败: ${e.message}", e)
          }
        }
        
        Log.d("AudioRecorder", "发送音频流结束标记")
        emit(AudioData.EndOfStream)
      } catch (e: Exception) {
        Log.e("AudioRecorder", "开始录音失败: ${e.message}", e)
        throw e
      }
    } catch (e: Exception) {
      Log.e("AudioRecorder", "Error recording audio: ${e.message}")
      if (e is CancellationException) throw e
      throw e
    } finally {
      stopRecording()
    }
  }.flowOn(Dispatchers.IO)
  
  override suspend fun stopRecording() {
    Log.d("AudioRecorder", "停止录音请求 - 当前录音状态: $isRecordingActive")
    isRecordingActive = false
    withContext(Dispatchers.IO) {
      try {
        if (audioRecord != null) {
          Log.d("AudioRecorder", "停止AudioRecord...")
          audioRecord?.stop()
          Log.d("AudioRecorder", "释放AudioRecord资源...")
          audioRecord?.release()
          audioRecord = null
          Log.d("AudioRecorder", "AudioRecord资源已成功释放")
        } else {
          Log.d("AudioRecorder", "AudioRecord已经是null，无需停止和释放")
        }
      } catch (e: IllegalStateException) {
        Log.e("AudioRecorder", "停止录音失败：AudioRecord可能处于非法状态: ${e.message}", e)
      } catch (e: Exception) {
        Log.e("AudioRecorder", "停止录音过程中发生错误: ${e.message}", e)
      } finally {
        Log.d("AudioRecorder", "录音已完全停止")
      }
    }
  }
  
  override fun isRecording(): Boolean = isRecordingActive
}

/**
 * 用于录制系统上行通道(通话发出)的录音器
 */
@Singleton
class SystemUplinkAudioRecorder @Inject constructor(
  @ApplicationContext private val context: Context
) : AudioRecorder {
  
  private var audioRecord: AudioRecord? = null
  private var isRecordingActive = false
  
  // 音频配置参数
  private val sampleRate = 16000
  private val channelConfig = AudioFormat.CHANNEL_IN_MONO
  private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
  
  override fun startRecording(): Flow<AudioData> = flow {
    try {
      // 权限检查，增加详细日志
      val hasRecordPermission = PermissionHelper.hasRecordAudioPermission(context)
      val hasStoragePermission = PermissionHelper.hasStoragePermissions(context)
      
      Log.d("UplinkAudioRecorder", "开始录音上行通道 - 录音权限: $hasRecordPermission, 存储权限: $hasStoragePermission")
      
      if (!hasRecordPermission) {
        Log.e("UplinkAudioRecorder", "没有录音权限 (RECORD_AUDIO)")
        throw SecurityException("没有录音权限 (RECORD_AUDIO)")
      }
      
      val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
      Log.d("UplinkAudioRecorder", "AudioRecord缓冲区大小: $bufferSize")
      
      if (bufferSize == AudioRecord.ERROR_BAD_VALUE) {
        Log.e("UplinkAudioRecorder", "无效的录音参数：sampleRate=$sampleRate, channelConfig=$channelConfig, audioFormat=$audioFormat")
        throw IllegalStateException("无效的录音参数")
      }
      
      if (bufferSize == AudioRecord.ERROR) {
        Log.e("UplinkAudioRecorder", "无法获取推荐的录音缓冲区大小")
        throw IllegalStateException("无法获取推荐的录音缓冲区大小")
      }
      
      Log.d("UplinkAudioRecorder", "创建上行通道AudioRecord实例...")
      
      try {
        audioRecord = AudioRecord(
          MediaRecorder.AudioSource.VOICE_UPLINK, // 使用VOICE_UPLINK作为录音源
          sampleRate,
          channelConfig,
          audioFormat,
          bufferSize,
        )
      } catch (e: Exception) {
        Log.e("UplinkAudioRecorder", "创建上行通道AudioRecord实例失败: ${e.message}", e)
        throw e
      }
      
      val state = audioRecord?.state ?: -1
      Log.d("UplinkAudioRecorder", "上行通道AudioRecord状态: $state (${if (state == AudioRecord.STATE_INITIALIZED) "已初始化" else "未初始化"})")
      
      if (state != AudioRecord.STATE_INITIALIZED) {
        Log.e("UplinkAudioRecorder", "上行通道AudioRecord初始化失败：状态=$state")
        throw IllegalStateException("上行通道AudioRecord初始化失败：状态=$state")
      }
      
      val buffer = ByteArray(bufferSize)
      
      try {
        Log.d("UplinkAudioRecorder", "开始录制上行通道音频...")
        audioRecord?.startRecording()
        isRecordingActive = true
        
        try {
          Log.d("UplinkAudioRecorder", "开始读取上行通道音频数据...")
          while (isRecordingActive) {
            val bytesRead = audioRecord?.read(buffer, 0, bufferSize) ?: 0
            
            if (bytesRead > 0) {
              // 复制缓冲区，以避免多线程访问问题
              val audioChunk = buffer.copyOfRange(0, bytesRead)
              emit(AudioData.AudioChunk(audioChunk))
            } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
              Log.e("UplinkAudioRecorder", "读取上行通道音频数据失败：无效操作")
              break
            } else if (bytesRead == AudioRecord.ERROR_BAD_VALUE) {
              Log.e("UplinkAudioRecorder", "读取上行通道音频数据失败：无效参数")
              break
            } else if (bytesRead == AudioRecord.ERROR_DEAD_OBJECT) {
              Log.e("UplinkAudioRecorder", "读取上行通道音频数据失败：AudioRecord对象已失效")
              break
            } else if (bytesRead <= 0) {
              Log.w("UplinkAudioRecorder", "读取上行通道音频数据：无数据，bytesRead=$bytesRead")
            }
          }
        } catch (e: Exception) {
          Log.e("UplinkAudioRecorder", "读取上行通道音频数据过程中出错: ${e.message}", e)
          throw e
        } finally {
          Log.d("UplinkAudioRecorder", "停止上行通道录音并释放资源")
          try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
          } catch (e: Exception) {
            Log.e("UplinkAudioRecorder", "停止上行通道录音或释放资源失败: ${e.message}", e)
          }
        }
        
        Log.d("UplinkAudioRecorder", "发送上行通道音频流结束标记")
        emit(AudioData.EndOfStream)
      } catch (e: Exception) {
        Log.e("UplinkAudioRecorder", "开始上行通道录音失败: ${e.message}", e)
        throw e
      }
    } catch (e: Exception) {
      Log.e("UplinkAudioRecorder", "Error recording uplink audio: ${e.message}")
      if (e is CancellationException) throw e
      throw e
    } finally {
      stopRecording()
    }
  }.flowOn(Dispatchers.IO)
  
  override suspend fun stopRecording() {
    Log.d("UplinkAudioRecorder", "停止上行通道录音请求 - 当前录音状态: $isRecordingActive")
    isRecordingActive = false
    withContext(Dispatchers.IO) {
      try {
        if (audioRecord != null) {
          Log.d("UplinkAudioRecorder", "停止上行通道AudioRecord...")
          audioRecord?.stop()
          Log.d("UplinkAudioRecorder", "释放上行通道AudioRecord资源...")
          audioRecord?.release()
          audioRecord = null
          Log.d("UplinkAudioRecorder", "上行通道AudioRecord资源已成功释放")
        } else {
          Log.d("UplinkAudioRecorder", "上行通道AudioRecord已经是null，无需停止和释放")
        }
      } catch (e: IllegalStateException) {
        Log.e("UplinkAudioRecorder", "停止上行通道录音失败：AudioRecord可能处于非法状态: ${e.message}", e)
      } catch (e: Exception) {
        Log.e("UplinkAudioRecorder", "停止上行通道录音过程中发生错误: ${e.message}", e)
      } finally {
        Log.d("UplinkAudioRecorder", "上行通道录音已完全停止")
      }
    }
  }
  
  override fun isRecording(): Boolean = isRecordingActive
}

/**
 * 用于录制系统下行通道(通话接收)的录音器
 */
@Singleton
class SystemDownlinkAudioRecorder @Inject constructor(
  @ApplicationContext private val context: Context
) : AudioRecorder {
  
  private var audioRecord: AudioRecord? = null
  private var isRecordingActive = false
  
  // 音频配置参数
  private val sampleRate = 16000
  private val channelConfig = AudioFormat.CHANNEL_IN_MONO
  private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
  
  override fun startRecording(): Flow<AudioData> = flow {
    try {
      // 权限检查，增加详细日志
      val hasRecordPermission = PermissionHelper.hasRecordAudioPermission(context)
      val hasStoragePermission = PermissionHelper.hasStoragePermissions(context)
      
      Log.d("DownlinkAudioRecorder", "开始录音下行通道 - 录音权限: $hasRecordPermission, 存储权限: $hasStoragePermission")
      
      if (!hasRecordPermission) {
        Log.e("DownlinkAudioRecorder", "没有录音权限 (RECORD_AUDIO)")
        throw SecurityException("没有录音权限 (RECORD_AUDIO)")
      }
      
      val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
      Log.d("DownlinkAudioRecorder", "AudioRecord缓冲区大小: $bufferSize")
      
      if (bufferSize == AudioRecord.ERROR_BAD_VALUE) {
        Log.e("DownlinkAudioRecorder", "无效的录音参数：sampleRate=$sampleRate, channelConfig=$channelConfig, audioFormat=$audioFormat")
        throw IllegalStateException("无效的录音参数")
      }
      
      if (bufferSize == AudioRecord.ERROR) {
        Log.e("DownlinkAudioRecorder", "无法获取推荐的录音缓冲区大小")
        throw IllegalStateException("无法获取推荐的录音缓冲区大小")
      }
      
      Log.d("DownlinkAudioRecorder", "创建下行通道AudioRecord实例...")
      
      try {
        audioRecord = AudioRecord(
          MediaRecorder.AudioSource.VOICE_DOWNLINK, // 使用VOICE_DOWNLINK作为录音源
          sampleRate,
          channelConfig,
          audioFormat,
          bufferSize,
        )
      } catch (e: Exception) {
        Log.e("DownlinkAudioRecorder", "创建下行通道AudioRecord实例失败: ${e.message}", e)
        throw e
      }
      
      val state = audioRecord?.state ?: -1
      Log.d("DownlinkAudioRecorder", "下行通道AudioRecord状态: $state (${if (state == AudioRecord.STATE_INITIALIZED) "已初始化" else "未初始化"})")
      
      if (state != AudioRecord.STATE_INITIALIZED) {
        Log.e("DownlinkAudioRecorder", "下行通道AudioRecord初始化失败：状态=$state")
        throw IllegalStateException("下行通道AudioRecord初始化失败：状态=$state")
      }
      
      val buffer = ByteArray(bufferSize)
      
      try {
        Log.d("DownlinkAudioRecorder", "开始录制下行通道音频...")
        audioRecord?.startRecording()
        isRecordingActive = true
        
        try {
          Log.d("DownlinkAudioRecorder", "开始读取下行通道音频数据...")
          while (isRecordingActive) {
            val bytesRead = audioRecord?.read(buffer, 0, bufferSize) ?: 0
            
            if (bytesRead > 0) {
              // 复制缓冲区，以避免多线程访问问题
              val audioChunk = buffer.copyOfRange(0, bytesRead)
              emit(AudioData.AudioChunk(audioChunk))
            } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
              Log.e("DownlinkAudioRecorder", "读取下行通道音频数据失败：无效操作")
              break
            } else if (bytesRead == AudioRecord.ERROR_BAD_VALUE) {
              Log.e("DownlinkAudioRecorder", "读取下行通道音频数据失败：无效参数")
              break
            } else if (bytesRead == AudioRecord.ERROR_DEAD_OBJECT) {
              Log.e("DownlinkAudioRecorder", "读取下行通道音频数据失败：AudioRecord对象已失效")
              break
            } else if (bytesRead <= 0) {
              Log.w("DownlinkAudioRecorder", "读取下行通道音频数据：无数据，bytesRead=$bytesRead")
            }
          }
        } catch (e: Exception) {
          Log.e("DownlinkAudioRecorder", "读取下行通道音频数据过程中出错: ${e.message}", e)
          throw e
        } finally {
          Log.d("DownlinkAudioRecorder", "停止下行通道录音并释放资源")
          try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
          } catch (e: Exception) {
            Log.e("DownlinkAudioRecorder", "停止下行通道录音或释放资源失败: ${e.message}", e)
          }
        }
        
        Log.d("DownlinkAudioRecorder", "发送下行通道音频流结束标记")
        emit(AudioData.EndOfStream)
      } catch (e: Exception) {
        Log.e("DownlinkAudioRecorder", "开始下行通道录音失败: ${e.message}", e)
        throw e
      }
    } catch (e: Exception) {
      Log.e("DownlinkAudioRecorder", "Error recording downlink audio: ${e.message}")
      if (e is CancellationException) throw e
      throw e
    } finally {
      stopRecording()
    }
  }.flowOn(Dispatchers.IO)
  
  override suspend fun stopRecording() {
    Log.d("DownlinkAudioRecorder", "停止下行通道录音请求 - 当前录音状态: $isRecordingActive")
    isRecordingActive = false
    withContext(Dispatchers.IO) {
      try {
        if (audioRecord != null) {
          Log.d("DownlinkAudioRecorder", "停止下行通道AudioRecord...")
          audioRecord?.stop()
          Log.d("DownlinkAudioRecorder", "释放下行通道AudioRecord资源...")
          audioRecord?.release()
          audioRecord = null
          Log.d("DownlinkAudioRecorder", "下行通道AudioRecord资源已成功释放")
        } else {
          Log.d("DownlinkAudioRecorder", "下行通道AudioRecord已经是null，无需停止和释放")
        }
      } catch (e: IllegalStateException) {
        Log.e("DownlinkAudioRecorder", "停止下行通道录音失败：AudioRecord可能处于非法状态: ${e.message}", e)
      } catch (e: Exception) {
        Log.e("DownlinkAudioRecorder", "停止下行通道录音过程中发生错误: ${e.message}", e)
      } finally {
        Log.d("DownlinkAudioRecorder", "下行通道录音已完全停止")
      }
    }
  }
  
  override fun isRecording(): Boolean = isRecordingActive
}