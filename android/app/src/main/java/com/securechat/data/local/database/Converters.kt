package com.securechat.data.local.database

import androidx.room.TypeConverter
import com.securechat.domain.model.MediaResourceType
import com.securechat.domain.model.MessageStatus
import com.securechat.domain.model.MessageType

class Converters {

    @TypeConverter
    fun fromMessageType(value: String?): MessageType {
        return value?.let { MessageType.valueOf(it) } ?: MessageType.TEXT
    }

    @TypeConverter
    fun toMessageType(value: MessageType): String {
        return value.name
    }

    @TypeConverter
    fun fromMessageStatus(value: String?): MessageStatus {
        return value?.let { MessageStatus.valueOf(it) } ?: MessageStatus.PENDING
    }

    @TypeConverter
    fun toMessageStatus(value: MessageStatus): String {
        return value.name
    }

    @TypeConverter
    fun fromMediaResourceType(value: String?): MediaResourceType {
        return value?.let { MediaResourceType.valueOf(it) } ?: MediaResourceType.RAW
    }

    @TypeConverter
    fun toMediaResourceType(value: MediaResourceType): String {
        return value.name
    }

    @TypeConverter
    fun fromConversationType(value: String?): String {
        return value ?: "direct"
    }

    @TypeConverter
    fun toConversationType(value: String): String {
        return value
    }
}