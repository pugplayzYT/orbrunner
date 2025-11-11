package ohio.pugnetgames.chad.game;

import org.lwjgl.assimp.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.assimp.Assimp.*;

/**
 * Loads a 3D model file (like .obj) from the resources folder
 * using LWJGL's Assimp bindings.
 */
public class ModelLoader {

    public static Mesh loadModel(String resourcePath) {
        File modelFile = getFileFromResources(resourcePath);
        if (modelFile == null) {
            throw new RuntimeException("Could not find model file in resources: " + resourcePath);
        }

        // aiProcess_Triangulate: Guarantees all faces are triangles
        // aiProcess_JoinIdenticalVertices: Saves memory
        // aiProcess_GenSmoothNormals: Generates normals if the model is missing them
        // aiProcess_FlipUVs: Flips texture coordinates (often needed for OpenGL)
        int flags = aiProcess_Triangulate | aiProcess_JoinIdenticalVertices | aiProcess_GenSmoothNormals | aiProcess_FlipUVs;

        AIScene scene = aiImportFile(modelFile.getAbsolutePath(), flags);

        if (scene == null || (scene.mFlags() & AI_SCENE_FLAGS_INCOMPLETE) != 0 || scene.mRootNode() == null) {
            throw new RuntimeException("Error loading model: " + aiGetErrorString());
        }

        // For this simple game, we only load the *first* mesh in the file.
        if (scene.mNumMeshes() == 0) {
            throw new RuntimeException("Model file has no meshes!");
        }

        // Get the first mesh
        AIMesh aiMesh = AIMesh.create(scene.mMeshes().get(0));

        // !!!!!!!!!!!!!! THE FIX !!!!!!!!!!!!!!
        // Pass the 'scene' object to processMesh so it can be released
        return processMesh(aiMesh, scene);
    }

    // !!!!!!!!!!!!!! THE FIX !!!!!!!!!!!!!!
    // Added AIScene to the method signature
    private static Mesh processMesh(AIMesh aiMesh, AIScene scene) {
        List<Float> vertices = new ArrayList<>();
        List<Float> normals = new ArrayList<>();
        List<Float> texCoords = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        // --- Process Vertices, Normals, and Tex Coords ---
        AIVector3D.Buffer aiVertices = aiMesh.mVertices();
        AIVector3D.Buffer aiNormals = aiMesh.mNormals();
        AIVector3D.Buffer aiTexCoords = aiMesh.mTextureCoords(0); // Get first texture channel

        for (int i = 0; i < aiMesh.mNumVertices(); i++) {
            AIVector3D vertex = aiVertices.get(i);
            vertices.add(vertex.x());
            vertices.add(vertex.y());
            vertices.add(vertex.z());

            if (aiNormals != null) {
                AIVector3D normal = aiNormals.get(i);
                normals.add(normal.x());
                normals.add(normal.y());
                normals.add(normal.z());
            }

            if (aiTexCoords != null) {
                AIVector3D texCoord = aiTexCoords.get(i);
                texCoords.add(texCoord.x());
                texCoords.add(texCoord.y());
            } else {
                texCoords.add(0.0f);
                texCoords.add(0.0f);
            }
        }

        // --- Process Indices (Faces) ---
        AIFace.Buffer aiFaces = aiMesh.mFaces();
        for (int i = 0; i < aiMesh.mNumFaces(); i++) {
            AIFace face = aiFaces.get(i);
            // We're triangulating, so mNumIndices should always be 3
            if (face.mNumIndices() != 3) {
                System.err.println("Warning: Mesh face not a triangle!");
                continue;
            }
            for (int j = 0; j < face.mNumIndices(); j++) {
                indices.add(face.mIndices().get(j));
            }
        }

        // --- Convert Lists to Arrays ---
        float[] verticesArr = toFloatArray(vertices);
        float[] normalsArr = toFloatArray(normals);
        float[] texCoordsArr = toFloatArray(texCoords);
        int[] indicesArr = toIntArray(indices);

        // We're done with the scene, release it
        // !!!!!!!!!!!!!! THE FIX !!!!!!!!!!!!!!
        // Call aiReleaseImport on the 'scene' object, not 'aiMesh'
        aiReleaseImport(scene);

        return new Mesh(verticesArr, normalsArr, texCoordsArr, indicesArr);
    }

    // --- List to Array Helpers ---
    private static float[] toFloatArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i< list.size(); i++) arr[i] = list.get(i);
        return arr;
    }
    private static int[] toIntArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i< list.size(); i++) arr[i] = list.get(i);
        return arr;
    }

    /**
     * Gets a file from the resources folder.
     * This is tricky because when in a JAR, it's an InputStream.
     * We must extract it to a temp file for Assimp to read.
     */
    private static File getFileFromResources(String fileName) {
        try {
            InputStream in = ModelLoader.class.getClassLoader().getResourceAsStream(fileName);
            if (in == null) {
                throw new IOException("Resource not found: " + fileName);
            }
            // Create a temp file and copy the stream to it
            File tempFile = File.createTempFile("model", fileName.substring(fileName.lastIndexOf('.')));
            tempFile.deleteOnExit();
            Files.copy(in, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return tempFile;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}