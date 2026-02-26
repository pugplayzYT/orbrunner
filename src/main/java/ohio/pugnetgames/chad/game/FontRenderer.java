package ohio.pugnetgames.chad.game;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTBakedChar;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.stb.STBTruetype.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Handles loading and rendering text using STB TrueType.
 * This bakes a font into a texture atlas and renders text as textured quads.
 */
public class FontRenderer {

    private int textureID;
    private STBTTBakedChar.Buffer charData;
    private final float FONT_HEIGHT = 32.0f; // The pixel height of the font
    private final int BITMAP_WIDTH = 1024;
    private final int BITMAP_HEIGHT = 1024;

    /**
     * Initializes the font renderer.
     * 
     * @param fontPath The path to the .ttf font file.
     */
    public void init(String fontPath) {
        textureID = glGenTextures();
        charData = STBTTBakedChar.malloc(96); // 96 characters (ASCII 32-127)

        try {
            // Load the font file into a ByteBuffer
            ByteBuffer ttfBuffer = loadFontFile(fontPath);

            ByteBuffer bitmap = BufferUtils.createByteBuffer(BITMAP_WIDTH * BITMAP_HEIGHT);

            // Bake the font characters into the bitmap
            int result = stbtt_BakeFontBitmap(ttfBuffer, FONT_HEIGHT, bitmap, BITMAP_WIDTH, BITMAP_HEIGHT, 32,
                    charData);
            if (result <= 0) {
                System.err.println("Failed to bake font bitmap. Result: " + result);
                return;
            }

            // Upload the bitmap to an OpenGL texture
            glBindTexture(GL_TEXTURE_2D, textureID);
            // Use GL_ALPHA for the single-channel bitmap
            glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, BITMAP_WIDTH, BITMAP_HEIGHT, 0, GL_ALPHA, GL_UNSIGNED_BYTE,
                    bitmap);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

            System.out.println("FontRenderer initialized successfully.");

        } catch (IOException e) {
            System.err.println("Failed to load font file: " + fontPath);
            e.printStackTrace();
        }
    }

    /**
     * Draws a string of text on the screen.
     * Assumes an orthographic projection is already set up (0,0 is top-left).
     */
    public void drawText(String text, float x, float y, float r, float g, float b) {
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, textureID);

        // --- FIX: Explicitly set the texture environment mode to GL_MODULATE.
        // This is crucial for single-channel (GL_ALPHA) font textures to ensure
        // the color set by glColor4f is modulated by the texture's alpha channel.
        glTexEnvi(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_MODULATE);

        // We modulate the texture's alpha (the letter shape) with the desired color.
        glColor4f(r, g, b, 1.0f);

        glBegin(GL_QUADS);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer xPos = stack.floats(x);
            FloatBuffer yPos = stack.floats(y + FONT_HEIGHT / 2); // Adjust Y for font baseline

            STBTTAlignedQuad quad = STBTTAlignedQuad.malloc(stack);

            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c < 32 || c >= 128) {
                    continue; // Skip characters not in our baked range
                }

                // Get the quad data for this character
                stbtt_GetBakedQuad(charData, BITMAP_WIDTH, BITMAP_HEIGHT, c - 32, xPos, yPos, quad, true);

                // Render the quad
                glTexCoord2f(quad.s0(), quad.t0());
                glVertex2f(quad.x0(), quad.y0());

                glTexCoord2f(quad.s1(), quad.t0());
                glVertex2f(quad.x1(), quad.y0());

                glTexCoord2f(quad.s1(), quad.t1());
                glVertex2f(quad.x1(), quad.y1());

                glTexCoord2f(quad.s0(), quad.t1());
                glVertex2f(quad.x0(), quad.y1());
            }
        }
        glEnd();

        glDisable(GL_TEXTURE_2D);
    }

    /**
     * Measures the pixel width of a string without rendering it.
     * Uses the same baked character data as drawText.
     */
    public float getTextWidth(String text) {
        if (charData == null || text == null)
            return 0;
        float width = 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer xPos = stack.floats(0);
            FloatBuffer yPos = stack.floats(0);
            STBTTAlignedQuad quad = STBTTAlignedQuad.malloc(stack);

            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c < 32 || c >= 128)
                    continue;
                stbtt_GetBakedQuad(charData, BITMAP_WIDTH, BITMAP_HEIGHT, c - 32, xPos, yPos, quad, true);
            }
            width = xPos.get(0);
        }
        return width;
    }

    /**
     * Cleans up the OpenGL texture.
     */
    public void cleanup() {
        glDeleteTextures(textureID);
        if (charData != null) {
            charData.free();
        }
    }

    /**
     * Utility function to load a file into a ByteBuffer.
     */
    private ByteBuffer loadFontFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            // Fallback for resources in JAR
            InputStream stream = FontRenderer.class.getClassLoader().getResourceAsStream(filePath);
            if (stream == null) {
                throw new IOException("Cannot find font file: " + filePath);
            }
            try (ReadableByteChannel rbc = Channels.newChannel(stream)) {
                ByteBuffer buffer = BufferUtils.createByteBuffer(1024 * 1024); // 1MB buffer
                while (rbc.read(buffer) != -1) {
                    // read
                }
                buffer.flip();
                return buffer;
            }
        }

        // Standard file read
        try (ReadableByteChannel rbc = Files.newByteChannel(path)) {
            ByteBuffer buffer = BufferUtils.createByteBuffer((int) Files.size(path));
            while (rbc.read(buffer) != -1) {
                // read
            }
            buffer.flip();
            return buffer;
        }
    }
}