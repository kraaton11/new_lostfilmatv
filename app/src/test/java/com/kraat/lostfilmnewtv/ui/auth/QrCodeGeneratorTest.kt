package com.kraat.lostfilmnewtv.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeGeneratorTest {

    @Test
    fun generateMatrix_returnsRequestedSize() {
        val matrix = QrCodeGenerator.generateMatrix(
            content = "https://example-phone.auth.example.test/",
            size = 256,
        )

        assertEquals(256, matrix.width)
        assertEquals(256, matrix.height)
        assertNotEquals(matrix.get(0, 0), matrix.get(128, 128))
    }

    @Test(expected = IllegalArgumentException::class)
    fun generateMatrix_rejectsBlankContent() {
        QrCodeGenerator.generateMatrix("   ", 256)
    }
}
