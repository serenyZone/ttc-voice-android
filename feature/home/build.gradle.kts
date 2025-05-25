plugins {
  id("skydoves.pokedex.android.feature")
  id("skydoves.pokedex.android.hilt")
  kotlin("kapt")
}

android {
  namespace = "com.skydoves.pokedex.compose.feature.home"
  
  // 添加测试配置
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }
  
  // 添加lint配置来禁用有问题的检查器
  lint {
    disable += "StateFlowValueCalledInComposition"
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
  
  // 添加Activity Compose依赖
  implementation(libs.androidx.activity.compose)
  
  // 添加Room数据库依赖
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  kapt(libs.androidx.room.compiler)
  
  // 测试依赖
  testImplementation("junit:junit:4.13.2")
  testImplementation("io.mockk:mockk:1.13.5")
  testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.0")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.0")

  // 其他原有依赖...
}