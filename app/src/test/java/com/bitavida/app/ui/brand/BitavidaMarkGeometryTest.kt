package com.bitavida.app.ui.brand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BitavidaMarkGeometryTest {

    @Test
    fun officialMark_hasExactlyThreeDistinctEventNodes() {
        val nodes = BitavidaMarkGeometry.eventNodes

        assertEquals(3, nodes.size)
        assertEquals(3, nodes.distinct().size)
    }

    @Test
    fun officialMark_keepsEveryEventNodeInsideNormalizedViewport() {
        val viewport = BitavidaMarkGeometry.VIEWPORT

        BitavidaMarkGeometry.eventNodes.forEach { node ->
            assertTrue(node.x >= 0f && node.x <= viewport)
            assertTrue(node.y >= 0f && node.y <= viewport)
        }
    }
}
