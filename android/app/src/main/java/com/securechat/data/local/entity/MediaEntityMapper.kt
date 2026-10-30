package com.securechat.data.local.entity

import com.securechat.domain.model.Media
import com.securechat.domain.model.MediaResourceType

/**
 * Room row to domain model.
 *
 * The domain speaks the **server's** media id: `messages.mediaId` references
 * `media.serverId`, a socket frame carries the server id, and `/media/:id` is
 * addressed by it. The local autoincrement `id` is an implementation detail of
 * this device's cache and must not leak past this boundary.
 */
fun MediaEntity.toDomain(): Media = Media(
    id = serverId ?: id,
    messageId = messageId,
    cloudinaryPublicId = cloudinaryPublicId,
    resourceType = MediaResourceType.fromStorage(resourceType),
    secureUrl = secureUrl,
    originalFilename = originalFilename,
    mimeType = mimeType,
    fileSize = fileSize,
    width = width,
    height = height,
    duration = duration,
    sha256 = sha256,
    thumbnailUrl = thumbnailUrl,
    createdAt = createdAt
)
