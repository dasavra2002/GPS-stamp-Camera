package com.geostamp.camera

import androidx.camera.core.ImageCapture
import com.geostamp.camera.data.GridType
import com.geostamp.camera.data.SlowMoSpeed
import com.geostamp.camera.data.StampPosition
import com.geostamp.camera.ui.CameraCycles
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraCyclesTest {

    private fun <T> cycle(start: T, next: (T) -> T): List<T> {
        val seen = mutableListOf(start)
        var v = next(start)
        while (v != start && seen.size < 20) {
            seen += v
            v = next(v)
        }
        return seen
    }

    @Test
    fun flashCyclesOffAutoOn() {
        assertEquals(
            listOf(ImageCapture.FLASH_MODE_OFF, ImageCapture.FLASH_MODE_AUTO, ImageCapture.FLASH_MODE_ON),
            cycle(ImageCapture.FLASH_MODE_OFF, CameraCycles::nextFlash),
        )
    }

    @Test
    fun gridVisitsEveryType() {
        assertEquals(listOf(GridType.OFF, GridType.THIRDS, GridType.CROSSHAIR, GridType.BOTH), cycle(GridType.OFF, CameraCycles::nextGrid))
    }

    @Test
    fun stampMovesBottomTopCenter() {
        assertEquals(
            listOf(StampPosition.BOTTOM, StampPosition.TOP, StampPosition.CENTER),
            cycle(StampPosition.BOTTOM, CameraCycles::nextStampPosition),
        )
    }

    @Test
    fun timerBurstAndIntervalSteps() {
        assertEquals(listOf(0, 3, 5, 10), cycle(0, CameraCycles::nextTimerSeconds))
        assertEquals(listOf(1, 3, 5), cycle(1, CameraCycles::nextBurstCount))
        assertEquals(listOf(1, 2, 5, 10, 30), cycle(1, CameraCycles::nextTimelapseInterval))
        assertEquals(listOf(SlowMoSpeed.X2, SlowMoSpeed.X4, SlowMoSpeed.X8), cycle(SlowMoSpeed.X2, CameraCycles::nextSlowMoSpeed))
    }

    @Test
    fun unknownValuesFallBackToTheFirstStep() {
        assertEquals(0, CameraCycles.nextTimerSeconds(7))
        assertEquals(1, CameraCycles.nextBurstCount(10))
        assertEquals(1, CameraCycles.nextTimelapseInterval(60))
    }
}
