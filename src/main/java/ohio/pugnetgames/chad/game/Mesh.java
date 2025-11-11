package ohio.pugnetgames.chad.game;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;

/**
 * A simple class to hold 3D model data (vertices, normals, texture coordinates)
 * and render it. This is a very basic implementation.
 */
public class Mesh {

    private final int vboId; // Vertex Buffer Object
    private final int tboId; // Texture Coords Buffer Object
    private final int nboId; // Normals Buffer Object
    private final int eboId; // Element Buffer Object (Indices)
    private final int vertexCount;

    public Mesh(float[] vertices, float[] normals, float[] texCoords, int[] indices) {
        // We get the count from indices, as that's what we draw
        this.vertexCount = indices.length;

        // --- 1. Vertex Buffer Object (VBO) for Vertices ---
        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, createFloatBuffer(vertices), GL_STATIC_DRAW);

        // --- 2. VBO for Texture Coords ---
        tboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, tboId);
        glBufferData(GL_ARRAY_BUFFER, createFloatBuffer(texCoords), GL_STATIC_DRAW);

        // --- 3. VBO for Normals ---
        nboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, nboId);
        glBufferData(GL_ARRAY_BUFFER, createFloatBuffer(normals), GL_STATIC_DRAW);

        // --- 4. Element Buffer Object (EBO) for Indices ---
        eboId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, createIntBuffer(indices), GL_STATIC_DRAW);

        // Unbind the buffer
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    /**
     * Renders the mesh. Assumes GL_TEXTURE_2D is already enabled
     * and the correct texture is bound.
     */
    public void render() {
        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        glEnableClientState(GL_NORMAL_ARRAY);

        // Point to vertex buffer
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glVertexPointer(3, GL_FLOAT, 0, 0);

        // Point to texture coordinate buffer
        glBindBuffer(GL_ARRAY_BUFFER, tboId);
        glTexCoordPointer(2, GL_FLOAT, 0, 0);

        // Point to normal buffer
        glBindBuffer(GL_ARRAY_BUFFER, nboId);
        glNormalPointer(GL_FLOAT, 0, 0);

        // Point to element (index) buffer
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);

        // Draw the elements
        glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_INT, 0);

        // Unbind buffers
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);

        glDisableClientState(GL_VERTEX_ARRAY);
        glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        glDisableClientState(GL_NORMAL_ARRAY);
    }

    /**
     * Cleans up the GPU memory
     */
    public void cleanup() {
        glDeleteBuffers(vboId);
        glDeleteBuffers(tboId);
        glDeleteBuffers(nboId);
        glDeleteBuffers(eboId);
    }

    // --- Buffer utilities ---
    private FloatBuffer createFloatBuffer(float[] data) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(data.length);
        buffer.put(data);
        buffer.flip();
        return buffer;
    }

    private IntBuffer createIntBuffer(int[] data) {
        IntBuffer buffer = BufferUtils.createIntBuffer(data.length);
        buffer.put(data);
        buffer.flip();
        return buffer;
    }
}