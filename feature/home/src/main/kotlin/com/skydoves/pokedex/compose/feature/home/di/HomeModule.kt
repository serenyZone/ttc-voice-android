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

package com.skydoves.pokedex.compose.feature.home.di

import com.skydoves.pokedex.compose.feature.home.AudioRecorder
import com.skydoves.pokedex.compose.feature.home.AudioRecorderImpl
import com.skydoves.pokedex.compose.feature.home.SpeechRecognitionApi
import com.skydoves.pokedex.compose.feature.home.XfyunSpeechRecognitionApi
import com.skydoves.pokedex.compose.feature.home.SystemUplinkAudioRecorder
import com.skydoves.pokedex.compose.feature.home.SystemDownlinkAudioRecorder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeModule {
  
  @Binds
  @Singleton
  @Named("mic")
  abstract fun bindMicAudioRecorder(
    audioRecorder: AudioRecorderImpl
  ): AudioRecorder
  
  @Binds
  @Singleton
  @Named("uplink")
  abstract fun bindUplinkAudioRecorder(
    audioRecorder: SystemUplinkAudioRecorder
  ): AudioRecorder
  
  @Binds
  @Singleton
  @Named("downlink")
  abstract fun bindDownlinkAudioRecorder(
    audioRecorder: SystemDownlinkAudioRecorder
  ): AudioRecorder
  
  @Binds
  @Singleton
  abstract fun bindSpeechRecognitionApi(
    // 在实际环境中使用讯飞的实现，但由于依赖可能未正确配置，现在继续使用Mock实现
     xfyunSpeechRecognitionApi: XfyunSpeechRecognitionApi
//    mockSpeechRecognitionApi: MockSpeechRecognitionApi
  ): SpeechRecognitionApi
} 