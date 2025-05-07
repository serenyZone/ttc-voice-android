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
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Before
import org.junit.Test
import android.util.Log
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class XfyunSpeechRecognitionApiTest {
  
  private lateinit var xfyunSpeechRecognitionApi: XfyunSpeechRecognitionApi
  private lateinit var mockContext: Context
  
  @Before
  fun setup() {
    // 模拟Android环境
    mockContext = mockk<Context>()
    
    // 模拟Log类
    mockkStatic(Log::class)
    every { Log.d(any(), any()) } returns 0
    every { Log.w(any(), any<String>()) } returns 0
    every { Log.e(any(), any(), any()) } returns 0
    
    // 初始化被测试对象
    xfyunSpeechRecognitionApi = XfyunSpeechRecognitionApi(mockContext)
  }
  
  @Test
  fun `test extractRoleIdAndText with valid JSON data`() {
    // 准备测试数据
    val jsonStr = "{\"action\":\"result\",\"code\":\"0\",\"data\":\"{\\\"seg_id\\\":0,\\\"cn\\\":{\\\"st\\\":{\\\"rt\\\":[{\\\"ws\\\":[{\\\"cw\\\":[{\\\"sc\\\":0.00,\\\"w\\\":\\\"金\\\",\\\"wp\\\":\\\"n\\\",\\\"rl\\\":\\\"0\\\",\\\"wb\\\":13,\\\"wc\\\":0.00,\\\"we\\\":26}],\\\"wb\\\":0,\\\"we\\\":0}]}],\\\"bg\\\":\\\"2000\\\",\\\"type\\\":\\\"1\\\",\\\"ed\\\":\\\"0\\\"}},\\\"ls\\\":false}\",\"desc\":\"success\",\"sid\":\"rta06caefd0@dx5bb21b609dbe1aba00\"}"
    
    // 调用私有方法
    val result = xfyunSpeechRecognitionApi.callPrivateFunction("extractRoleIdAndText", jsonStr) as? Quadruple<Int, String, Int, Long>
    
    // 验证结果
    assertNotNull(result, "结果不应为空")
    assertEquals(0, result.first, "角色ID应该为0")
    assertEquals("金", result.second, "文本内容应该为'金'")
    assertEquals(1, result.third, "类型应该为1（中间结果）")
    assertEquals(2000L, result.fourth, "开始时间应该为2000")
  }
  
  @Test
  fun `test extractRoleIdAndText with invalid JSON`() {
    // 准备测试数据 - 无效的JSON
    val jsonStr = "{\"invalid\":\"json\"}"
    
    // 调用私有方法
    val result = xfyunSpeechRecognitionApi.callPrivateFunction("extractRoleIdAndText", jsonStr)
    
    // 验证结果
    assertEquals(null, result, "处理无效JSON时应返回null")
  }
}

/**
 * 扩展函数，用于调用私有方法进行测试
 */
fun XfyunSpeechRecognitionApi.callPrivateFunction(methodName: String, jsonStr: String): Any? {
  val method = this.javaClass.getDeclaredMethod(methodName, String::class.java)
  method.isAccessible = true
  return method.invoke(this, jsonStr)
} 