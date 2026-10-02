package io.github.kreza6173pixel.pulsebattery.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultParsersTest {

    /** From the phone, 2026-10-02: `pm path app.pwhs.blockads`. */
    private val realPmPath =
        "package:/data/app/~~e60mRa74asANb4qRjXA-Yw==/app.pwhs.blockads-i_qmUbxDQ2EWUNOLYegfAA==/base.apk"

    /** From the phone, 2026-10-02: `ls -l /sdcard/Download/PulseVault`. */
    private val realLs = listOf(
        "total 54544",
        "-rw-rw---- 1 u0_a253 media_rw 55792396 2026-10-02 05:19 test.apk",
    ).joinToString("\n")

    @Test
    fun pmPathRealLine() {
        val paths = VaultParsers.parsePmPath(realPmPath + "\n")
        assertEquals(1, paths.size)
        assertTrue(paths[0].startsWith("/data/app/"))
        assertEquals("base.apk", VaultParsers.baseName(paths[0]))
    }

    @Test
    fun pmPathWithSplits() {
        val out = listOf(
            "package:/data/app/x/com.a.b-1/base.apk",
            "package:/data/app/x/com.a.b-1/split_config.arm64_v8a.apk",
            "package:/data/app/x/com.a.b-1/split_config.xxhdpi.apk",
        ).joinToString("\n")
        assertEquals(3, VaultParsers.parsePmPath(out).size)
    }

    @Test
    fun lsRealLine() {
        val files = VaultParsers.parseLsFiles(realLs)
        assertEquals(listOf(VaultFile("test.apk", 55792396L)), files)
    }

    @Test
    fun vaultListingGroupsFilesPerPackage() {
        val out = listOf(
            "-rw-rw---- 1 u0_a253 media_rw 9 2026-10-02 05:19 stray.apk",
            "== app.pwhs.blockads",
            "total 54544",
            "-rw-rw---- 1 u0_a253 media_rw 55792396 2026-10-02 05:19 base.apk",
            "== com.a.b",
            "-rw-rw---- 1 u0_a253 media_rw 100 2026-10-02 05:20 base.apk",
            "-rw-rw---- 1 u0_a253 media_rw 50 2026-10-02 05:20 split_config.xxhdpi.apk",
        ).joinToString("\n")
        val v = VaultParsers.parseVaultListing(out)
        assertEquals(listOf("app.pwhs.blockads", "com.a.b"), v.map { it.pkg })
        assertEquals(1, v[0].apkCount)
        assertEquals(55792396L, v[0].totalBytes)
        assertEquals(2, v[1].apkCount)
        assertEquals(150L, v[1].totalBytes)
    }

    @Test
    fun emptyVault() {
        assertTrue(VaultParsers.parseVaultListing("").isEmpty())
    }
}
