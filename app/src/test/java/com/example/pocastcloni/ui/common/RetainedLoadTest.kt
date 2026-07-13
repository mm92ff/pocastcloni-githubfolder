package com.example.pocastcloni.ui.common

import com.example.pocastcloni.ui.UiText
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RetainedLoadTest {
    @Test
    fun `retainLatestValue preserves content when a retry fails before emitting`() = runTest {
        val retained = listOf("saved")
        val values =
            flow {
                emit(RetainedLoad(loading = false, lastValue = retained))
                emit(RetainedLoad<List<String>>(loading = false, error = errorMessage))
            }.retainLatestValue().toList()

        assertEquals(retained, values.last().lastValue)
        assertEquals(errorMessage, values.last().error)
    }

    private val errorMessage = UiText.DynamicString("load failed")

    @Test
    fun `loaded value is retained when the flow later fails`() = runTest {
        val states =
            flow {
                emit(listOf("episode"))
                throw IOException("temporary")
            }.asRetainedLoad(errorMessage).toList()

        assertEquals(listOf("episode"), states.last().lastValue)
        assertEquals(errorMessage, states.last().error)
        assertFalse(states.last().loading)
    }

    @Test
    fun `initial error remains distinguishable from a legitimate empty value`() = runTest {
        assertTrue(RetainedLoad<List<String>>().loading)
        val failed =
            flow<List<String>> { throw IOException("initial") }
                .asRetainedLoad(errorMessage)
                .toList()
                .last()
        val empty = flowOf(emptyList<String>()).asRetainedLoad(errorMessage).toList().last()

        assertNull(failed.lastValue)
        assertNotNull(failed.error)
        assertEquals(emptyList<String>(), empty.lastValue)
        assertNull(empty.error)
    }
}
