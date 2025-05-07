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

import androidx.compose.ui.graphics.Color

/**
 * 提供发言者消息背景色的工具类
 */
object SpeakerColors {
  /**
   * 10种不同的背景色，用于区分不同角色的发言
   */
  private val colorList = listOf(
    Color(0xFFE3F2FD), // 淡蓝色
    Color(0xFFFFF9C4), // 淡黄色
    Color(0xFFE8F5E9), // 淡绿色
    Color(0xFFFCE4EC), // 淡粉色
    Color(0xFFEDE7F6), // 淡紫色
    Color(0xFFF3E5F5), // 淡紫红色
    Color(0xFFE0F7FA), // 淡青色
    Color(0xFFFFF3E0), // 淡橙色
    Color(0xFFEFEBE9), // 淡棕色
    Color(0xFFF1F8E9)  // 淡黄绿色
  )
  
  /**
   * 根据发言者ID获取对应的背景色
   * @param speakerId 发言者ID
   * @return 对应的背景色
   */
  fun getColorForSpeaker(speakerId: Int): Color {
    // 确保ID为正数，然后对颜色列表长度取模，实现循环
    val positiveId = if (speakerId <= 0) 1 else speakerId
    val colorIndex = (positiveId - 1) % colorList.size
    return colorList[colorIndex]
  }
} 