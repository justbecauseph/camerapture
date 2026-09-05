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

        // 1. Assert opaque base pass always exists (depth-tested entityCutoutCull)
        assertTrue(renderPictureBody.contains("RenderTypes.entityCutoutCull"),
                "renderPicture must always submit an opaque base pass with entityCutoutCull");

        // 2. Assert glowing pictures no longer use RenderTypes.text as their sole render path
        assertFalse(renderPictureBody.contains("RenderTypes.text"),
                "renderPicture must not select RenderTypes.text for glowing pictures");

        // 3. Assert glowing has a separate emissive pass using an emissive render type (eyes)
        assertTrue(renderPictureBody.contains("RenderTypes.eyes"),
                "renderPicture must submit a second emissive overlay pass using RenderTypes.eyes for glowing pictures");
        assertTrue(renderPictureBody.contains("if (isGlowing)"),
                "renderPicture must guard the emissive overlay pass with isGlowing");
        assertTrue(renderPictureBody.contains("0x00F000F0"),
                "renderPicture must use fullbright light (0x00F000F0) for emissive rendering");
    }
}
