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
 * 语音识别结果数据类
 * @param speakerId 说话者ID
 * @param text 识别的文本内容
 * @param sentenceId 句子ID，用于区分不同的句子
 * @param beginTime 句子开始时间（毫秒）
 */
data class RecognitionResult(
  val speakerId: Int,
  val text: String,
  val sentenceId: Int = 0,
  val beginTime: Long = 0
) 