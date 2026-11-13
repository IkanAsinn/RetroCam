package com.retrocam.logic

/**
 * Lazily creates a heavy resource (the GL renderer), counts users, and releases it only when
 * nobody is using it, so closing the screen mid-save cannot kill an in-flight capture.
 * After a release the next [use] creates a fresh instance.
 */
class ReleasableHolder<T : Any>(
    private val create: () -> T,
    private val release: (T) -> Unit,
) {
    private val lock = Any()
    private var instance: T? = null
    private var users = 0
    private var releaseRequested = false

    suspend fun <R> use(block: suspend (T) -> R): R {
        val item = synchronized(lock) {
            releaseRequested = false
            users++
            instance ?: create().also { instance = it }
        }
        try {
            return block(item)
        } finally {
            val toRelease = synchronized(lock) {
                users--
                if (users == 0 && releaseRequested) takeInstance() else null
            }
            toRelease?.let(release)
        }
    }

    /** Release now if idle, otherwise as soon as the last user finishes. */
    fun requestRelease() {
        val toRelease = synchronized(lock) {
            if (users == 0) {
                takeInstance()
            } else {
                releaseRequested = true
                null
            }
        }
        toRelease?.let(release)
    }

    private fun takeInstance(): T? = instance.also {
        instance = null
        releaseRequested = false
    }
}
