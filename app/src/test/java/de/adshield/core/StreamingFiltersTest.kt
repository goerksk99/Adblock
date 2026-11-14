package de.adshield.core

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class StreamingFiltersTest {
    @Test fun byteLimitsIncludeSingleByteReadsAndCannotBeReset(){
        val stream=LimitedInputStream(ByteArrayInputStream(ByteArray(20)),10)
        repeat(10){assertEquals(0,stream.read())};assertTrue(runCatching{stream.read()}.exceptionOrNull() is IOException)
        assertFalse(stream.markSupported());assertTrue(runCatching{stream.reset()}.isFailure)
    }
    @Test fun streamedAndTextListsMatchAndCancellationIsNotSwallowed(){
        val text="# Title: oisd big\n# Syntax: Domains (wildcards)\n# Entries: 2\n*.one.example\n*.two.example\n"
        assertEquals(OisdRules.parse(text,1),OisdRules.parse(text.reader(),1))
        assertTrue(runCatching{OisdRules.parse(text.reader(),1){throw java.util.concurrent.CancellationException()}}.exceptionOrNull() is java.util.concurrent.CancellationException)
    }
    @Test fun largeLinesAndMultiByteStreamsStayBounded(){
        val input=LimitedInputStream(ByteArrayInputStream("ä".repeat(10).toByteArray()),10)
        assertTrue(runCatching{input.bufferedReader().readText()}.exceptionOrNull() is IOException)
    }
}
