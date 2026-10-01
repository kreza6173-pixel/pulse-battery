package io.github.kreza6173pixel.pulsebattery.exec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellQuotingTest {

    @Test
    fun `every result is wrapped in single quotes`() {
        for (arg in listOf("id", "a b", "", "\$(rm -rf /)", "x;y", "back`tick`")) {
            val quoted = ShellQuoting.quote(arg)
            assertTrue("not wrapped: [$arg] -> [$quoted]", quoted.startsWith("'"))
            assertTrue("not wrapped: [$arg] -> [$quoted]", quoted.endsWith("'"))
        }
    }

    @Test
    fun `plain word round-trips unchanged inside the quotes`() {
        assertEquals("'id'", ShellQuoting.quote("id"))
    }

    @Test
    fun `empty string is still a quoted empty argument`() {
        assertEquals("''", ShellQuoting.quote(""))
    }

    @Test
    fun `spaces stay inside one argument`() {
        assertEquals("'a b c'", ShellQuoting.quote("a b c"))
    }

    @Test
    fun `a single quote is escaped by closing escaping and reopening`() {
        assertEquals("'it'\\''s'", ShellQuoting.quote("it's"))
    }

    @Test
    fun `several single quotes are each escaped`() {
        // input `'''`  ->  body is three copies of the 4-char escape `'\''`  ->  wrapped
        assertEquals("''\\'''\\'''\\'''", ShellQuoting.quote("'''"))
    }

    @Test
    fun `only a quote is handled specially`() {
        // `'` -> body is the 4-char escape `'\''` -> wrapped it is 6 chars
        assertEquals("''\\'''", ShellQuoting.quote("'"))
    }

    @Test
    fun `dollar sign is neutralised`() {
        assertEquals("'\$HOME'", ShellQuoting.quote("\$HOME"))
        assertEquals("'\$(id)'", ShellQuoting.quote("\$(id)"))
    }

    @Test
    fun `backtick command substitution is neutralised`() {
        assertEquals("'`id`'", ShellQuoting.quote("`id`"))
    }

    @Test
    fun `newlines stay inside the quotes`() {
        assertEquals("'a\nb'", ShellQuoting.quote("a\nb"))
    }

    @Test
    fun `shell metacharacters are neutralised`() {
        assertEquals("';'", ShellQuoting.quote(";"))
        assertEquals("'&&'", ShellQuoting.quote("&&"))
        assertEquals("'|'", ShellQuoting.quote("|"))
        assertEquals("'*'", ShellQuoting.quote("*"))
        assertEquals("'~'", ShellQuoting.quote("~"))
        assertEquals("'>out'", ShellQuoting.quote(">out"))
    }

    @Test
    fun `backslash is literal inside single quotes`() {
        assertEquals("'a\\b'", ShellQuoting.quote("a\\b"))
    }

    @Test
    fun `joinArgs quotes each element separately`() {
        assertEquals("'a b' 'c'", ShellQuoting.joinArgs(listOf("a b", "c")))
    }

    @Test
    fun `joinArgs on an empty list is an empty command line`() {
        assertEquals("", ShellQuoting.joinArgs(emptyList()))
    }

    @Test
    fun `joinArgs cannot be tricked into starting a second command`() {
        // The classic injection: an argument that tries to close the quote and append its own
        // command. joinArgs quotes *every* element, including the command word, so the result
        // is one flat argv line and the hostile argument stays a single literal argument.
        val evil = "x'; rm -rf /; echo '"
        val line = ShellQuoting.joinArgs(listOf("echo", evil))
        assertTrue(line.startsWith("'echo' '"))
        assertTrue(line.endsWith("''"))
        // The hostile argument occupies the tail and does not spill over into a second word.
        assertEquals("'echo' " + ShellQuoting.quote(evil), line)
    }

    @Test
    fun `an injected quote cannot terminate the quoted region`() {
        // Note: an even apostrophe count is NOT the invariant. Each input apostrophe expands
        // to the 4-char escape `'\''`, which itself contains three apostrophes, so the count is
        // 2 + 3n. The invariant that matters is that no *bare* apostrophe survives in the body.
        for (evil in listOf("a'b", "';id", "\";id", "`id`", "\$(id)", "a\nrm -rf /")) {
            val body = ShellQuoting.quote(evil).removeSurrounding("'")
            val bare = body.replace("'\\''", "").count { it == '\'' }
            assertEquals("bare apostrophe survived for [$evil] in [$body]", 0, bare)
        }
    }

    @Test
    fun `unicode is preserved`() {
        assertEquals("'سلام'", ShellQuoting.quote("سلام"))
    }
}