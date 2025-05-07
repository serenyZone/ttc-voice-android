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
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.PagerState
import com.google.accompanist.pager.rememberPagerState
import com.skydoves.pokedex.compose.core.designsystem.theme.PokedexTheme
import kotlinx.coroutines.launch

// 定义Tab选项
enum class HomeTab {
  VOICE_RECOGNITION,
  AI_CHAT
}

@OptIn(ExperimentalPagerApi::class)
@Composable
fun SharedTransitionScope.PokedexHome(
  animatedVisibilityScope: AnimatedVisibilityScope,
  voiceViewModel: VoiceRecognitionViewModel = hiltViewModel(),
  aiViewModel: AIChatViewModel = hiltViewModel()
) {
  // 获取当前上下文
  val context = LocalContext.current
  val isRecording by voiceViewModel.isRecording.collectAsStateWithLifecycle()
  
  // 添加生命周期观察者，在应用切换到后台时显示悬浮窗
  val lifecycleOwner = LocalLifecycleOwner.current
  
  DisposableEffect(lifecycleOwner, isRecording) {
    val observer = FloatingWindowManager.createLifecycleObserver(context) { isRecording }
    lifecycleOwner.lifecycle.addObserver(observer)
    
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      // 确保在组件销毁时关闭悬浮窗
      FloatingWindowManager.hideRecordingFloatingWindow()
    }
  }
  
  HomeScreen(voiceViewModel, aiViewModel)
}

@OptIn(ExperimentalPagerApi::class)
@Composable
fun HomeScreen(
  voiceViewModel: VoiceRecognitionViewModel,
  aiViewModel: AIChatViewModel
) {
  var selectedTab by rememberSaveable { mutableStateOf(HomeTab.VOICE_RECOGNITION) }
  val isRecording by voiceViewModel.isRecording.collectAsStateWithLifecycle()
  val pagerState = rememberPagerState()
  val coroutineScope = rememberCoroutineScope()
  
  // 同步Pager状态和Tab状态
  LaunchedEffect(selectedTab) {
    coroutineScope.launch {
      pagerState.animateScrollToPage(selectedTab.ordinal)
    }
  }
  
  LaunchedEffect(pagerState.currentPage) {
    selectedTab = HomeTab.values()[pagerState.currentPage]
  }
  
  Scaffold(
    modifier = Modifier
      .fillMaxSize()
      .statusBarsPadding(),
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      TabRow(
        selectedTabIndex = selectedTab.ordinal,
        modifier = Modifier.fillMaxWidth()
      ) {
        Tab(
          selected = selectedTab == HomeTab.VOICE_RECOGNITION,
          onClick = { 
            selectedTab = HomeTab.VOICE_RECOGNITION
            coroutineScope.launch {
              pagerState.animateScrollToPage(HomeTab.VOICE_RECOGNITION.ordinal)
            }
          },
          text = { Text("通话") }
        )
        Tab(
          selected = selectedTab == HomeTab.AI_CHAT,
          onClick = { 
            selectedTab = HomeTab.AI_CHAT 
            coroutineScope.launch {
              pagerState.animateScrollToPage(HomeTab.AI_CHAT.ordinal)
            }
          },
          text = { Text("AI") }
        )
      }
    }
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      // 使用HorizontalPager实现左右滑动
      HorizontalPager(
        count = HomeTab.values().size,
        state = pagerState,
        modifier = Modifier.fillMaxSize()
      ) { page ->
        when (HomeTab.values()[page]) {
          HomeTab.VOICE_RECOGNITION -> 
            AudioRecorderScreen(voiceViewModel, onSwitchToChat = { 
              selectedTab = HomeTab.AI_CHAT
              coroutineScope.launch {
                pagerState.animateScrollToPage(HomeTab.AI_CHAT.ordinal)
              }
            })
          HomeTab.AI_CHAT -> 
            AIChatScreen(voiceViewModel, aiViewModel)
        }
      }
    }
  }
}

