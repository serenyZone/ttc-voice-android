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

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/**
 * 权限检查和请求助手类
 */
object PermissionHelper {

  private const val TAG = "PermissionHelper"
  
  /**
   * 检查是否有录音权限
   */
  fun hasRecordAudioPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
  }
  
  /**
   * 检查是否有存储读写权限
   */
  fun hasStoragePermissions(context: Context): Boolean {
    // 对于Android 10以下版本
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_EXTERNAL_STORAGE
      ) == PackageManager.PERMISSION_GRANTED &&
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
      ) == PackageManager.PERMISSION_GRANTED
    }
    
    // 对于Android 10及以上版本
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
      return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_EXTERNAL_STORAGE
      ) == PackageManager.PERMISSION_GRANTED
    }
    
    // 对于Android 11及以上版本，检查是否有MANAGE_EXTERNAL_STORAGE权限
    return Environment.isExternalStorageManager()
  }
  
  /**
   * 检查是否有所有必要的权限
   */
  fun hasAllRequiredPermissions(context: Context): Boolean {
    return hasRecordAudioPermission(context) && hasStoragePermissions(context)
  }
  
  /**
   * 获取需要请求的权限列表
   */
  fun getRequiredPermissions(): Array<String> {
    val basePermissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
    
    // 添加存储权限
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      basePermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
      basePermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
      basePermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    
    return basePermissions.toTypedArray()
  }
  
  /**
   * 打开应用设置页面，用于用户手动授予权限
   */
  fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    val uri = Uri.fromParts("package", context.packageName, null)
    intent.data = uri
    context.startActivity(intent)
  }
  
  /**
   * 打开MANAGE_EXTERNAL_STORAGE权限设置页面
   */
  fun openManageAllFilesPermissionSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      try {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        intent.data = Uri.parse("package:${context.packageName}")
        context.startActivity(intent)
      } catch (e: Exception) {
        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        context.startActivity(intent)
      }
    }
  }
}

/**
 * Composable函数，用于请求录音权限
 */
@Composable
fun RequestPermissions(
  onPermissionsGranted: () -> Unit,
  onPermissionsDenied: () -> Unit
) {
  var allPermissionsGranted by remember { mutableStateOf(false) }
  val context = androidx.compose.ui.platform.LocalContext.current
  
  // 检查已有权限
  LaunchedEffect(Unit) {
    allPermissionsGranted = PermissionHelper.hasAllRequiredPermissions(context)
    if (allPermissionsGranted) {
      onPermissionsGranted()
    }
  }
  
  // 请求基本权限
  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { permissionsMap ->
    val allGranted = permissionsMap.values.all { it }
    
    if (allGranted && !PermissionHelper.hasAllRequiredPermissions(context)) {
      // 如果是Android 11+，还需要请求MANAGE_EXTERNAL_STORAGE权限
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
        PermissionHelper.openManageAllFilesPermissionSettings(context)
      }
    } else if (allGranted) {
      allPermissionsGranted = true
      onPermissionsGranted()
    } else {
      onPermissionsDenied()
    }
  }
  
  // 请求权限
  LaunchedEffect(Unit) {
    if (!allPermissionsGranted) {
      permissionLauncher.launch(PermissionHelper.getRequiredPermissions())
    }
  }
  
  DisposableEffect(Unit) {
    onDispose { }
  }
} 