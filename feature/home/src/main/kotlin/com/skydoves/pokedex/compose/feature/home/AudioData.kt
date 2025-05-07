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

import java.util.Arrays

/**
 * 封装音频数据的密封类，用于在Flow流中传递音频数据和结束标记
 */
sealed class AudioData {
  /**
   * 普通音频数据块
   * @param bytes 音频字节数组
   */
  data class AudioChunk(val bytes: ByteArray) : AudioData() {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false
      
      other as AudioChunk
      
      return Arrays.equals(bytes, other.bytes)
    }
    
    override fun hashCode(): Int {
      return Arrays.hashCode(bytes)
    }
  }
  
  /**
   * 音频流结束标记
   */
  object EndOfStream : AudioData()
} 