@Composable
fun AudioRecorderScreen(
  viewModel: VoiceRecognitionViewModel,
  onSwitchToChat: () -> Unit = {}
) {
  val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
  val aggregatedMessages by viewModel.aggregatedMessages.collectAsStateWithLifecycle()
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var hasRecordingPermission by remember { mutableStateOf(false) }
  val focusManager = LocalFocusManager.current
  
  val context = LocalContext.current
  val listState = rememberLazyListState()
  
  // 检查录音权限
  hasRecordingPermission = ContextCompat.checkSelfPermission(
    context,
    Manifest.permission.RECORD_AUDIO
  ) == PackageManager.PERMISSION_GRANTED
  
  // 录音权限请求
  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    hasRecordingPermission = isGranted
    if (isGranted) {
      viewModel.startRecording()
    }
  }
  
  // 当有新消息时自动滚动到底部
  LaunchedEffect(aggregatedMessages.size) {
    if (aggregatedMessages.isNotEmpty()) {
      listState.animateScrollToItem(aggregatedMessages.size - 1)
    }
  }
  
  // 使用点击事件清除焦点
  Box(
    modifier = Modifier
      .fillMaxSize()
      .imePadding()
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null
      ) {
        focusManager.clearFocus()
      }
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // 消息列表
      LazyColumn(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .padding(horizontal = 16.dp),
        state = listState,
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(aggregatedMessages, key = { it.sentenceIds.joinToString() + it.speakerId }) { message ->
          if (message.speakerId == VoiceRecognitionViewModel.USER_SPEAKER_ID) {
             UserMessageItem(message = RecognitionMessage(message.speakerId, message.text, message.sentenceIds.firstOrNull() ?: 0, message.beginTime))
          } else {
             AggregatedMessageItem(message = message)
          }
        }
      }
      
      // 输入区域
      ChatInputField(
        onMessageSent = { /* No direct sending from here */ },
        isRecording = isRecording,
        onStartRecording = {
          if (!hasRecordingPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
          } else {
            viewModel.startRecording()
          }
        },
        onStopRecording = { viewModel.stopRecording() },
        onFocusGained = onSwitchToChat,
        focusManager = focusManager,
        uiState = uiState
      )
    }
    
    // 加载状态时显示加载指示器
    if (uiState is HomeUiState.Loading) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        CircularProgressIndicator(
          modifier = Modifier.size(48.dp),
          color = MaterialTheme.colorScheme.primary
        )
      }
    }
  }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ChatInputField(
  onMessageSent: (String) -> Unit,
  isRecording: Boolean = false,
  onStartRecording: () -> Unit = {},
  onStopRecording: () -> Unit = {},
  onFocusGained: () -> Unit = {},
  focusManager: FocusManager = LocalFocusManager.current,
  uiState: HomeUiState = HomeUiState.Idle
) {
  var message by remember { mutableStateOf("") }
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusRequester = remember { FocusRequester() }
  
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 8.dp)
      .navigationBarsPadding()
      .padding(bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // 输入框
    OutlinedTextField(
      value = message,
      onValueChange = { message = it },
      modifier = Modifier
        .weight(1f)
        .padding(end = 8.dp)
        .focusRequester(focusRequester)
        .onFocusChanged { 
          if (it.isFocused) {
            onFocusGained()
          }
        },
      placeholder = { Text("输入消息...") },
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
      keyboardActions = KeyboardActions(
        onSend = {
          keyboardController?.hide()
          focusManager.clearFocus()
        }
      ),
      shape = RoundedCornerShape(24.dp),
      colors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedTextColor = MaterialTheme.colorScheme.primary,
        unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        focusedIndicatorColor = MaterialTheme.colorScheme.primary
      )
    )
    
    // 录音/发送按钮
    if (isRecording) {
      // 录音中，显示停止按钮
      FloatingActionButton(
        onClick = onStopRecording,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.error
      ) {
        Icon(
          imageVector = Icons.Filled.Done,
          contentDescription = "停止录音",
          tint = Color.White
        )
      }
    } else if (uiState == HomeUiState.Loading) {
      // 加载中状态 - 显示加载动画
      Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center
      ) {
        CircularProgressIndicator(
          modifier = Modifier.size(36.dp),
          color = MaterialTheme.colorScheme.primary
        )
      }
    } else {
      // 普通状态 - 显示开始录音按钮
      FloatingActionButton(
        onClick = onStartRecording,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary
      ) {
        Icon(
          imageVector = Icons.Filled.Call,
          contentDescription = "开始录音",
          tint = Color.White
        )
      }
    }
  }
}

