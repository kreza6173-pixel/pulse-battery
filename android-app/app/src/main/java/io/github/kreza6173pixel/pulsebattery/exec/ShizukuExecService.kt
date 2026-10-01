package io.github.kreza6173pixel.pulsebattery.exec

import android.app.Service
import android.content.Intent
import android.os.IBinder
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The Shizuku UserService. Lives in the Shizuku-spawned process, so commands run with
 * Shizuku's privileges (uid 2000 shell, or uid 0 root) rather than the app's own uid.
 *
 * Guarantees:
 *  - commands run via `/system/bin/sh -c`, one at a time (single-threaded executor);
 *  - stdout and stderr are drained concurrently, so a full pipe buffer cannot deadlock the child;
 *  - both streams are capped and set `truncated = true` once the cap is reached;
 *  - the child is destroyed when the timeout expires;
 *  - [cancel] destroys the child of the command currently running.
 */
class ShizukuExecService : Service() {

    /** Serialises commands: exactly one command at a time, in submission order. */
    private val commandExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /** Drains output streams. Cached so a short-lived daemon thread per stream. */
    private val drainExecutor: ExecutorService = Executors.newCachedThreadPool()

    /** The process currently running, so [cancel] can destroy it. */
    private val running = AtomicReference<Process?>(null)

    /** The last process [cancel] destroyed, so the run can tell it was cancelled. */
    private val cancelled = AtomicReference<Process?>(null)

    private val binder = object : IUserService.Stub() {

        override fun exec(command: String?, timeoutMs: Int): ExecResult? {
            val cmd = command.orEmpty()
            if (cmd.isBlank()) {
                return ExecResult(EXIT_BAD_COMMAND, "", "empty command", false)
            }
            val task = commandExecutor.submit { runOnce(cmd, timeoutMs) }
            return runCatching { task.get() }.getOrElse { failure ->
                ExecResult(EXIT_INTERNAL, "", "exec failed: ${failure.javaClass.simpleName}", false)
            }
        }

        override fun cancel() {
            running.getAndSet(null)?.let { process ->
                cancelled.set(process)
                process.destroy()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        binder.cancel()
        commandExecutor.shutdownNow()
        drainExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun runOnce(command: String, timeoutMs: Int): ExecResult {
        val timeout = timeoutMs.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS).toLong()
        val out = OutputCollector(MAX_OUTPUT_CHARS)
        val err = OutputCollector(MAX_OUTPUT_CHARS)

        val process = try {
            ProcessBuilder(SHELL_PATH, "-c", command).redirectErrorStream(false).start()
        } catch (e: Exception) {
            return ExecResult(
                EXIT_INTERNAL,
                "",
                "cannot start $SHELL_PATH: ${e.javaClass.simpleName}: ${e.message}",
                false,
            )
        }
        // Nothing may be written to the child; close stdin so a command that reads it fails
        // fast instead of blocking until the timeout.
        runCatching { process.outputStream.close() }
        running.set(process)

        val readers = listOf(
            drainExecutor.submit { drain(process.inputStream, out) },
            drainExecutor.submit { drain(process.errorStream, err) },
        )

        var exitCode = EXIT_INTERNAL
        var timedOut = false
        try {
            if (process.waitFor(timeout, TimeUnit.MILLISECONDS)) {
                exitCode = process.exitValue()
            } else {
                timedOut = true
                stop(process)
            }
        } catch (e: InterruptedException) {
            stop(process)
            Thread.currentThread().interrupt()
        } finally {
            running.compareAndSet(process, null)
        }

        // destroy() closes both streams, which unblocks the readers.
        awaitAll(readers)

        val wasCancelled = cancelled.compareAndSet(process, null)
        if (timedOut) {
            out.markTruncated()
            err.markTruncated()
        }
        if (wasCancelled) err.appendLine(CANCELLED_MARKER)

        return ExecResult(
            exitCode = if (timedOut || wasCancelled) CANCELLED_EXIT_CODE else exitCode,
            stdout = out.text,
            stderr = err.text,
            truncated = out.truncated || err.truncated || timedOut,
        )
    }

    private fun stop(process: Process) {
        process.destroy()
        runCatching {
            if (!process.waitFor(DESTROY_GRACE_MS, TimeUnit.MILLISECONDS)) process.destroyForcibly()
        }
    }

    /** Drains one stream. Runs on a pooled thread so the child can never block on a full pipe. */
    private fun drain(stream: InputStream, sink: OutputCollector) {
        try {
            stream.bufferedReader().use { reader ->
                val chunk = CharArray(READ_CHUNK)
                while (true) {
                    val n = reader.read(chunk)
                    if (n < 0) break
                    if (!sink.appendAll(CharArray(chunk, 0, n))) break
                }
            }
        } catch (_: Exception) {
            // Stream closed underneath us by destroy(); whatever was collected stands.
        }
    }

    /** Bounded wait, so a wedged reader can never hang the binder call forever. */
    private fun awaitAll(futures: List<Future<*>>) {
        for (f in futures) runCatching { f.get(JOIN_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
    }

    private companion object {
        const val SHELL_PATH = "/system/bin/sh"
        const val MAX_OUTPUT_CHARS = 64 * 1024
        const val READ_CHUNK = 4096
        const val MIN_TIMEOUT_MS = 250L
        const val MAX_TIMEOUT_MS = 120_000L
        const val DESTROY_GRACE_MS = 500L
        const val JOIN_TIMEOUT_MS = 2_000L
        const val CANCELLED_MARKER = "cancelled by user"

        /** Reported when the child was killed for exceeding its timeout or was cancelled. */
        const val CANCELLED_EXIT_CODE = 124

        /** Reported for an empty command. */
        const val EXIT_BAD_COMMAND = 2

        /** Reported when the process could not be started or the call itself failed. */
        const val EXIT_INTERNAL = 127
    }
}