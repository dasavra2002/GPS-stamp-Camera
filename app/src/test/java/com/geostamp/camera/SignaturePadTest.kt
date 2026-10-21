package com.geostamp.camera

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.geostamp.camera.ui.SignaturePadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignaturePadTest {

    @Test
    fun testEmptyPadState() {
        val pad = SignaturePadState()
        assertFalse(pad.hasSignature)
        assertEquals(0, pad.strokes.size)
    }

    @Test
    fun testDrawingStrokeAndUndo() {
        val pad = SignaturePadState()
        pad.selectedColor = Color(0xFF0A2540)
        pad.strokeWidthPx = 4f

        pad.addPoint(Offset(10f, 10f))
        pad.addPoint(Offset(20f, 20f))
        pad.addPoint(Offset(30f, 40f))
        pad.finishStroke()

        assertTrue(pad.hasSignature)
        assertEquals(1, pad.strokes.size)
        assertEquals(3, pad.strokes[0].points.size)
        assertEquals(Color(0xFF0A2540), pad.strokes[0].color)

        // Undo
        pad.undo()
        assertFalse(pad.hasSignature)
        assertEquals(0, pad.strokes.size)
    }

    @Test
    fun testMultipleStrokesAndClear() {
        val pad = SignaturePadState()

        // First stroke
        pad.addPoint(Offset(5f, 5f))
        pad.addPoint(Offset(15f, 15f))
        pad.finishStroke()

        // Second stroke
        pad.addPoint(Offset(50f, 50f))
        pad.addPoint(Offset(80f, 90f))
        pad.finishStroke()

        assertEquals(2, pad.strokes.size)
        assertTrue(pad.hasSignature)

        // Clear all
        pad.clear()
        assertFalse(pad.hasSignature)
        assertEquals(0, pad.strokes.size)
    }
}