@Composable
fun AIChatScreen(
  voiceViewModel: VoiceRecognitionViewModel,
  aiViewModel: AIChatViewModel
) {
  val messages by aiViewModel.chatMessages.collectAsStateWithLifecycle()
  val aiState by aiViewModel.aiUiState.collectAsStateWithLifecycle()
  
  var inputText by remember { mutableStateOf("") }
  val listState = rememberLazyListState()
  val focusManager = LocalFocusManager.current
  
  // 当有新消息时自动滚动到底部
  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.lastIndex)
    }
  }
  
  // 使用点击事件清除焦点
  Box(
    modifier = Modifier
      .fillMaxSize()
      .imePadding()
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null
      ) {
        focusManager.clearFocus()
      }
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // 聊天消息列表
      LazyColumn(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .padding(horizontal = 16.dp),
        state = listState,
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(messages, key = { it.sentenceId.toString() + it.speakerId }) { message ->
          if (message.speakerId == AIChatViewModel.USER_SPEAKER_ID) {
             UserMessageItem(message = message)
          } else {
             AggregatedMessageItem(message = AggregatedMessage.fromRecognitionMessage(message))
          }
        }
      }
      
      when(aiState) {
          is AiUiState.Error -> {
              Text(
                  text = (aiState as AiUiState.Error).message ?: "Unknown AI error", 
                  color = MaterialTheme.colorScheme.error, 
                  modifier = Modifier.padding(8.dp)
              )
          }
          AiUiState.Loading -> {
              // Optionally show a subtle loading indicator
              // CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(8.dp))
          }
          else -> { /* Idle or Receiving - handled by message list updates */ }
      }
      
      AIChatInputField(
        value = inputText,
        onValueChange = { inputText = it },
        onSend = {
          if (inputText.isNotBlank()) {
            val fullAggregatedHistory = voiceViewModel.aggregatedMessages.value
            aiViewModel.sendMessageToAI(inputText, fullAggregatedHistory)
            inputText = ""
            focusManager.clearFocus()
          }
        },
        focusManager = focusManager
      )
    }
  }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AIChatInputField(
  value: String,
  onValueChange: (String) -> Unit,
  onSend: () -> Unit,
  focusManager: FocusManager = LocalFocusManager.current
) {
  val keyboardController = LocalSoftwareKeyboardController.current
  
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 8.dp)
      .navigationBarsPadding()
      .padding(bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      modifier = Modifier
        .weight(1f)
        .padding(end = 8.dp),
      placeholder = { Text("输入消息给AI...") },
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
      keyboardActions = KeyboardActions(
        onSend = {
          onSend()
          keyboardController?.hide()
          focusManager.clearFocus()
        }
      ),
      shape = RoundedCornerShape(24.dp),
      colors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedTextColor = MaterialTheme.colorScheme.primary,
        unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        focusedIndicatorColor = MaterialTheme.colorScheme.primary
      )
    )
    
    FloatingActionButton(
      onClick = {
        onSend()
        focusManager.clearFocus()
      },
      modifier = Modifier.size(48.dp),
      shape = CircleShape,
      containerColor = MaterialTheme.colorScheme.primary
    ) {
      Icon(
        imageVector = Icons.Filled.Send,
        contentDescription = "发送",
        tint = Color.White
      )
    }
  }
}

