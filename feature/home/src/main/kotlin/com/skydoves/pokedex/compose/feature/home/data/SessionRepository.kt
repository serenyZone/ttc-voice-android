package com.skydoves.pokedex.compose.feature.home.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

// 会话详情数据类
data class SessionDetail(
    val session: SessionEntity,
    val recognitionMessages: List<RecognitionMessageEntity>,
    val aiChatMessages: List<AiChatMessageEntity>
)

// 会话摘要数据类
data class SessionSummary(
    val session: SessionEntity,
    val messageCount: Int,
    val aiMessageCount: Int,
    val lastActivity: Long
)

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val recognitionMessageDao: RecognitionMessageDao,
    private val aiChatMessageDao: AiChatMessageDao
) {
    
    // 获取所有会话
    fun getAllSessions(): Flow<List<SessionEntity>> {
        return sessionDao.getAllSessions()
    }
    
    // 获取所有会话摘要
    fun getAllSessionSummaries(): Flow<List<SessionSummary>> {
        return combine(
            sessionDao.getAllSessions(),
            recognitionMessageDao.getRecognitionMessagesBySession(0),
            aiChatMessageDao.getAiChatMessagesBySession(0)
        ) { sessions, _, _ ->
            sessions.map { session ->
                SessionSummary(
                    session = session,
                    messageCount = 0,
                    aiMessageCount = 0,
                    lastActivity = session.endTime ?: session.startTime
                )
            }
        }
    }
    
    // 获取会话详情
    fun getSessionDetail(sessionId: Long): Flow<SessionDetail?> {
        return combine(
            sessionDao.getAllSessions(),
            recognitionMessageDao.getRecognitionMessagesBySession(sessionId),
            aiChatMessageDao.getAiChatMessagesBySession(sessionId)
        ) { sessions, recognitionMessages, aiMessages ->
            val session = sessions.find { it.id == sessionId }
            session?.let {
                SessionDetail(
                    session = it,
                    recognitionMessages = recognitionMessages,
                    aiChatMessages = aiMessages
                )
            }
        }
    }
    
    // 创建新会话
    suspend fun createSession(recordingType: String): Long {
        val session = SessionEntity(
            startTime = System.currentTimeMillis(),
            recordingType = recordingType,
            status = "ACTIVE"
        )
        return sessionDao.insertSession(session)
    }
    
    // 结束会话
    suspend fun endSession(sessionId: Long, status: String = "COMPLETED") {
        sessionDao.endSession(sessionId, System.currentTimeMillis(), status)
    }
    
    // 更新会话音频路径
    suspend fun updateSessionAudioPaths(
        sessionId: Long,
        microphoneAudioPath: String? = null,
        uplinkAudioPath: String? = null,
        downlinkAudioPath: String? = null
    ) {
        val session = sessionDao.getSessionById(sessionId)
        session?.let {
            val updatedSession = it.copy(
                microphoneAudioPath = microphoneAudioPath ?: it.microphoneAudioPath,
                uplinkAudioPath = uplinkAudioPath ?: it.uplinkAudioPath,
                downlinkAudioPath = downlinkAudioPath ?: it.downlinkAudioPath
            )
            sessionDao.updateSession(updatedSession)
        }
    }
    
    // 添加识别消息
    suspend fun addRecognitionMessage(
        sessionId: Long,
        speakerId: Int,
        text: String,
        sentenceId: Int,
        beginTime: Long,
        isStreaming: Boolean = false
    ) {
        val message = RecognitionMessageEntity(
            sessionId = sessionId,
            speakerId = speakerId,
            text = text,
            sentenceId = sentenceId,
            beginTime = beginTime,
            isStreaming = isStreaming
        )
        recognitionMessageDao.insertRecognitionMessage(message)
    }
    
    // 更新识别消息
    suspend fun updateRecognitionMessage(message: RecognitionMessageEntity) {
        recognitionMessageDao.updateRecognitionMessage(message)
    }
    
    // 添加AI聊天消息
    suspend fun addAiChatMessage(
        sessionId: Long,
        speakerId: Int,
        text: String,
        sentenceId: Int,
        beginTime: Long,
        isStreaming: Boolean = false
    ) {
        val message = AiChatMessageEntity(
            sessionId = sessionId,
            speakerId = speakerId,
            text = text,
            sentenceId = sentenceId,
            beginTime = beginTime,
            isStreaming = isStreaming
        )
        aiChatMessageDao.insertAiChatMessage(message)
    }
    
    // 更新AI聊天消息
    suspend fun updateAiChatMessage(message: AiChatMessageEntity) {
        aiChatMessageDao.updateAiChatMessage(message)
    }
    
    // 删除会话
    suspend fun deleteSession(sessionId: Long) {
        val session = sessionDao.getSessionById(sessionId)
        session?.let {
            sessionDao.deleteSession(it)
        }
    }
    
    // 获取当前活跃会话
    suspend fun getActiveSession(): SessionEntity? {
        return sessionDao.getAllSessions().let { flow ->
            // 这里需要同步获取，可能需要调整实现
            null // 临时返回null，实际实现中需要处理
        }
    }
} 