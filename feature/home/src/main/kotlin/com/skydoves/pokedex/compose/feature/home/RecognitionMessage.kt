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

/**
 * 表示UI中显示的识别消息（语音或AI）
 * @param speakerId 说话者ID (e.g., 0, 1, 2... for speakers, -1 for User, -2 for AI)
 * @param text 识别或回复的文本内容
 * @param sentenceId 句子ID，用于区分不同的句子
 * @param beginTime 句子开始时间（毫秒）
 * @param isStreaming 是否是流式AI回复（用于UI判断是否持续更新）
 */
data class RecognitionMessage(
  val speakerId: Int,
  var text: String, // Made var to allow updating streaming AI responses
  val sentenceId: Int = 0,
  val beginTime: Long = 0,
  var isStreaming: Boolean = false // Flag for AI streaming state
)

/**
 * 表示聚合后的识别消息，用于UI展示
 * @param speakerId 说话者ID
 * @param text 聚合后的文本内容
 * @param sentenceIds 包含的所有句子ID列表
 * @param beginTime 第一句话的开始时间（毫秒）
 * @param messageCount 包含的原始消息数量
 * @param isStreaming 是否是流式AI回复
 */
data class AggregatedMessage(
  val speakerId: Int,
  val text: String, // Keep as val, merge will create new text
  val sentenceIds: List<Int>,
  val beginTime: Long = 0,
  val messageCount: Int = 1, // Keep as val
  val isStreaming: Boolean = false // Keep as val
) {
  companion object {
    /**
     * 将单个RecognitionMessage转换为AggregatedMessage
     */
    fun fromRecognitionMessage(message: RecognitionMessage): AggregatedMessage {
      return AggregatedMessage(
        speakerId = message.speakerId,
        text = message.text,
        sentenceIds = listOf(message.sentenceId),
        beginTime = message.beginTime,
        isStreaming = message.isStreaming
      )
    }
    
    /**
     * 聚合（合并）一个AggregatedMessage和一个新的RecognitionMessage，返回一个新的AggregatedMessage
     */
    fun merge(aggregated: AggregatedMessage, newMessage: RecognitionMessage): AggregatedMessage {
      // 合并文本，如果前一条不是流式回复，则加空格
      val separator = if (!aggregated.isStreaming) " " else ""
      val newText = "${aggregated.text}$separator${newMessage.text}"
      
      // 返回一个新的 AggregatedMessage 实例
      return AggregatedMessage(
        speakerId = aggregated.speakerId, // 角色ID保持不变
        text = newText,
        sentenceIds = aggregated.sentenceIds + newMessage.sentenceId, // 创建新的ID列表
        beginTime = aggregated.beginTime, // 开始时间保持第一次的时间
        messageCount = aggregated.messageCount + 1, // 增加消息计数
        isStreaming = newMessage.isStreaming // 流式状态更新为新消息的状态
      )
    }
  }
} 