@Composable
fun AggregatedMessageItem(message: AggregatedMessage) {
  val alignment = Alignment.CenterStart
  
  val backgroundColor = if (message.speakerId == VoiceRecognitionViewModel.AI_SPEAKER_ID) 
                        MaterialTheme.colorScheme.surfaceVariant
                      else 
                        SpeakerColors.getColorForSpeaker(message.speakerId)
  val textColor = if (message.speakerId == VoiceRecognitionViewModel.AI_SPEAKER_ID) 
                    MaterialTheme.colorScheme.onSurfaceVariant 
                  else 
                    Color.Black
  
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp, vertical = 4.dp),
    contentAlignment = alignment
  ) {
    Card(
      modifier = Modifier
        .wrapContentWidth()
        .padding(
          start = if (alignment == Alignment.CenterStart) 0.dp else 40.dp, 
          end = if (alignment == Alignment.CenterEnd) 0.dp else 40.dp
        ),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(
        containerColor = backgroundColor
      ),
      elevation = CardDefaults.cardElevation(
        defaultElevation = 2.dp
      )
    ) {
      Column(
        modifier = Modifier.padding(12.dp)
      ) {
        if (message.speakerId != VoiceRecognitionViewModel.AI_SPEAKER_ID && message.speakerId != VoiceRecognitionViewModel.USER_SPEAKER_ID) {
          Text(
            text = "角色 ${message.speakerId}" + (if (message.messageCount > 1) " (${message.messageCount}条消息)" else ""),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = textColor.copy(alpha = 0.7f)
          )
        }
        
        Text(
          text = message.text + if(message.isStreaming) "..." else "",
          fontSize = 16.sp,
          color = textColor,
          modifier = Modifier.padding(top = if (message.speakerId != VoiceRecognitionViewModel.AI_SPEAKER_ID && message.speakerId != VoiceRecognitionViewModel.USER_SPEAKER_ID) 4.dp else 0.dp)
        )
      }
    }
  }
}

@Composable
fun UserMessageItem(message: RecognitionMessage) {
  val alignment = Alignment.CenterEnd
  val backgroundColor = MaterialTheme.colorScheme.primaryContainer
  val textColor = MaterialTheme.colorScheme.onPrimaryContainer

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp, vertical = 4.dp),
    contentAlignment = alignment
  ) {
    Card(
      modifier = Modifier
        .wrapContentWidth()
        .padding(
          start = if (alignment == Alignment.CenterStart) 0.dp else 40.dp, 
          end = if (alignment == Alignment.CenterEnd) 0.dp else 40.dp
         ),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(
        containerColor = backgroundColor
      ),
      elevation = CardDefaults.cardElevation(
        defaultElevation = 2.dp
      )
    ) {
      Text(
        text = message.text,
        fontSize = 16.sp,
        color = textColor,
        modifier = Modifier.padding(12.dp)
      )
    }
  }
}

@Composable
fun MessageItem(message: RecognitionMessage) {
  val alignment = Alignment.CenterStart
  
  val backgroundColor = SpeakerColors.getColorForSpeaker(message.speakerId)
  val textColor = Color.Black
  
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp),
    contentAlignment = alignment
  ) {
    Card(
      modifier = Modifier
        .wrapContentWidth()
        .padding(horizontal = 4.dp),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(
        containerColor = backgroundColor
      ),
      elevation = CardDefaults.cardElevation(
        defaultElevation = 2.dp
      )
    ) {
      Column(
        modifier = Modifier.padding(12.dp)
      ) {
        Text(
          text = "角色 ${message.speakerId}",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = textColor.copy(alpha = 0.7f)
        )
        
        Text(
          text = message.text,
          fontSize = 16.sp,
          color = textColor,
          modifier = Modifier.padding(top = 4.dp)
        )
      }
    }
  }
}
