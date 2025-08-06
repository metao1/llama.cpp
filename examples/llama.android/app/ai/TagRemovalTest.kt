package com.metao.ai

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class TagRemovalTest {

    private fun removeTags(input: String): String {
        return input.replace(Regex("<\\w*[^>]*>?"), "")
    }

    @Test
    fun testRemoveSingleTag() {
        val input = "<start_of_turn>assistance"
        val expected = "assistance"
        assertEquals(expected, removeTags(input))
    }

    @Test
    fun testRemoveOnlyTag() {
        val input = "<"
        val expected = ""
        fun String.removeSpaces() = replace(Regex("\\s+"), "")
        assertEquals(expected, removeTags(input))
    }

    @Test
    fun testRemoveMultipleTags() {
        val input = "Hello <tag1>world<end>"
        val expected = "Hello world"
        assertEquals(expected, removeTags(input))
    }

    @Test
    fun testNoTags() {
        val input = "No tags here"
        val expected = "No tags here"
        assertEquals(expected, removeTags(input))
    }
}
