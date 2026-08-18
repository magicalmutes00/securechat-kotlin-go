package com.securechat.core.common

sealed interface Result<out T> {
    data class Success<out T>(val data: T) : Result<T>
    data class Failure(val exception: Throwable) : Result<Nothing>

    companion object {
        fun <T> success(data: T): Result<T> = Success(data)
        fun <T> failure(exception: Throwable): Result<T> = Failure(exception)
        fun <T> runCatching(block: () -> T): Result<T> =
            try {
                Success(block())
            } catch (e: Throwable) {
                Failure(e)
            }
    }

    suspend fun <R> map(transform: suspend (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

    suspend fun <R> flatMap(transform: suspend (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(data)
        is Failure -> this
    }

    suspend fun onSuccess(action: suspend (T) -> Unit): Result<T> = when (this) {
        is Success -> {
            action(data)
            this
        }
        is Failure -> this
    }

    suspend fun onFailure(action: suspend (Throwable) -> Unit): Result<T> = when (this) {
        is Success -> this
        is Failure -> {
            action(exception)
            this
        }
    }

    fun getOrElse(default: () -> @UnsafeVariance T): T = when (this) {
        is Success -> data
        is Failure -> default()
    }

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Failure -> throw exception
    }

    val isSuccess: Boolean
        get() = this is Success

    val isFailure: Boolean
        get() = this is Failure
}