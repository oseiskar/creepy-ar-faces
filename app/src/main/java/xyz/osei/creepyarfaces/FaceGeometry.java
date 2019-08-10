package xyz.osei.creepyarfaces;

import android.content.Context;
import android.opengl.GLES20;

import com.google.ar.core.AugmentedFace;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class FaceGeometry {
    private static final String TAG = FaceGeometry.class.getSimpleName();

    private static final int COORDS_PER_VERTEX = 3;

    // Object vertex buffer variables.
    private int vertexBufferId;
    private int verticesBaseAddress;
    private int texCoordsBaseAddress;
    private int normalsBaseAddress;
    private int indexBufferId;
    private int indexCount;

    private boolean objectLoaded = false;

    public void createOnGlThread() {
        int[] buffers = new int[2];
        GLES20.glGenBuffers(2, buffers, 0);
        vertexBufferId = buffers[0];
        indexBufferId = buffers[1];
        ShaderUtil.checkGLError(TAG, "createOnGlThread");
    }

    public void setToAugmentedFace(AugmentedFace face) {
        // Obtain the data from the OBJ, as direct buffers:
        FloatBuffer vertices = face.getMeshVertices();
        FloatBuffer texCoords = face.getMeshTextureCoordinates();
        FloatBuffer normals = face.getMeshNormals();

        // Convert int indices to shorts for GL ES 2.0 compatibility
        ShortBuffer indices = face.getMeshTriangleIndices();

        // Load vertex buffer
        verticesBaseAddress = 0;
        texCoordsBaseAddress = verticesBaseAddress + 4 * vertices.limit();
        normalsBaseAddress = texCoordsBaseAddress + 4 * texCoords.limit();
        final int totalBytes = normalsBaseAddress + 4 * normals.limit();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, totalBytes, null, GLES20.GL_STATIC_DRAW);
        GLES20.glBufferSubData(
                GLES20.GL_ARRAY_BUFFER, verticesBaseAddress, 4 * vertices.limit(), vertices);
        GLES20.glBufferSubData(
                GLES20.GL_ARRAY_BUFFER, texCoordsBaseAddress, 4 * texCoords.limit(), texCoords);
        GLES20.glBufferSubData(
                GLES20.GL_ARRAY_BUFFER, normalsBaseAddress, 4 * normals.limit(), normals);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        // Load index buffer
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId);
        indexCount = indices.limit();
        GLES20.glBufferData(
                GLES20.GL_ELEMENT_ARRAY_BUFFER, 2 * indexCount, indices, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        ShaderUtil.checkGLError(TAG, "setToAugmentedFace");

        objectLoaded = true;
    }

    public boolean isReady() {
        return objectLoaded;
    }

    public void bindGeometryBuffers(int positionAttribute, int texCoordAttribute, int normalAttribute) {
        if (!objectLoaded) return;
        final boolean hasNormals = normalAttribute > 0;

        // Set the vertex attributes.
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId);

        GLES20.glVertexAttribPointer(
                positionAttribute, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, verticesBaseAddress);
        if (hasNormals) {
            GLES20.glVertexAttribPointer(normalAttribute, 3, GLES20.GL_FLOAT, false, 0, normalsBaseAddress);
        }
        GLES20.glVertexAttribPointer(
                texCoordAttribute, 2, GLES20.GL_FLOAT, false, 0, texCoordsBaseAddress);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(positionAttribute);
        if (hasNormals) GLES20.glEnableVertexAttribArray(normalAttribute);
        GLES20.glEnableVertexAttribArray(texCoordAttribute);

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId);

        ShaderUtil.checkGLError(TAG, "bindGeometryBuffers");
    }

    public void drawElements() {
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, 0);
        ShaderUtil.checkGLError(TAG, "drawElements");
    }

    public void unbindGeometryBuffers(int positionAttribute, int texCoordAttribute, int normalAttribute) {
        final boolean hasNormals = normalAttribute > 0;

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(positionAttribute);
        if (hasNormals) GLES20.glDisableVertexAttribArray(normalAttribute);
        GLES20.glDisableVertexAttribArray(texCoordAttribute);
        ShaderUtil.checkGLError(TAG, "unbindGeometryBuffers");
    }
}
