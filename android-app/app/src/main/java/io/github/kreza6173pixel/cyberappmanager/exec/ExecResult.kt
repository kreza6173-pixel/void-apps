package io.github.kreza6173pixel.cyberappmanager.exec

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable

/**
 * Result of one `exec` call. Immutable, and a `Parcelable` so it can be handed across a
 * binder boundary.
 *
 * The AIDL transport is a `Bundle`, not this Parcelable (see `IUserService.aidl`).
 * [toBundle] and [fromBundle] are the conversion, and the bundle keys and the parcel field
 * order are both pinned here so the two can never drift apart silently.
 */
data class ExecResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    /** True when stdout or stderr hit the size cap, or the command was killed by timeout. */
    val truncated: Boolean,
) : Parcelable {

    fun toBundle(): Bundle = Bundle().apply {
        putInt(KEY_EXIT_CODE, exitCode)
        putString(KEY_STDOUT, stdout)
        putString(KEY_STDERR, stderr)
        putBoolean(KEY_TRUNCATED, truncated)
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(exitCode)
        dest.writeString(stdout)
        dest.writeString(stderr)
        dest.writeInt(if (truncated) 1 else 0)
    }

    companion object {
        const val KEY_EXIT_CODE = "exitCode"
        const val KEY_STDOUT = "stdout"
        const val KEY_STDERR = "stderr"
        const val KEY_TRUNCATED = "truncated"

        /** Rebuilds the typed result from what crossed the binder. */
        fun fromBundle(bundle: Bundle): ExecResult = ExecResult(
            exitCode = bundle.getInt(KEY_EXIT_CODE, EXIT_CODE_UNKNOWN),
            stdout = bundle.getString(KEY_STDOUT).orEmpty(),
            stderr = bundle.getString(KEY_STDERR).orEmpty(),
            truncated = bundle.getBoolean(KEY_TRUNCATED, false),
        )

        /** Used when the bundle carries no exit code at all. */
        const val EXIT_CODE_UNKNOWN = 255

        @JvmField
        val CREATOR: Parcelable.Creator<ExecResult> = object : Parcelable.Creator<ExecResult> {
            override fun createFromParcel(source: Parcel): ExecResult = ExecResult(
                exitCode = source.readInt(),
                stdout = source.readString().orEmpty(),
                stderr = source.readString().orEmpty(),
                truncated = source.readInt() != 0,
            )

            override fun newArray(size: Int): Array<ExecResult?> = arrayOfNulls(size)
        }
    }
}
