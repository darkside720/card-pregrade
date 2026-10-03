package com.cardpregrade.core.cv.fixtures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ArgbRasterTest {

    @Test
    fun `pixels are row major and default to zero`() {
        val raster = ArgbRaster(3, 2)
        assertEquals(6, raster.pixels.size)
        raster[2, 1] = 0xFF112233.toInt()
        assertEquals(0xFF112233.toInt(), raster.pixels[1 * 3 + 2])
        assertEquals(0xFF112233.toInt(), raster[2, 1])
        assertEquals(0, raster[0, 0])
    }

    @Test
    fun `accepts a supplied pixel array of the right size`() {
        val raster = ArgbRaster(2, 2, intArrayOf(1, 2, 3, 4))
        assertEquals(3, raster[0, 1])
    }

    @Test
    fun `rejects invalid dimensions and mismatched pixel arrays`() {
        assertThrows(IllegalArgumentException::class.java) { ArgbRaster(0, 5) }
        assertThrows(IllegalArgumentException::class.java) { ArgbRaster(5, -1) }
        assertThrows(IllegalArgumentException::class.java) { ArgbRaster(2, 2, IntArray(3)) }
        assertThrows(IllegalArgumentException::class.java) { ArgbRaster(2, 2, IntArray(5)) }
    }

    @Test
    fun `pixel count is overflow safe and capped`() {
        assertEquals(16_777_216, ArgbRaster.checkedPixelCount(4096, 4096))
        // 65536² overflows Int to 0; it must be rejected rather than allocating an empty array.
        assertThrows(IllegalArgumentException::class.java) { ArgbRaster.checkedPixelCount(65_536, 65_536) }
        assertThrows(IllegalArgumentException::class.java) { ArgbRaster(65_536, 65_536) }
        assertThrows(IllegalArgumentException::class.java) { ArgbRaster(4097, 4096) }
        assertThrows(IllegalArgumentException::class.java) { ArgbRaster(Int.MAX_VALUE, 2) }
    }

    @Test
    fun `access outside the raster throws instead of wrapping to another row`() {
        val raster = ArgbRaster(3, 2)
        assertThrows(IndexOutOfBoundsException::class.java) { raster[3, 0] }
        assertThrows(IndexOutOfBoundsException::class.java) { raster[-1, 1] }
        assertThrows(IndexOutOfBoundsException::class.java) { raster[0, 2] = 1 }
    }

    @Test
    fun `converts to BT601 grey`() {
        val raster = ArgbRaster(4, 1, intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFFFFFF.toInt()))
        val grey = raster.toGrayImage()
        assertEquals(4, grey.width)
        assertEquals(1, grey.height)
        // (299·R + 587·G + 114·B) / 1000, integer division.
        assertEquals(76, grey[0, 0])
        assertEquals(149, grey[1, 0])
        assertEquals(29, grey[2, 0])
        assertEquals(255, grey[3, 0])
    }
}
