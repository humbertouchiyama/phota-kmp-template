package com.humbertouchiyama.phota

import app.cash.turbine.test
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SmokeTest {
    @Test
    fun flowEmitsThenCompletes() = runTest {
        flowOf(1).test {
            assertEquals(1, awaitItem())
            awaitComplete()
        }
    }
}
