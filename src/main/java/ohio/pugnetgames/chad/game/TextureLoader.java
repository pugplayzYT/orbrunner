package ohio.pugnetgames.chad.game;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
// NEW: Imports for file handling
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Utility class to load image files from resources using STB Image and create
 * an OpenGL texture ID.
 */
public class TextureLoader {

    /**
     * Loads a texture from the resources folder.
     * @param fileName The name of the texture file (e.g., "orb_texture.png").
     * @return The OpenGL texture ID, or 0 if loading failed.
     */
    public static int loadTexture(String fileName) {
        ByteBuffer imageBuffer;
        try {
            // MODIFIED: Use the same robust loadResource method as FontRenderer
            imageBuffer = loadResource(fileName, 512 * 1024); // 512KB buffer
        } catch (IOException e) {
            System.err.println("Could not load texture file: " + fileName);
            e.printStackTrace();
            return 0;
        }

        int width, height, comp;
        ByteBuffer imageData;

        // Use MemoryStack for temporary buffers
        try (var stack = stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer c = stack.mallocInt(1);

            // Decode the image
            imageData = STBImage.stbi_load_from_memory(imageBuffer, w, h, c, 4); // Force 4 channels (RGBA)
            if (imageData == null) {
                System.err.println("Failed to load texture data: " + STBImage.stbi_failure_reason());
                return 0;
            }

            width = w.get(0);
            height = h.get(0);
            comp = c.get(0);

            // Create OpenGL Texture
            int textureID = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, textureID);

            // Set texture parameters
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

            // Upload the image data to the texture
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, imageData);

            // Free the image data buffer
            STBImage.stbi_image_free(imageData);
            System.out.println("Texture loaded successfully: " + fileName);
            return textureID;
        }
    }

    /**
     * Utility to load a resource file into a ByteBuffer.
     * MODIFIED: Copied from FontRenderer to robustly handle JAR paths.
     */
    private static ByteBuffer loadResource(String resource, int bufferSize) throws IOException {
        ByteBuffer buffer;
        Path path = Paths.get(resource);

        // Try loading from file system first
        if (Files.exists(path)) {
            try (ReadableByteChannel rbc = Files.newByteChannel(path)) {
                buffer = BufferUtils.createByteBuffer((int) Files.size(path));
                while (rbc.read(buffer) != -1) {
                    // read
                }
            }
        } else {
            // Fallback for resources in JAR
            try (InputStream source = TextureLoader.class.getClassLoader().getResourceAsStream(resource);
                 ReadableByteChannel rbc = Channels.newChannel(source)) {

                if (source == null) {
                    throw new IOException("Resource not found: " + resource);
                }

                buffer = BufferUtils.createByteBuffer(bufferSize);
                while (true) {
                    int bytes = rbc.read(buffer);
                    if (bytes == -1) {
                        break;
                    }
                    if (buffer.remaining() == 0) {
                        buffer = resizeBuffer(buffer, buffer.capacity() * 2);
                    }
                }
            }
        }

        buffer.flip();
        return buffer;
    }

    private static ByteBuffer resizeBuffer(ByteBuffer buffer, int newCapacity) {
        ByteBuffer newBuffer = BufferUtils.createByteBuffer(newCapacity);
        buffer.flip();
        newBuffer.put(buffer);
        return newBuffer;
    }
}