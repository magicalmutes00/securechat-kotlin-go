package com.securechat.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * A message together with the attachment it refers to.
 *
 * The join is on the **server** media id: `messages.mediaId` stores the id the
 * backend assigned, and `media.serverId` mirrors it. The local autoincrement
 * `media.id` is meaningless to the message row (and to the server), so joining
 * on it resolved nothing.
 *
 * Before this existed, `MessageRepositoryImpl.mapToMessage(entity)` hardcoded
 * `media = null` ("loaded separately" — it never was), so every image, video and
 * document arrived as an empty bubble no matter what the server sent.
 */
data class MessageWithMedia(
    @Embedded val message: MessageEntity,
    @Relation(parentColumn = "mediaId", entityColumn = "serverId")
    val media: MediaEntity? = null
)
