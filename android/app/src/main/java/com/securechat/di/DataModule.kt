package com.securechat.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.securechat.R
import com.securechat.core.media.CloudinaryUploadHelper
import com.securechat.core.network.NetworkModule
import com.securechat.core.security.E2eeKeyManager
import com.securechat.core.security.E2eeProtocolStore
import com.securechat.core.security.E2eeSessionManager
import com.securechat.core.security.TokenStorage
import com.securechat.data.local.database.SecureChatDatabase
import com.securechat.data.local.dao.*
import com.securechat.data.remote.api.ApiService
import com.securechat.data.remote.api.ApiServiceImpl
import com.securechat.data.remote.websocket.WebSocketManager
import com.securechat.data.repository.*
import com.securechat.domain.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SecureChatDatabase {
        return SecureChatDatabase.getInstance(context)
    }

    // DAOs
    @Provides
    @Singleton
    fun provideUserDao(database: SecureChatDatabase): UserDao = database.userDao()

    @Provides
    @Singleton
    fun provideDeviceDao(database: SecureChatDatabase): DeviceDao = database.deviceDao()

    @Provides
    @Singleton
    fun provideSessionDao(database: SecureChatDatabase): SessionDao = database.sessionDao()

    @Provides
    @Singleton
    fun provideConversationDao(database: SecureChatDatabase): ConversationDao = database.conversationDao()

    @Provides
    @Singleton
    fun provideConversationParticipantDao(database: SecureChatDatabase): ConversationParticipantDao = database.conversationParticipantDao()

    @Provides
    @Singleton
    fun provideMessageDao(database: SecureChatDatabase): MessageDao = database.messageDao()

    @Provides
    @Singleton
    fun provideMediaDao(database: SecureChatDatabase): MediaDao = database.mediaDao()

    @Provides
    @Singleton
    fun providePendingUploadDao(database: SecureChatDatabase): PendingUploadDao = database.pendingUploadDao()

    @Provides
    @Singleton
    fun providePendingOperationDao(database: SecureChatDatabase): PendingOperationDao = database.pendingOperationDao()

    @Provides
    @Singleton
    fun provideUserSettingsDao(database: SecureChatDatabase): UserSettingsDao = database.userSettingsDao()

    // Network

    @Provides
    @Singleton
    fun provideE2eeSessionDao(database: SecureChatDatabase): E2eeSessionDao = database.e2eeSessionDao()
    @Provides
    @Singleton
    fun provideNetworkModule(): NetworkModule = NetworkModule()

    @Provides
    @Singleton
    fun provideHttpClient(networkModule: NetworkModule): HttpClient = networkModule.getClient()

    @Provides
    @Singleton
    fun provideApiService(
        httpClient: HttpClient,
        @ApplicationContext context: Context,
        tokenStorage: TokenStorage,
        // dagger.Lazy breaks the ApiService ↔ AuthRepository dependency cycle
        // (the repository is built on top of this service).
        authRepository: dagger.Lazy<AuthRepository>
    ): ApiService {
        val baseUrl = context.getString(R.string.server_base_url)
        return ApiServiceImpl(
            client = httpClient,
            baseUrl = baseUrl,
            tokenProvider = { runBlocking { tokenStorage.getAccessToken().getOrNull() } },
            refreshAuth = { authRepository.get().refreshToken().isSuccess }
        )
    }

    // WebSocket
    @Provides
    @Singleton
    fun provideWebSocketManager(
        httpClient: HttpClient,
        @ApplicationContext context: Context,
        tokenStorage: TokenStorage
    ): WebSocketManager {
        val wsUrl = context.getString(R.string.server_ws_url)
        return WebSocketManager(httpClient, wsUrl) {
            runBlocking { tokenStorage.getAccessToken().getOrNull() }
        }
    }

    // Media
    @Provides
    @Singleton
    fun provideCloudinaryUploadHelper(@ApplicationContext context: Context): CloudinaryUploadHelper {
        return CloudinaryUploadHelper(context)
    }

    // E2EE (Phase E1 + E2)
    @Provides
    @Singleton
    fun provideE2eeProtocolStore(
        @ApplicationContext context: Context,
        keyManager: E2eeKeyManager,
        sessionDao: E2eeSessionDao
    ): E2eeProtocolStore = E2eeProtocolStore(context, keyManager, sessionDao)

    @Provides
    @Singleton
    fun provideE2eeSessionManager(
        protocolStore: E2eeProtocolStore
    ): E2eeSessionManager = E2eeSessionManager(protocolStore)

    @Provides
    @Singleton
    fun provideE2eeRepository(
        apiService: ApiService,
        keyManager: E2eeKeyManager,
        sessionManager: E2eeSessionManager
    ): E2eeRepository = E2eeRepositoryImpl(apiService, keyManager, sessionManager)

    @Provides
    @Singleton
    fun provideAuthRepository(
        apiService: ApiService,
        tokenStorage: TokenStorage,
        userDao: UserDao,
        database: SecureChatDatabase
    ): AuthRepository = AuthRepositoryImpl(apiService, tokenStorage, userDao, database)

    @Provides
    @Singleton
    fun provideConversationRepository(
        apiService: ApiService,
        database: SecureChatDatabase,
        conversationDao: ConversationDao,
        participantDao: ConversationParticipantDao
    ): ConversationRepository = ConversationRepositoryImpl(apiService, database, conversationDao, participantDao)

    @Provides
    @Singleton
    fun provideMessageRepository(
        apiService: ApiService,
        database: SecureChatDatabase,
        messageDao: MessageDao,
        mediaDao: MediaDao,
        webSocketManager: WebSocketManager,
        operationQueue: com.securechat.data.sync.PendingOperationQueue,
        e2eeRepository: E2eeRepository,
        deviceDao: DeviceDao,
        conversationParticipantDao: ConversationParticipantDao
    ): MessageRepository = MessageRepositoryImpl(
        apiService, database, messageDao, mediaDao, webSocketManager, operationQueue,
        e2eeRepository, deviceDao, conversationParticipantDao
    )

    @Provides
    @Singleton
    fun provideMediaRepository(
        apiService: ApiService,
        database: SecureChatDatabase,
        mediaDao: MediaDao,
        cloudinaryHelper: CloudinaryUploadHelper
    ): MediaRepository = MediaRepositoryImpl(apiService, database, mediaDao, cloudinaryHelper)

    @Provides
    @Singleton
    fun provideUserRepository(
        apiService: ApiService,
        database: SecureChatDatabase,
        userDao: UserDao,
        userSettingsDao: UserSettingsDao
    ): UserRepository = UserRepositoryImpl(apiService, database, userDao, userSettingsDao)

    @Provides
    @Singleton
    fun provideDeviceRepository(
        apiService: ApiService,
        database: SecureChatDatabase,
        deviceDao: DeviceDao
    ): DeviceRepository = DeviceRepositoryImpl(apiService, database, deviceDao)

    @Provides
    @Singleton
    fun provideSyncRepository(
        messageDao: MessageDao,
        pendingUploadDao: PendingUploadDao,
        mediaSendQueue: com.securechat.data.sync.MediaSendQueue,
        mediaMessageSender: com.securechat.data.sync.MediaMessageSender,
        operationQueue: com.securechat.data.sync.PendingOperationQueue,
        webSocketManager: WebSocketManager,
        apiService: ApiService
    ): SyncRepository = SyncRepositoryImpl(
        messageDao, pendingUploadDao, mediaSendQueue, mediaMessageSender, operationQueue, webSocketManager, apiService
    )

    @Provides
    @Singleton
    fun provideAiRepository(): AiRepository = AiRepositoryImpl()

    // Push notifications
    @Provides
    @Singleton
    fun providePushRepository(
        apiService: ApiService,
        sharedPreferences: SharedPreferences,
        userRepository: UserRepository
    ): PushRepository = PushRepositoryImpl(apiService, sharedPreferences, userRepository)
}

// Placeholder for AI repository
class AiRepositoryImpl : com.securechat.domain.repository.AiRepository {
    override suspend fun getSmartReplies(conversationId: Long, lastMessages: List<String>) =
        com.securechat.core.common.Result.failure<List<String>>(UnsupportedOperationException("AI not implemented"))

    override suspend fun summarizeConversation(conversationId: Long, messageCount: Int) =
        com.securechat.core.common.Result.failure<String>(UnsupportedOperationException("AI not implemented"))

    override suspend fun summarizeDocument(documentUrl: String) =
        com.securechat.core.common.Result.failure<String>(UnsupportedOperationException("AI not implemented"))

    override suspend fun searchMessages(query: String, conversationId: Long?) =
        com.securechat.core.common.Result.failure<List<com.securechat.domain.repository.SearchResult>>(UnsupportedOperationException("AI not implemented"))

    override suspend fun chatWithAssistant(message: String, context: String?) =
        com.securechat.core.common.Result.failure<String>(UnsupportedOperationException("AI not implemented"))

    override fun observeAiAvailability() = kotlinx.coroutines.flow.flowOf(false)
}
