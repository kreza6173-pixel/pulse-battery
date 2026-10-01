package io.github.kreza6173pixel.pulsebattery.exec

import android.os.Parcel
import android.os.Parcelable

/**
 * Result of one `exec` call. Marshalled through AIDL, so the field write order here and the
 * read order in [CREATOR] must stay in lockstep.
 */
data class ExecResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    /** True when stdout or stderr hit the size cap, or the command was killed by timeout. */
    val truncated: Boolean,
) : Parcelable {

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(exitCode)
        dest.writeString(stdout)
        dest.writeString(stderr)
        dest.writeInt(if (truncated) 1 else 0)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ExecResult> = object : Parcelable.Creator<ExecResult> {
            override fun createFromParcel(source: Parcel): ExecResult = ExecResult(
                exitCode = source.readInt(),
                stdout = source.readString() ?: "",
                stderr = source.readString() ?: "",
                truncated = source.readInt() != 0,
            )

            override fun newArray(size: Int): Array<ExecResult?> = arrayOfNulls(size)
        }
    }
}