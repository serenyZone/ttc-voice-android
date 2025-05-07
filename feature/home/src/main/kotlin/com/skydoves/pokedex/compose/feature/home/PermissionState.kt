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
 * 权限状态枚举
 */
enum class PermissionState {
  /** 未知状态，尚未检查 */
  UNKNOWN,
  
  /** 需要权限，但尚未授予 */
  REQUIRED,
  
  /** 已授予所有必要权限 */
  GRANTED,
  
  /** 已拒绝权限请求 */
  DENIED,
  
  /** 永久拒绝权限，需要用户手动修改 */
  PERMANENTLY_DENIED
} 