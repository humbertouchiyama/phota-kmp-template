package com.humbertouchiyama.phota

import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenSaverTest {

    @Test
    fun roundTrips() {
        listOf(Screen.Gallery, Screen.Detail("a"), Screen.Detail("a:b")).forEach {
            assertEquals(it, decodeScreen(it.encode()))
        }
    }

    @Test
    fun garbageGivesGallery() {
        assertEquals(Screen.Gallery, decodeScreen("???"))
        assertEquals(Screen.Gallery, decodeScreen(""))
    }
}
