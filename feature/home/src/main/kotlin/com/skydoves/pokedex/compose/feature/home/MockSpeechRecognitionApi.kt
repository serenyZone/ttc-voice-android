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

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 语音识别API的模拟实现
 * 实际项目中应替换为真实的API实现
 */
@Singleton
class MockSpeechRecognitionApi @Inject constructor() : SpeechRecognitionApi {
  
  override fun streamAudioForRecognition(audioBytes: Flow<AudioData>): Flow<RecognitionResult> = flow {
    try {
      // 使用coroutineScope创建一个协程作用域
      kotlinx.coroutines.coroutineScope {
        // 在这个作用域内启动协程来处理音频数据
        launch {
          try {
            audioBytes.collect { audioData ->
              when (audioData) {
                is AudioData.AudioChunk -> {
                  // 模拟处理音频数据
                  // 这里不做实际处理，只是为了消费流
                }
                is AudioData.EndOfStream -> {
                  // 收到结束标记，可以在这里做一些清理工作
                  Log.d("MockSpeechRecognitionApi", "收到音频流结束标记")
                }
              }
            }
          } catch (e: Exception) {
            Log.e("MockSpeechRecognitionApi", "处理音频数据出错: ${e.message}", e)
          }
        }
      }
      
      // 模拟角色1的对话 - 每组句子用不同的sentenceId
      val conversations = listOf(
        // 第一轮对话
        listOf(
          SpeakerSentence(1, "你好，请问我能帮你什么忙吗？", 1),
          SpeakerSentence(1, "你好，请问我能帮你什么忙吗？我是客服小美。", 1),
          SpeakerSentence(1, "你好，请问我能帮你什么忙吗？我是客服小美。很高兴为您服务！", 1)
        ),
        // 第二轮对话
        listOf(
          SpeakerSentence(2, "我想查询我的订单。", 2),
          SpeakerSentence(2, "我想查询我的订单。订单号是BJ12345678。", 2),
          SpeakerSentence(2, "我想查询我的订单。订单号是BJ12345678。能帮我看一下发货状态吗？", 2)
        ),
        // 第三轮对话
        listOf(
          SpeakerSentence(1, "让我来查一下。", 3),
          SpeakerSentence(1, "让我来查一下。您的订单已经在配送中了。", 3),
          SpeakerSentence(1, "让我来查一下。您的订单已经在配送中了。预计明天送达。", 3)
        ),
        // 第四轮对话
        listOf(
          SpeakerSentence(2, "好的，谢谢。", 4),
          SpeakerSentence(2, "好的，谢谢。还有其他问题吗？", 4), // 这里修正一下，客户不会问"还有其他问题吗"
          SpeakerSentence(2, "好的，谢谢。请问你们的退换货政策是怎样的？", 4)
        ),
        // 第五轮对话
        listOf(
          SpeakerSentence(1, "我们的退换货政策是这样的：", 5),
          SpeakerSentence(1, "我们的退换货政策是这样的：商品签收后7天内，", 5),
          SpeakerSentence(1, "我们的退换货政策是这样的：商品签收后7天内，如果商品质量有问题，可以申请退换货。", 5)
        ),
        // 第六轮对话
        listOf(
          SpeakerSentence(2, "明白了，非常感谢您的解答。", 6),
          SpeakerSentence(2, "明白了，非常感谢您的解答。再见！", 6)
        ),
        // 第七轮对话
        listOf(
          SpeakerSentence(1, "不客气，很高兴能帮到您。", 7),
          SpeakerSentence(1, "不客气，很高兴能帮到您。再见！祝您生活愉快！", 7)
        )
      )
      
      // 基础时间戳，每轮对话增加1000ms
      var baseTimestamp = System.currentTimeMillis()
      
      // 发送所有对话
      for (conversation in conversations) {
        // 为每轮对话发送增量识别结果
        for (sentence in conversation) {
          emit(RecognitionResult(
            speakerId = sentence.speakerId,
            text = sentence.text,
            sentenceId = sentence.sentenceId,
            beginTime = baseTimestamp
          ))
          delay(700) // 模拟识别延迟
          // 检查协程是否被取消
          currentCoroutineContext().ensureActive()
        }
        baseTimestamp += 1000 // 下一轮对话的时间戳增加1000ms
      }
    } catch (e: CancellationException) {
      // 当协程被取消时，我们可以在这里进行清理工作
      // 只需要重新抛出异常，让调用者知道流已经被取消
      throw e
    }
  }.onCompletion { cause ->
    // 当流完成或取消时会执行这个代码块
    if (cause != null) {
      // 如果是因为异常而完成，可以在这里进行日志记录
      android.util.Log.d("MockSpeechRecognitionApi", "流被取消: ${cause.message}")
    } else {
      // 正常完成的情况
      android.util.Log.d("MockSpeechRecognitionApi", "识别流正常完成")
    }
  }
  
  // 辅助数据类，用于表示一句话的信息
  private data class SpeakerSentence(
    val speakerId: Int, // 说话者ID
    val text: String,   // 文本内容
    val sentenceId: Int // 句子ID
  )
} 