package com.moatbot.infrastructure.messaging.discord

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiscordMessageUtilsTest {

    // stripMentions tests

    @Test
    fun `stripMentions removes user mentions`() {
        val content = "Hello <@123456789> how are you?"
        val result = DiscordMessageUtils.stripMentions(content)
        assertEquals("Hello  how are you?", result)
    }

    @Test
    fun `stripMentions removes nickname mentions`() {
        val content = "Hello <@!123456789> how are you?"
        val result = DiscordMessageUtils.stripMentions(content)
        assertEquals("Hello  how are you?", result)
    }

    @Test
    fun `stripMentions removes role mentions`() {
        val content = "Hello <@&123456789> everyone!"
        val result = DiscordMessageUtils.stripMentions(content)
        assertEquals("Hello  everyone!", result)
    }

    @Test
    fun `stripMentions removes channel mentions`() {
        val content = "Check out <#123456789> for more info"
        val result = DiscordMessageUtils.stripMentions(content)
        assertEquals("Check out  for more info", result)
    }

    @Test
    fun `stripMentions removes multiple mentions`() {
        val content = "<@123> said to <@456> in <#789>"
        val result = DiscordMessageUtils.stripMentions(content)
        assertEquals("said to  in", result)
    }

    @Test
    fun `stripMentions trims result`() {
        val content = "  <@123>  "
        val result = DiscordMessageUtils.stripMentions(content)
        assertEquals("", result)
    }

    @Test
    fun `stripMentions preserves non-mention content`() {
        val content = "Hello World!"
        val result = DiscordMessageUtils.stripMentions(content)
        assertEquals("Hello World!", result)
    }

    @Test
    fun `stripMentions handles mention-only message`() {
        val content = "<@123456789>"
        val result = DiscordMessageUtils.stripMentions(content)
        assertEquals("", result)
    }

    // splitMessage tests

    @Test
    fun `splitMessage returns single chunk for short messages`() {
        val content = "Hello World!"
        val result = DiscordMessageUtils.splitMessage(content, 100)
        assertEquals(1, result.size)
        assertEquals("Hello World!", result[0])
    }

    @Test
    fun `splitMessage splits at newline when possible`() {
        val content = "First line\nSecond line\nThird line"
        val result = DiscordMessageUtils.splitMessage(content, 20)

        assertTrue(result.size >= 2)
        assertTrue(result[0].contains("First line"))
    }

    @Test
    fun `splitMessage splits at space when no newline`() {
        val content = "Hello World this is a long message"
        val result = DiscordMessageUtils.splitMessage(content, 15)

        assertTrue(result.size >= 2)
        // Should split at word boundary
        assertTrue(result.all { it.length <= 15 || !it.contains(" ") })
    }

    @Test
    fun `splitMessage hard cuts when no good split point`() {
        val content = "AAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        val result = DiscordMessageUtils.splitMessage(content, 10)

        assertTrue(result.size >= 2)
        assertEquals(10, result[0].length)
    }

    @Test
    fun `splitMessage handles exact length`() {
        val content = "12345"
        val result = DiscordMessageUtils.splitMessage(content, 5)
        assertEquals(1, result.size)
        assertEquals("12345", result[0])
    }

    @Test
    fun `splitMessage handles empty string`() {
        val content = ""
        val result = DiscordMessageUtils.splitMessage(content, 100)
        assertEquals(1, result.size)
        assertEquals("", result[0])
    }

    @Test
    fun `splitMessage uses default maxLength of 1900`() {
        val content = "A".repeat(2000)
        val result = DiscordMessageUtils.splitMessage(content)

        assertTrue(result.size >= 2)
        assertEquals(1900, result[0].length)
    }

    @Test
    fun `splitMessage trims start of subsequent chunks`() {
        val content = "Hello World  \n  Next line"
        val result = DiscordMessageUtils.splitMessage(content, 15)

        // Second chunk should have leading whitespace trimmed
        if (result.size > 1) {
            assertTrue(!result[1].startsWith(" "))
        }
    }
}
