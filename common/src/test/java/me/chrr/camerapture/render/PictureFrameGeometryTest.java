package me.chrr.camerapture.render;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class PictureFrameGeometryTest {

    @Test
    public void testRenderPictureSourceStructure() throws Exception {
        Path path = Path.of("src/client/java/me/chrr/camerapture/render/PictureFrameGeometry.java");
        if (!Files.exists(path)) {
            path = Path.of("common/src/client/java/me/chrr/camerapture/render/PictureFrameGeometry.java");
        }
        assertTrue(Files.exists(path), "PictureFrameGeometry.java source file should exist at: " + path.toAbsolutePath());

        String source = Files.readString(path);
        
        // Find renderPicture method
        int methodIndex = source.indexOf("void renderPicture(");
        assertTrue(methodIndex >= 0, "renderPicture method should exist in PictureFrameGeometry");
        
        int nextMethodIndex = source.indexOf("void renderPlaceholderQuad(", methodIndex);
        assertTrue(nextMethodIndex > methodIndex, "renderPlaceholderQuad method should follow renderPicture");
        
        String renderPictureBody = source.substring(methodIndex, nextMethodIndex);

        // 1. Assert renderPicture does not select RenderTypes.text based on isGlowing
        assertFalse(renderPictureBody.contains("RenderTypes.text"),
                "renderPicture must not select RenderTypes.text for glowing pictures");

        // 2. Assert renderPicture uses RenderTypes.entityCutoutCull unconditionally
        assertTrue(renderPictureBody.contains("RenderTypes.entityCutoutCull"),
                "renderPicture must use entityCutoutCull");

        // 3. Assert fullbright light is still used when isGlowing
        assertTrue(renderPictureBody.contains("isGlowing ? 0x00F000F0 : lightCoords"),
                "renderPicture must use fullbright light 0x00F000F0 when isGlowing is true");
    }
}
