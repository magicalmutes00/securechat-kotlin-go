package com.securechat.di

import com.securechat.domain.repository.AuthRepository
import com.securechat.domain.repository.ConversationRepository
import com.securechat.domain.repository.MediaRepository
import com.securechat.domain.repository.MessageRepository
import com.securechat.domain.repository.SyncRepository
import com.securechat.domain.usecase.auth.LogoutUseCase
import com.securechat.domain.usecase.auth.GoogleSignInUseCase
import com.securechat.domain.usecase.auth.RefreshTokenUseCase
import com.securechat.domain.usecase.auth.SendOtpUseCase
import com.securechat.domain.usecase.auth.VerifyOtpUseCase
import com.securechat.domain.usecase.chat.CreateConversationUseCase
import com.securechat.domain.usecase.chat.DeleteMessageUseCase
import com.securechat.domain.usecase.chat.GetConversationsUseCase
import com.securechat.domain.usecase.chat.GetMessagesUseCase
import com.securechat.domain.usecase.chat.MarkAsReadUseCase
import com.securechat.domain.usecase.chat.SendMessageUseCase
import com.securechat.domain.usecase.media.CompleteUploadUseCase
import com.securechat.domain.usecase.media.DeleteMediaUseCase
import com.securechat.domain.usecase.media.DownloadMediaUseCase
import com.securechat.domain.usecase.media.GetSignedUploadParamsUseCase
import com.securechat.domain.usecase.media.UploadMediaUseCase
import com.securechat.domain.usecase.sync.ForceFullSyncUseCase
import com.securechat.domain.usecase.sync.SyncPendingMediaUseCase
import com.securechat.domain.usecase.sync.SyncPendingMessagesUseCase
import com.securechat.domain.usecase.sync.SyncReadReceiptsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    // Auth UseCases
    @Provides
    @Singleton
    fun provideSendOtpUseCase(authRepository: AuthRepository): SendOtpUseCase = SendOtpUseCase(authRepository)

    @Provides
    @Singleton
    fun provideVerifyOtpUseCase(authRepository: AuthRepository): VerifyOtpUseCase = VerifyOtpUseCase(authRepository)

    @Provides
    @Singleton
    fun provideRefreshTokenUseCase(authRepository: AuthRepository): RefreshTokenUseCase = RefreshTokenUseCase(authRepository)

    @Provides
    @Singleton
    fun provideLogoutUseCase(authRepository: AuthRepository): LogoutUseCase = LogoutUseCase(authRepository)

    @Provides
    @Singleton
    fun provideGoogleSignInUseCase(authRepository: AuthRepository): GoogleSignInUseCase = GoogleSignInUseCase(authRepository)

    // Chat UseCases
    @Provides
    @Singleton
    fun provideGetConversationsUseCase(conversationRepository: ConversationRepository): GetConversationsUseCase = GetConversationsUseCase(conversationRepository)

    @Provides
    @Singleton
    fun provideCreateConversationUseCase(conversationRepository: ConversationRepository): CreateConversationUseCase = CreateConversationUseCase(conversationRepository)

    @Provides
    @Singleton
    fun provideGetMessagesUseCase(messageRepository: MessageRepository): GetMessagesUseCase = GetMessagesUseCase(messageRepository)

    @Provides
    @Singleton
    fun provideSendMessageUseCase(messageRepository: MessageRepository): SendMessageUseCase = SendMessageUseCase(messageRepository)

    @Provides
    @Singleton
    fun provideDeleteMessageUseCase(messageRepository: MessageRepository): DeleteMessageUseCase = DeleteMessageUseCase(messageRepository)

    @Provides
    @Singleton
    fun provideMarkAsReadUseCase(messageRepository: MessageRepository): MarkAsReadUseCase = MarkAsReadUseCase(messageRepository)

    // Media UseCases
    @Provides
    @Singleton
    fun provideGetSignedUploadParamsUseCase(mediaRepository: MediaRepository): GetSignedUploadParamsUseCase = GetSignedUploadParamsUseCase(mediaRepository)

    @Provides
    @Singleton
    fun provideUploadMediaUseCase(mediaRepository: MediaRepository): UploadMediaUseCase = UploadMediaUseCase(mediaRepository)

    @Provides
    @Singleton
    fun provideCompleteUploadUseCase(mediaRepository: MediaRepository): CompleteUploadUseCase = CompleteUploadUseCase(mediaRepository)

    @Provides
    @Singleton
    fun provideDownloadMediaUseCase(mediaRepository: MediaRepository): DownloadMediaUseCase = DownloadMediaUseCase(mediaRepository)

    @Provides
    @Singleton
    fun provideDeleteMediaUseCase(mediaRepository: MediaRepository): DeleteMediaUseCase = DeleteMediaUseCase(mediaRepository)

    // Sync UseCases
    @Provides
    @Singleton
    fun provideSyncPendingMessagesUseCase(syncRepository: SyncRepository): SyncPendingMessagesUseCase = SyncPendingMessagesUseCase(syncRepository)

    @Provides
    @Singleton
    fun provideSyncPendingMediaUseCase(syncRepository: SyncRepository): SyncPendingMediaUseCase = SyncPendingMediaUseCase(syncRepository)

    @Provides
    @Singleton
    fun provideSyncReadReceiptsUseCase(syncRepository: SyncRepository): SyncReadReceiptsUseCase = SyncReadReceiptsUseCase(syncRepository)

    @Provides
    @Singleton
    fun provideForceFullSyncUseCase(syncRepository: SyncRepository): ForceFullSyncUseCase = ForceFullSyncUseCase(syncRepository)
}