package me.chrr.camerapture;

import me.chrr.camerapture.client.compat.distantdecorations.CameraptureDistantDecorationRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DistantDecorationCompatTest {

    @Test
    public void testFarLodCullingThresholdMatchesMaxScale() {
        CameraptureDistantDecorationRenderer renderer = new CameraptureDistantDecorationRenderer();

        assertEquals(0.01, renderer.cullBelowProjectedPixelSize(), 1e-6);
        assertEquals(0.01, CameraptureDistantDecorationRenderer.FAR_LOD_CULL_PIXELS, 1e-6);
        assertEquals(1.0, CameraptureDistantDecorationRenderer.FAR_LOD_TARGET_PIXELS, 1e-6);
        assertEquals(16.0, CameraptureDistantDecorationRenderer.FAR_LOD_MAX_SCALE, 1e-6);

        // Assert that the culling threshold allows far-LOD scaling up to the declared maximum scale
        double requiredThresholdForMaxScale = CameraptureDistantDecorationRenderer.FAR_LOD_TARGET_PIXELS / CameraptureDistantDecorationRenderer.FAR_LOD_MAX_SCALE;
        assertTrue(renderer.cullBelowProjectedPixelSize() <= requiredThresholdForMaxScale,
                "Culling threshold (" + renderer.cullBelowProjectedPixelSize() + " px) must be <= " +
                requiredThresholdForMaxScale + " px to permit 16x Far-LOD scaling");
    }
}
