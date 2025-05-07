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

package com.skydoves.pokedex.compose

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import android.util.Log
import com.skydoves.pokedex.compose.feature.home.VoiceRecognitionViewModel

/**
 * 应用程序入口类
 */
@HiltAndroidApp
class PokedexApp : Application() {
    private val TAG = "PokedexApp"
    
    // 保存ViewModel实例的引用
    @Volatile
    private var voiceViewModel: VoiceRecognitionViewModel? = null
    
    /**
     * 注册ViewModel实例，通常由ViewModel本身在初始化时调用
     */
    fun registerVoiceViewModel(viewModel: VoiceRecognitionViewModel) {
        Log.d(TAG, "注册VoiceViewModel: $viewModel")
        voiceViewModel = viewModel
    }
    
    /**
     * 获取ViewModel实例
     */
    fun getVoiceViewModel(): VoiceRecognitionViewModel? {
        return voiceViewModel
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "PokedexApp 创建")
    }
}
