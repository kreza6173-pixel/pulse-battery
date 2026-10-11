package io.github.kreza6173pixel.pulsebattery.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataVaultParsersTest {

    @Test
    fun readsTheOwnerUid() {
        assertEquals(10176, DataVaultParsers.parseUid("10176\n"))
        assertEquals(1000, DataVaultParsers.parseUid("  1000  "))
    }

    @Test
    fun anErrorLineIsNotAUid() {
        assertNull(DataVaultParsers.parseUid("stat: '/data/data/x': No such file or directory"))
        assertNull(DataVaultParsers.parseUid(""))
        assertNull(DataVaultParsers.parseUid("10176 extra"))
    }

    @Test
    fun readsAPaddedCount() {
        assertEquals(42, DataVaultParsers.parseCount("      42\n"))
        assertEquals(2, DataVaultParsers.parseCount("2"))
        assertNull(DataVaultParsers.parseCount("not a number"))
    }

    @Test
    fun listsArchiveEntries() {
        val out = "./\n./shared_prefs/\n./databases/app.db\n"
        assertEquals(3, DataVaultParsers.parseArchiveEntries(out).size)
    }

    @Test
    fun anUnreadableArchiveHasNoEntries() {
        val out = "tar: Invalid gzip magic\ntar: short read\n"
        assertTrue(DataVaultParsers.parseArchiveEntries(out).isEmpty())
    }

    @Test
    fun theIsolationErrorDoesNotLookLikeAnArchive() {
        // Exactly what the phone reported before the namespace fix.
        val out = "tar: chdir '/data/data/app.morphe.manager': No such file or directory"
        assertTrue(DataVaultParsers.parseArchiveEntries(out).isEmpty())
    }

    @Test
    fun readsTheDirectoryProbe() {
        assertTrue(DataVaultParsers.probedYes("yes\n"))
        assertFalse(DataVaultParsers.probedYes(""))
        assertFalse(DataVaultParsers.probedYes("no"))
    }

    @Test
    fun pathsAreBuiltFromThePackageName() {
        assertEquals("/data/data/com.example", DataVault.privateDir("com.example"))
        assertEquals("data/com.example", DataVault.externalMember("com.example"))
        assertEquals("obb/com.example", DataVault.obbMember("com.example"))
        assertEquals("/sdcard/Android/data/com.example", DataVault.externalDir("com.example"))
        assertEquals("/sdcard/Android/obb/com.example", DataVault.obbDir("com.example"))
    }

    @Test
    fun stagingStaysOnDataNotOnSharedStorage() {
        // init's mount namespace has no per-user /sdcard view, so the handover file must not
        // live there.
        assertTrue(DataVault.STAGING_DIR.startsWith("/data/"))
        assertFalse(DataVault.STAGING_DIR.startsWith("/sdcard"))
        assertEquals(
            "/data/local/tmp/pulse_data/data.tar.gz",
            DataVault.stagedArchive(DataVault.DATA_ARCHIVE),
        )
    }

    @Test
    fun theNamespaceWrapperPassesTheCommandAsOneArgument() {
        val wrapped = DataVault.inInitNamespace("tar -czf '/tmp/a.tar.gz' -C '/data/data/x' .")
        assertTrue(wrapped.startsWith("nsenter --mount=/proc/1/ns/mnt -- sh -c "))
        // The inner single quotes survive, escaped, so the outer shell cannot reinterpret them.
        assertTrue(wrapped.contains("'\\''"))
    }

    @Test
    fun aZeroByteArchiveDoesNotCountAsABackup() {
        val empty = VaultEntry("com.example", listOf(VaultFile(DataVault.DATA_ARCHIVE, 0L)))
        assertFalse(empty.hasDataArchive)
        val real = VaultEntry("com.example", listOf(VaultFile(DataVault.DATA_ARCHIVE, 1234L)))
        assertTrue(real.hasDataArchive)
        assertEquals(1234L, real.dataBytes)
    }

    @Test
    fun archiveSizesAreReadPerArchive() {
        val entry = VaultEntry(
            "com.example",
            listOf(
                VaultFile("base.apk", 100L),
                VaultFile(DataVault.DATA_ARCHIVE, 200L),
                VaultFile(DataVault.EXTERNAL_ARCHIVE, 300L),
            ),
        )
        assertEquals(1, entry.apkCount)
        assertEquals(200L, entry.dataBytes)
        assertEquals(300L, entry.externalBytes)
        assertEquals(600L, entry.totalBytes)
    }
}
