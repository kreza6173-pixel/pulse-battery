package io.github.kreza6173pixel.pulsebattery.exec

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicBoolean

/** Client-side view of the UserService connection. Pure enum, unit-testable. */
enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

sealed interface ExecOutcome {
    data class Completed(val result: ExecResult, val durationMs: Long) : ExecOutcome
    data class Failed(val message: String) : ExecOutcome
}

/**
 * Binds the Shizuku UserService and runs commands through it.
 *
 * Recovery: the bridge listens for Shizuku's binder-dead event and unbinds itself, so the
 * next [connect] after a Shizuku restart starts from a clean state instead of holding a dead
 * binder. Callers should call [connect] whenever the home screen reaches `READY`.
 */
class ExecBridge(private val context: Context) {

    var connectionState: ConnectionState by mutableStateOf(ConnectionState.DISCONNECTED)
        private set

    private var service: IUserService? = null
    private val connecting = AtomicBoolean(false)
    private var started = false

    /** The component Shizuku is asked to bind. */
    private val component = ComponentName(context, ShizukuExecService::class.java)

    private val binderDeadListener = Shizuku.OnBinderDeadListener { handleShizukuDied() }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            connecting.set(false)
            if (binder == null) {
                service = null
                connectionState = ConnectionState.DISCONNECTED
                return
            }
            service = IUserService.Stub.asInterface(binder)
            connectionState = ConnectionState.CONNECTED
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            connecting.set(false)
            service = null
            connectionState = ConnectionState.DISCONNECTED
        }
    }

    /** Idempotent. Registers the Shizuku binder-dead listener once. */
    fun start() {
        if (started) return
        started = true
        runCatching { Shizuku.addBinderDeadListener(binderDeadListener) }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { Shizuku.removeBinderDeadListener(binderDeadListener) }
        disconnect()
    }

    /** Requests a bind. No-op when already connected or a bind is already in flight. */
    fun connect() {
        if (service != null || !connecting.compareAndSet(false, true)) return
        connectionState = ConnectionState.CONNECTING
        val args = Shizuku.UserServiceArgs(component)
            .daemon(false)
            .debuggable(true)
        // Shizuku.bindUserService returns a package-private ShizukuServiceConnection, which cannot
        // be named outside rikka.shizuku. The result is therefore discarded, and the call is
        // written as a statement rather than wrapped in runCatching so that no Kotlin type
        // inference ever has to name that return type.
        val bound = try {
            Shizuku.bindUserService(args, serviceConnection)
            true
        } catch (_: Exception) {
            false
        }
        if (!bound) {
            connecting.set(false)
            service = null
            connectionState = ConnectionState.DISCONNECTED
        }
    }

    fun disconnect() {
        runCatching { Shizuku.unbindUserService(Shizuku.UserServiceArgs(component), serviceConnection) }
        service = null
        connecting.set(false)
        connectionState = ConnectionState.DISCONNECTED
    }

    private fun handleShizukuDied() {
        service = null
        connecting.set(false)
        connectionState = ConnectionState.DISCONNECTED
    }

    /**
     * Runs [command] on the service. Must not be called from the main thread: the AIDL call
     * blocks the calling thread until the command finishes.
     */
    fun execBlocking(command: String, timeoutMs: Int): ExecOutcome {
        val binder = service
            ?: return ExecOutcome.Failed(FAILURE_NOT_CONNECTED)
        val startedAt = System.currentTimeMillis()
        return try {
            val bundle = binder.exec(command, timeoutMs)
                ?: return ExecOutcome.Failed(FAILURE_NULL_RESULT)
            ExecOutcome.Completed(
                ExecResult.fromBundle(bundle),
                System.currentTimeMillis() - startedAt,
            )
        } catch (e: Exception) {
            ExecOutcome.Failed("binder error: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** Asks the service to destroy the running child. Fire-and-forget. */
    fun cancel() {
        val binder = service ?: return
        runCatching { binder.cancel() }
            .onFailure { connectionState = ConnectionState.DISCONNECTED }
    }

    private companion object {
        const val FAILURE_NOT_CONNECTED = "not connected to the Shizuku user service"
        const val FAILURE_NULL_RESULT = "the user service returned no result"
    }
}