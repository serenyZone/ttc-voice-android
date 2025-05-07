package com.skydoves.pokedex.compose.feature.home

import android.util.Log
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.Stable
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.skydoves.pokedex.compose.core.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// --- Data Classes for Dify API (MOVED TO network/DifyData.kt) --- 

@HiltViewModel
class AIChatViewModel @Inject constructor() : BaseViewModel() {

  companion object {
    private const val TAG = "AIChatViewModel"
    // IMPORTANT: Replace with your actual Dify API Key
    private const val DIFY_API_KEY = "app-XNwBkN5ivDI5QjgBxpuJTVYH"
    private const val DIFY_BASE_URL = "https://api.dify.ai/v1"
    // 定义Speaker IDs，与VoiceRecognitionViewModel保持一致
    const val USER_SPEAKER_ID = -1
    const val AI_SPEAKER_ID = -2
  }

  // 存储AI聊天消息的独立列表
  private val _chatMessages = MutableStateFlow<List<RecognitionMessage>>(emptyList())
  val chatMessages: StateFlow<List<RecognitionMessage>> = _chatMessages.asStateFlow()

  // State for UI (e.g., loading indicator, errors specific to AI chat)
  private val _aiUiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
  val aiUiState: StateFlow<AiUiState> = _aiUiState.asStateFlow()

