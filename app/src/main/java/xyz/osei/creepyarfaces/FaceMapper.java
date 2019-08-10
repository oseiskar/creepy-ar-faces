package xyz.osei.creepyarfaces;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import com.google.ar.core.Pose;

import java.io.IOException;
import java.nio.ByteBuffer;

public class FaceMapper {
    private static final String TAG = FaceMapper.class.getSimpleName();

    // Shader names.
    private static final String VERTEX_SHADER_NAME = "shaders/uv.vert";
    private static final String FRAGMENT_SHADER_NAME = "shaders/uv.frag";

    private static final int FACE_TEXTURE_W = 256;
    private static final int FACE_TEXTURE_H = 256;

    private int program;
    private final int[] textures = new int[1];

    // Shader location: model view projection matrix.
    //private int modelViewUniform;
    private int modelViewProjectionUniform;

    // Shader location: object attributes.
    private int positionAttribute;
    private int texCoordAttribute;

    // Temporary matrices allocated here to reduce number of allocations for each frame.
    private final float[] modelMatrix = new float[16];
    private final float[] modelViewMatrix = new float[16];
    private final float[] modelViewProjectionMatrix = new float[16];

    private byte[] faceBuffer, videoBytes, uvBytes;
    private ByteBuffer videoBuffer, uvBuffer, faceTexture;
    private int width, height;

    private final FaceGeometry faceGeometry;

    FaceMapper(FaceGeometry geometry) {
        faceGeometry = geometry;
    }

    public void createOnGlThread(Context context)
            throws IOException {
        final int vertexShader =
                ShaderUtil.loadGLShader(TAG, context, GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_NAME);
        final int fragmentShader =
                ShaderUtil.loadGLShader(TAG, context, GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_NAME);

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);
        GLES20.glUseProgram(program);
        ShaderUtil.checkGLError(TAG, "Program creation");

        modelViewProjectionUniform = GLES20.glGetUniformLocation(program, "u_ModelViewProjection");

        positionAttribute = GLES20.glGetAttribLocation(program, "a_Position");
        texCoordAttribute = GLES20.glGetAttribLocation(program, "a_TexCoord");

        ShaderUtil.checkGLError(TAG, "Program parameters");

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glGenTextures(textures.length, textures, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);

        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        faceTexture = ByteBuffer.allocateDirect(FACE_TEXTURE_W * FACE_TEXTURE_H * 4);
        faceBuffer = new byte[FACE_TEXTURE_W * FACE_TEXTURE_H * 4];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        Matrix.setIdentityM(modelMatrix, 0);
        ShaderUtil.checkGLError(TAG, "end FaceRenderer.createOnGlThread");
    }

    public void setDimensions(int w, int h) {
        width = w;
        height = h;
    }

    public void updateModelMatrix(Pose pose) {
        pose.toMatrix(this.modelMatrix, 0);
    }

    public int getFaceTextureId() {
        return textures[0];
    }

    public void draw(
            float[] cameraView,
            float[] cameraPerspective) {

        if (!faceGeometry.isReady()) return;

        int readSize = width * height * 4;
        if (readSize <= 0) return;
        if (videoBuffer == null || videoBuffer.limit() != readSize) {
            Log.d(TAG, "allocating pixel buffers of size " + readSize);
            videoBuffer = ByteBuffer.allocateDirect(readSize);
            uvBuffer = ByteBuffer.allocateDirect(readSize);
            videoBytes = new byte[readSize];
            uvBytes = new byte[readSize];
        }

        videoBuffer.rewind();
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, videoBuffer);

        ShaderUtil.checkGLError(TAG, "Before draw");

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        // Build the ModelView and ModelViewProjection matrices
        // for calculating object position and light.
        Matrix.multiplyMM(modelViewMatrix, 0, cameraView, 0, modelMatrix, 0);
        Matrix.multiplyMM(modelViewProjectionMatrix, 0, cameraPerspective, 0, modelViewMatrix, 0);

        GLES20.glUseProgram(program);
        ShaderUtil.checkGLError(TAG, "After glUseProgram");


        GLES20.glCullFace(GLES20.GL_FRONT);
        GLES20.glEnable(GLES20.GL_CULL_FACE);

        faceGeometry.bindGeometryBuffers(positionAttribute, texCoordAttribute, -1);


        ShaderUtil.checkGLError(TAG, "After glBindBuffers");

        // Set the ModelViewProjection matrix in the shader.
        //GLES20.glUniformMatrix4fv(modelViewUniform, 1, false, modelViewMatrix, 0);
        GLES20.glUniformMatrix4fv(modelViewProjectionUniform, 1, false, modelViewProjectionMatrix, 0);

        //GLES20.glDepthMask(false);
        GLES20.glEnable(GLES20.GL_BLEND);
        // Grid, additive blending function.
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        faceGeometry.drawElements();

        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_CULL_FACE);

        faceGeometry.unbindGeometryBuffers(positionAttribute, texCoordAttribute, -1);

        uvBuffer.rewind();
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, uvBuffer);

        updateFaceTexture();

        ShaderUtil.checkGLError(TAG, "After draw");
    }

    private void updateFaceTexture() {
        uvBuffer.rewind();
        uvBuffer.get(uvBytes, 0, uvBytes.length);
        videoBuffer.rewind();
        videoBuffer.get(videoBytes, 0, videoBytes.length);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int srcIdx = y*width+x;
                byte a = uvBytes[srcIdx*4+2]; // channel b should equal alpha
                if (a == 0) { // why not != 0?
                    byte u = uvBytes[srcIdx*4];
                    byte v = uvBytes[srcIdx*4 + 1];
                    int faceX = (int)(((u & 0xff) / 255.0) * FACE_TEXTURE_W);
                    int faceY = (int)(((v & 0xff) / 255.0) * FACE_TEXTURE_H);
                    if (faceX >= 0 && faceX < FACE_TEXTURE_W && faceY >= 0 && faceY < FACE_TEXTURE_H) {
                        int idx = faceY*FACE_TEXTURE_W + faceX;
                        faceBuffer[idx*4] = videoBytes[srcIdx*4];
                        faceBuffer[idx*4+1] = videoBytes[srcIdx*4+1];
                        faceBuffer[idx*4+2] = videoBytes[srcIdx*4+2];
                        faceBuffer[idx*4+3] = (byte)255;
                    }
                }
            }
        }
        faceTexture.rewind();
        faceTexture.put(faceBuffer);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, FACE_TEXTURE_W, FACE_TEXTURE_H, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, faceTexture);
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }
}
