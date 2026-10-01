// IUserService.aidl
package io.github.kreza6173pixel.pulsebattery.exec;

/**
 * Runs a single shell command in the Shizuku user-service process.
 *
 * ExecResult is a custom Parcelable implemented in Kotlin
 * (io.github.kreza6173pixel.pulsebattery.exec.ExecResult). It is deliberately NOT declared
 * with `parcelable ExecResult;` here: that declaration makes the AIDL compiler emit an empty
 * Java stub class with the same fully-qualified name, which would clash with the Kotlin
 * implementation. AIDL marshals a Parcelable return value through Parcelable.writeToParcel /
 * Parcelable.CREATOR at runtime, so no declaration is needed.
 */
interface IUserService {

    /**
     * Runs `command` with `/system/bin/sh -c`, blocking the calling binder thread.
     * Never returns null; a failure to even start the process is reported as a non-zero
     * exitCode with the reason in stderr.
     */
    ExecResult exec(String command, int timeoutMs);

    /**
     * Destroys the process of the currently running command, if any. Safe to call when idle
     * and safe to call twice.
     */
    void cancel();
}