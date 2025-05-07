import com.skydoves.pokedex.compose.Configuration
import java.io.FileInputStream
import java.util.Properties

plugins {
  id("skydoves.pokedex.android.application")
  id("skydoves.pokedex.android.application.compose")
  id("skydoves.pokedex.android.hilt")
  id("skydoves.pokedex.spotless")
  alias(libs.plugins.kotlin.parcelize)
  alias(libs.plugins.baselineprofile)
}

android {
  namespace = "com.skydoves.pokedex.compose"

  defaultConfig {
    applicationId = "com.skydoves.pokedex.compose"
    versionCode = Configuration.versionCode
    versionName = Configuration.versionName
    testInstrumentationRunner = "com.skydoves.pokedex.compose.AppTestRunner"
  }

  // 添加顶级packaging配置，应用于所有构建类型
  packaging {
    resources {
      excludes += listOf(
        "META-INF/DEPENDENCIES",
        "META-INF/LICENSE",
        "META-INF/LICENSE.txt",
        "META-INF/NOTICE",
        "META-INF/NOTICE.txt",
        "META-INF/INDEX.LIST",
        "META-INF/ASL2.0"
      )
    }
  }

  signingConfigs {
    val properties = Properties()
    val localPropertyFile = project.rootProject.file("local.properties")
    if (localPropertyFile.canRead()) {
      properties.load(FileInputStream("$rootDir/local.properties"))
    }
    create("release") {
      storeFile = file(properties["RELEASE_KEYSTORE_PATH"] ?: "../keystores/pokedex.jks")
      keyAlias = properties["RELEASE_KEY_ALIAS"].toString()
      keyPassword = properties["RELEASE_KEY_PASSWORD"].toString()
      storePassword = properties["RELEASE_KEYSTORE_PASSWORD"].toString()
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles("proguard-rules.pro",)
      signingConfig = signingConfigs.getByName("release")

      kotlinOptions {
        freeCompilerArgs += listOf(
          "-Xno-param-assertions",
          "-Xno-call-assertions",
          "-Xno-receiver-assertions"
        )
      }

      packaging {
        resources {
          excludes += listOf(
            "DebugProbesKt.bin",
            "kotlin-tooling-metadata.json",
            "kotlin/**",
          )
        }
      }
    }
  }

  buildFeatures {
    buildConfig = true
  }

  hilt {
    enableAggregatingTask = true
  }

  kotlin {
    sourceSets.configureEach {
      kotlin.srcDir(layout.buildDirectory.files("generated/ksp/$name/kotlin/"))
    }
    sourceSets.all {
      languageSettings {
        languageVersion = "2.0"
      }
    }
  }

  testOptions.unitTests {
    isIncludeAndroidResources = true
    isReturnDefaultValues = true
  }
}

// 解决 Guava 和 ListenableFuture 依赖冲突
configurations.all {
  resolutionStrategy {
    // 强制使用特定版本的 listenablefuture，避免与 guava 冲突
    force("com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava")
    // 或者可以完全排除 listenablefuture:1.0
    exclude(group = "com.google.guava", module = "listenablefuture")
    
    // 处理Apache HttpComponents依赖冲突
    force("org.apache.httpcomponents:httpclient:4.5.13")
    force("org.apache.httpcomponents:httpcore:4.4.15")
    force("org.apache.httpcomponents:httpmime:4.5.13")
  }
}

dependencies {
  // features
  implementation(projects.feature.home)
  implementation(projects.feature.details)

  // cores
  implementation(projects.core.model)
  implementation(projects.core.designsystem)
  implementation(projects.core.navigation)
  
  // 引入本地JAR文件
  // implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
  // 或者引入单个JAR文件，将YOUR_JAR_FILE替换为实际的JAR文件名
  // implementation(files("libs/YOUR_JAR_FILE.jar"))

  // compose
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.runtime)
  implementation(libs.androidx.compose.foundation)

  // di
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  androidTestImplementation(libs.hilt.testing)
  kspAndroidTest(libs.hilt.compiler)

  // baseline profile
  implementation(libs.profileinstaller)
  baselineProfile(project(":baselineprofile"))

  // unit test
  testImplementation(libs.junit)
  testImplementation(libs.turbine)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.mockito.core)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.kotlinx.coroutines.test)
  androidTestImplementation(libs.truth)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso)
//  androidTestImplementation(libs.android.test.runner)
}