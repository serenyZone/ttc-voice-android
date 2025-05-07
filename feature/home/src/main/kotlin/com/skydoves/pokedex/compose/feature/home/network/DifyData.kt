package com.skydoves.pokedex.compose.feature.home

import com.google.gson.annotations.SerializedName

// --- Data Classes for Dify API --- 

data class ChatMessageRequest(
  val query: String,
  val inputs: Map<String, Any>,
  @SerializedName("response_mode") val responseMode: String = "streaming",
  val user: String,
  @SerializedName("conversation_id") val conversationId: String? = null,
  // files: List<Any>? = null, // Not implemented for now
  @SerializedName("auto_generate_name") val autoGenerateName: Boolean = true
)

// Base class to determine the event type first
data class BaseChunkResponse(
  val event: String,
  @SerializedName("task_id") val taskId: String? = null, 
  @SerializedName("message_id") val messageId: String? = null, 
  @SerializedName("conversation_id") val conversationId: String? = null,
)

// Specific event data classes (NO inheritance)
data class MessageChunk( // event: message
  val event: String, // Keep event field for potential use
  val answer: String,
  @SerializedName("created_at") val createdAt: Long,
  @SerializedName("message_id") val messageId: String? = null, 
  @SerializedName("conversation_id") val conversationId: String? = null,
)

data class MessageEndChunk( // event: message_end
  val event: String,
  val metadata: Map<String, Any>? = null,
  // val usage: Usage? = null, 
  // val retrieverResources: List<Any>? = null 
  @SerializedName("message_id") val messageId: String? = null, 
  @SerializedName("conversation_id") val conversationId: String? = null,
)

data class ErrorChunk( // event: error
  val event: String,
  val status: Int,
  val code: String,
  val message: String,
  @SerializedName("message_id") val messageId: String? = null, 
) 