  private var aiChatJob: Job? = null
  private var currentConversationId: String? = null
  private val gson = Gson()
  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(120, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .writeTimeout(120, TimeUnit.SECONDS)
    .build()

  // 添加用户消息到聊天记录
  fun addUserMessage(text: String) {
    val userMessage = RecognitionMessage(
      speakerId = USER_SPEAKER_ID,
      text = text,
      sentenceId = System.currentTimeMillis().toInt(),
      beginTime = System.currentTimeMillis()
    )
    _chatMessages.value = _chatMessages.value + userMessage
  }

  // 更新或添加AI消息
  private fun updateOrAddAiMessage(aiMessage: RecognitionMessage) {
    val existingMessageIndex = _chatMessages.value.indexOfFirst { 
      it.sentenceId == aiMessage.sentenceId && it.speakerId == AI_SPEAKER_ID
    }
    
    _chatMessages.value = if (existingMessageIndex >= 0) {
      val updatedList = _chatMessages.value.toMutableList()
      updatedList[existingMessageIndex] = aiMessage
      updatedList
    } else {
      _chatMessages.value + aiMessage
    }
  }

  // 完成AI流式传输
  private fun finishAiStreaming(aiMessageId: Int) {
    val index = _chatMessages.value.indexOfFirst { it.sentenceId == aiMessageId && it.speakerId == AI_SPEAKER_ID }
    if (index >= 0) {
      val updatedList = _chatMessages.value.toMutableList()
      updatedList[index] = updatedList[index].copy(isStreaming = false)
      _chatMessages.value = updatedList
    }
  }

  // Function to send message to Dify API
  fun sendMessageToAI(userInput: String, aggregatedHistory: List<AggregatedMessage>) {
    aiChatJob?.cancel() // Cancel previous AI job if any

    _aiUiState.value = AiUiState.Loading // Indicate loading

    // 1. Format conversation history
    val dialogHistory = aggregatedHistory.joinToString("\n") { msg ->
      val role = when (msg.speakerId) {
          USER_SPEAKER_ID -> "用户"
          AI_SPEAKER_ID -> "AI"
          else -> "角色${msg.speakerId}"
      }
      "$role: ${msg.text}"
    }

    // 2. Add user message to our chat messages list
    addUserMessage(userInput)

    // 3. Prepare API Request
    val requestBody = ChatMessageRequest(
      query = userInput,
      inputs = mapOf("session" to dialogHistory), // Send history as inputs.dialog
      user = "user-pokedex-app", // Replace with actual user identifier if available
      conversationId = currentConversationId // Send previous ID if exists
    )
    val jsonBody = gson.toJson(requestBody)
    val request = Request.Builder()
      .url("$DIFY_BASE_URL/chat-messages")
      .post(jsonBody.toRequestBody("application/json".toMediaType()))
      .header("Authorization", "Bearer $DIFY_API_KEY")
      .build()

    // 4. Launch Coroutine for Network Call and Streaming
    aiChatJob = viewModelScope.launch(Dispatchers.IO) {
      var aiResponseMessage: RecognitionMessage? = null
      var currentAiMessageId : Int? = null
      try {
        Log.d(TAG, "Sending AI request: $jsonBody")
        val response: Response = httpClient.newCall(request).execute()
        Log.d(TAG, "AI Response Code: ${response.code}")

        if (!response.isSuccessful) {
          val errorBody = response.body?.string()
          Log.e(TAG, "AI Request Failed: ${response.code} - $errorBody")
          throw Exception("AI request failed: ${response.message} ($errorBody)")
        }

        // Handle Streaming Response
        val inputStream = response.body?.byteStream()
        val reader = BufferedReader(InputStreamReader(inputStream))

        reader.useLines { lines ->
          lines.forEach { line ->
             Log.d(TAG, "SSE Received: $line")
             if (line.startsWith("data:")) {
               val jsonData = line.substring(5).trim()
               if (jsonData.isNotEmpty()) {
                 var baseChunk: BaseChunkResponse? = null
                 try {
                   // Parse to BaseChunkResponse first to get the event type
                   baseChunk = gson.fromJson(jsonData, BaseChunkResponse::class.java)
                   
                   // Update conversation ID if not already set
                   if (currentConversationId == null && baseChunk.conversationId != null) {
                       currentConversationId = baseChunk.conversationId
                       Log.d(TAG, "Received Conversation ID: $currentConversationId")
                   }

                   when (baseChunk.event) {
                     "message" -> {
                       // Now parse specifically as MessageChunk
                       val messageChunk = gson.fromJson(jsonData, MessageChunk::class.java)
                       if (aiResponseMessage == null) {
                         currentAiMessageId = baseChunk.messageId?.hashCode() ?: System.currentTimeMillis().toInt()
                         aiResponseMessage = RecognitionMessage(
                           speakerId = AI_SPEAKER_ID,
                           text = messageChunk.answer, 
                           sentenceId = currentAiMessageId!!, 
                           beginTime = System.currentTimeMillis(),
                           isStreaming = true
                         )
                         withContext(Dispatchers.Main) {
                           updateOrAddAiMessage(aiResponseMessage!!)
                         }
                       } else {
                         aiResponseMessage = aiResponseMessage!!.copy(
                           text = aiResponseMessage!!.text + messageChunk.answer
                         )
                         withContext(Dispatchers.Main) {
                            updateOrAddAiMessage(aiResponseMessage!!)
                         }
                       }
                       // Indicate receiving data
                       if (_aiUiState.value == AiUiState.Loading) {
                            _aiUiState.value = AiUiState.Receiving
                       }
                     }
                     "message_end" -> {
                       if (currentAiMessageId != null) {
                         withContext(Dispatchers.Main) {
                           finishAiStreaming(currentAiMessageId!!)
                         }
                       }
                       _aiUiState.value = AiUiState.Idle // Stream ended successfully
                       Log.d(TAG, "AI stream ended. Conversation ID: $currentConversationId")
                     }
                     "error" -> {
                       val errorChunk = gson.fromJson(jsonData, ErrorChunk::class.java)
                       Log.e(TAG, "AI Stream Error: ${errorChunk.code} - ${errorChunk.message}")
                       if (currentAiMessageId != null) { 
                         withContext(Dispatchers.Main) {
                           finishAiStreaming(currentAiMessageId!!)
                         }
                       }
                       _aiUiState.value = AiUiState.Error("AI Error: ${errorChunk.message}")
                     }
                     "ping" -> { /* Ignore */ }
                     else -> Log.w(TAG, "Unhandled SSE event: ${baseChunk?.event}")
                   }
                 } catch (e: JsonSyntaxException) {
                    Log.e(TAG, "Error parsing SSE JSON: $jsonData, Event: ${baseChunk?.event ?: "Unknown"}", e)
                    _aiUiState.value = AiUiState.Error("Failed to parse AI response chunk")
                 } catch (e: Exception) {
                   Log.e(TAG, "Error processing SSE chunk: $jsonData, Event: ${baseChunk?.event ?: "Unknown"}", e)
                   _aiUiState.value = AiUiState.Error("Error processing AI response")
                 }
               }
             }
           }
        }
        // If the stream finishes without a message_end event (unexpected but possible)
        if (_aiUiState.value == AiUiState.Receiving || _aiUiState.value == AiUiState.Loading) {
             Log.w(TAG, "Stream finished without message_end event.")
            if (currentAiMessageId != null) {
              withContext(Dispatchers.Main) {
                finishAiStreaming(currentAiMessageId!!)
              }
            }
            _aiUiState.value = AiUiState.Idle
        }

      } catch (e: Exception) {
        Log.e(TAG, "AI Chat Error", e)
        if (currentAiMessageId != null) {
          withContext(Dispatchers.Main) {
            finishAiStreaming(currentAiMessageId!!)
          }
        }
        _aiUiState.value = AiUiState.Error("AI chat failed: ${e.message}")
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    aiChatJob?.cancel()
  }
}

// Sealed interface for AI Chat specific UI states
@Stable
sealed interface AiUiState {
  data object Idle : AiUiState
  data object Loading : AiUiState // Waiting for the first response chunk
  data object Receiving : AiUiState // Actively receiving stream
  data class Error(val message: String?) : AiUiState
} 