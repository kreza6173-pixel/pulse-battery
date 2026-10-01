// IUserService.aidl
package io.github.kreza6173pixel.pulsebattery.exec;

/**
 * Runs a single shell command in the Shizuku user-service process.
 *
 * The result travels as an android.os.Bundle rather than as the ExecResult Parcelable.
 * That is forced by the toolchain, not a preference: the aidl compiler resolves types only
 * from its `-I` include directories and the framework, never from the Java classpath.
 * CI run 36808003294 failed `:app:compileDebugAidl` with
 *   ERROR: IUserService.aidl: Couldn't find import for class ExecResult.
 *   ERROR: IUserService.aidl:21.1-15: Failed to resolve 'ExecResult'
 * The only way to make ExecResult resolvable would be a `parcelable ExecResult;`
 * declaration, and that makes aidl emit an empty Java class of the same name, which
 * clashes with the Kotlin implementation. So the Bundle carries the four fields and
 * ExecResult.fromBundle() rebuilds the typed result on the client.
 *
 * The type is written fully qualified so it resolves without an import statement.
 */
interface IUserService {

    /**
     * Runs `command` with `/system/bin/sh -c`, blocking the calling binder thread.
     * Never returns null. A command that cannot even be started is reported as a non-zero
     * "exitCode" with the reason in "stderr".
     */
    android.os.Bundle exec(String command, int timeoutMs);

    /**
     * Destroys the process of the currently running command, if any. Safe to call when idle
     * and safe to call twice.
     */
    void cancel();
}