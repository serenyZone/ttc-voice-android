package com.skydoves.pokedex.compose.feature.home.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

// 会话实体
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val recordingType: String, // MICROPHONE, SYSTEM_CALL
    val microphoneAudioPath: String? = null,
    val uplinkAudioPath: String? = null,
    val downlinkAudioPath: String? = null,
    val status: String = "ACTIVE" // ACTIVE, COMPLETED, ERROR
)

// 识别消息实体
@Entity(
    tableName = "recognition_messages",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["sessionId"])]
)
data class RecognitionMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val speakerId: Int,
    val text: String,
    val sentenceId: Int,
    val beginTime: Long,
    val isStreaming: Boolean = false
)

// AI聊天消息实体
@Entity(
    tableName = "ai_chat_messages",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["sessionId"])]
)
data class AiChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val speakerId: Int,
    val text: String,
    val sentenceId: Int,
    val beginTime: Long,
    val isStreaming: Boolean = false
)

// 会话DAO
@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>
    
    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): SessionEntity?
    
    @Insert
    suspend fun insertSession(session: SessionEntity): Long
    
    @Update
    suspend fun updateSession(session: SessionEntity)
    
    @Delete
    suspend fun deleteSession(session: SessionEntity)
    
    @Query("UPDATE sessions SET endTime = :endTime, status = :status WHERE id = :sessionId")
    suspend fun endSession(sessionId: Long, endTime: Long, status: String)
}

// 识别消息DAO
@Dao
interface RecognitionMessageDao {
    @Query("SELECT * FROM recognition_messages WHERE sessionId = :sessionId ORDER BY beginTime ASC")
    fun getRecognitionMessagesBySession(sessionId: Long): Flow<List<RecognitionMessageEntity>>
    
    @Insert
    suspend fun insertRecognitionMessage(message: RecognitionMessageEntity)
    
    @Update
    suspend fun updateRecognitionMessage(message: RecognitionMessageEntity)
    
    @Query("DELETE FROM recognition_messages WHERE sessionId = :sessionId")
    suspend fun deleteRecognitionMessagesBySession(sessionId: Long)
}

// AI聊天消息DAO
@Dao
interface AiChatMessageDao {
    @Query("SELECT * FROM ai_chat_messages WHERE sessionId = :sessionId ORDER BY beginTime ASC")
    fun getAiChatMessagesBySession(sessionId: Long): Flow<List<AiChatMessageEntity>>
    
    @Insert
    suspend fun insertAiChatMessage(message: AiChatMessageEntity)
    
    @Update
    suspend fun updateAiChatMessage(message: AiChatMessageEntity)
    
    @Query("DELETE FROM ai_chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteAiChatMessagesBySession(sessionId: Long)
}

// 数据库
@Database(
    entities = [SessionEntity::class, RecognitionMessageEntity::class, AiChatMessageEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SessionDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun recognitionMessageDao(): RecognitionMessageDao
    abstract fun aiChatMessageDao(): AiChatMessageDao
}

// 类型转换器
class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
} 