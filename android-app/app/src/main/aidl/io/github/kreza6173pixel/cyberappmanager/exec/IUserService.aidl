// IUserService.aidl
package io.github.kreza6173pixel.cyberappmanager.exec;

/**
 * Runs a single shell command in the Shizuku user-service process.
 *
 * The result travels as an android.os.Bundle rather than as the ExecResult Parcelable:
 * the aidl compiler resolves types only from its include directories and the framework,
 * never from the Java classpath (PULSE CI run 36808003294 failed with
 * "Couldn't find import for class ExecResult").
 *
 * Transaction codes are explicit because destroy() MUST use 16777114: that is the code the
 * Shizuku server sends when the service is unbound with remove=true. AIDL requires that
 * once one method has an explicit code, every method has one.
 */
interface IUserService {

    /** Reserved by Shizuku. Implementation cleans up and calls System.exit(0). */
    void destroy() = 16777114;

    /**
     * Runs `command` with `/system/bin/sh -c`, blocking the calling binder thread.
     * Never returns null. A command that cannot even be started is reported as a non-zero
     * "exitCode" with the reason in "stderr".
     */
    android.os.Bundle exec(String command, int timeoutMs) = 1;

    /**
     * Destroys the process of the currently running command, if any. Safe to call when idle
     * and safe to call twice.
     */
    void cancel() = 2;
}
