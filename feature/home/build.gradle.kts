plugins {
  id("skydoves.pokedex.android.feature")
  id("skydoves.pokedex.android.hilt")
}

android {
  namespace = "com.skydoves.pokedex.compose.feature.home"
  
  // 添加测试配置
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }
}

dependencies {
  // 添加讯飞实时语音转写SDK
  implementation(libs.websdk.java.speech) // 讯飞语音识别SDK
  implementation(libs.fastjson) // JSON解析库
  
  // 添加OkHttp3依赖
  implementation(libs.okhttp)
  implementation(files("libs/SparkChain.aar"))

  // 添加EasyFloat悬浮窗库
  implementation("com.github.princekin-f:EasyFloat:2.0.4")

  implementation("com.google.accompanist:accompanist-pager:0.25.1")
  // 测试依赖
  testImplementation("junit:junit:4.13.2")
  testImplementation("io.mockk:mockk:1.13.5")
  testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.0")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.0")

  // 其他原有依赖...
}