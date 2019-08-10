/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * --- Modified by oseiskar, 2019, see Git commit history
 */
package xyz.osei.creepyarfaces;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import com.google.ar.core.AugmentedFace;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/** Renders an object loaded from an OBJ file in OpenGL. */
public class ObjectRenderer {
  private static final String TAG = ObjectRenderer.class.getSimpleName();

  // Shader names.
  private static final String VERTEX_SHADER_NAME = "shaders/uv.vert";
  private static final String FRAGMENT_SHADER_NAME = "shaders/uv.frag";

  private static final int COORDS_PER_VERTEX = 3;
  private static final int FACE_TEXTURE_W = 256;
  private static final int FACE_TEXTURE_H = 256;

  // Object vertex buffer variables.
  private int vertexBufferId;
  private int verticesBaseAddress;
  private int texCoordsBaseAddress;
  private int normalsBaseAddress;
  private int indexBufferId;
  private int indexCount;

  private int program;
  private final int[] textures = new int[1];

  // Shader location: model view projection matrix.
  //private int modelViewUniform;
  private int modelViewProjectionUniform;

  // Shader location: object attributes.
  private int positionAttribute;
  //private int normalAttribute;
  private int texCoordAttribute;

  private int textureUniform;
  private int whichPassUniform;
  private static final int TEXCOORDS_PER_VERTEX = 2;
  private static final int QUAD_COORDS_PER_VERTEX = 2;
  private static final int FLOAT_SIZE = 4;

  // Temporary matrices allocated here to reduce number of allocations for each frame.
  private final float[] modelMatrix = new float[16];
  private final float[] modelViewMatrix = new float[16];
  private final float[] modelViewProjectionMatrix = new float[16];

  private byte[] faceBuffer, videoBytes, uvBytes;
  private ByteBuffer videoBuffer, uvBuffer, faceTexture;
  private int width, height;

  private boolean objectLoaded = false;

  public ObjectRenderer() {}

  /**
   * Creates and initializes OpenGL resources needed for rendering the model.
   *
   * @param context Context for loading the shader and below-named model and texture assets.
   */
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

    //modelViewUniform = GLES20.glGetUniformLocation(program, "u_ModelView");
    modelViewProjectionUniform = GLES20.glGetUniformLocation(program, "u_ModelViewProjection");

    positionAttribute = GLES20.glGetAttribLocation(program, "a_Position");
    //normalAttribute = GLES20.glGetAttribLocation(program, "a_Normal");
    texCoordAttribute = GLES20.glGetAttribLocation(program, "a_TexCoord");

    ShaderUtil.checkGLError(TAG, "Program parameters");

    textureUniform = GLES20.glGetUniformLocation(program, "u_Texture");
    whichPassUniform = GLES20.glGetUniformLocation(program, "u_WhichPass");

    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glGenTextures(textures.length, textures, 0);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);

    GLES20.glTexParameteri(
        GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

    //GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, textureBitmap, 0);

    faceTexture = ByteBuffer.allocateDirect(FACE_TEXTURE_W * FACE_TEXTURE_H * 4);
    faceBuffer = new byte[FACE_TEXTURE_W * FACE_TEXTURE_H * 4];

    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

    int[] buffers = new int[2];
    GLES20.glGenBuffers(2, buffers, 0);
    vertexBufferId = buffers[0];
    indexBufferId = buffers[1];

    Matrix.setIdentityM(modelMatrix, 0);
    ShaderUtil.checkGLError(TAG, "end ObjectRenderer.createOnGlThread");
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

    ShaderUtil.checkGLError(TAG, "OBJ buffer load");

    Matrix.setIdentityM(modelMatrix, 0);
    objectLoaded = true;
  }

  public void setDimensions(int w, int h) {
      width = w;
      height = h;
  }

  /**
   * Updates the object model matrix and applies scaling.
   *
   * @param modelMatrix A 4x4 model-to-world transformation matrix, stored in column-major order.
   * @param scaleFactor A separate scaling factor to apply before the {@code modelMatrix}.
   * @see android.opengl.Matrix
   */
  public void updateModelMatrix(float[] modelMatrix, float scaleFactor) {
    float[] scaleMatrix = new float[16];
    Matrix.setIdentityM(scaleMatrix, 0);
    scaleMatrix[0] = scaleFactor;
    scaleMatrix[5] = scaleFactor;
    scaleMatrix[10] = scaleFactor;
    Matrix.multiplyMM(this.modelMatrix, 0, modelMatrix, 0, scaleMatrix, 0);
  }

  public void draw(
      float[] cameraView,
      float[] cameraPerspective) {

    if (!objectLoaded) return;

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

    // Set the vertex attributes.
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId);


    GLES20.glVertexAttribPointer(
        positionAttribute, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, verticesBaseAddress);
    //GLES20.glVertexAttribPointer(normalAttribute, 3, GLES20.GL_FLOAT, false, 0, normalsBaseAddress);
    GLES20.glVertexAttribPointer(
        texCoordAttribute, 2, GLES20.GL_FLOAT, false, 0, texCoordsBaseAddress);

    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

    ShaderUtil.checkGLError(TAG, "After glBindBuffers");

    // Set the ModelViewProjection matrix in the shader.
    //GLES20.glUniformMatrix4fv(modelViewUniform, 1, false, modelViewMatrix, 0);
    GLES20.glUniformMatrix4fv(modelViewProjectionUniform, 1, false, modelViewProjectionMatrix, 0);
    GLES20.glUniform1f(whichPassUniform, 0f);
    GLES20.glUniform1i(textureUniform, 0);

    // Enable vertex arrays
    GLES20.glEnableVertexAttribArray(positionAttribute);
    //GLES20.glEnableVertexAttribArray(normalAttribute);
    GLES20.glEnableVertexAttribArray(texCoordAttribute);

    ShaderUtil.checkGLError(TAG, "After glEnableVertexAttribArray");

    //GLES20.glDepthMask(false);
    GLES20.glEnable(GLES20.GL_BLEND);
    // Grid, additive blending function.
    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

    GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId);
    GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, 0);
    GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

    ShaderUtil.checkGLError(TAG, "After glDrawElements");

    GLES20.glDisable(GLES20.GL_BLEND);

    GLES20.glDisable(GLES20.GL_CULL_FACE);

    // Disable vertex arrays
    GLES20.glDisableVertexAttribArray(positionAttribute);
    //GLES20.glDisableVertexAttribArray(normalAttribute);
    GLES20.glDisableVertexAttribArray(texCoordAttribute);

    //GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

    uvBuffer.rewind();
    GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, uvBuffer);

    updateFaceTexture();

    ShaderUtil.checkGLError(TAG, "After draw");
  }

  public void drawSecondPass(
          float[] cameraView,
          float[] cameraPerspective) {

    ShaderUtil.checkGLError(TAG, "Before draw2");

    GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT);

    Matrix.multiplyMM(modelViewMatrix, 0, cameraView, 0, modelMatrix, 0);
    Matrix.multiplyMM(modelViewProjectionMatrix, 0, cameraPerspective, 0, modelViewMatrix, 0);

    GLES20.glUseProgram(program);
    ShaderUtil.checkGLError(TAG, "After glUseProgram");

    GLES20.glCullFace(GLES20.GL_FRONT);
    GLES20.glEnable(GLES20.GL_CULL_FACE);

    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
    GLES20.glUniform1i(textureUniform, 0);

    // Set the vertex attributes.
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId);

    GLES20.glVertexAttribPointer(
            positionAttribute, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, verticesBaseAddress);
    //GLES20.glVertexAttribPointer(normalAttribute, 3, GLES20.GL_FLOAT, false, 0, normalsBaseAddress);
    GLES20.glVertexAttribPointer(
            texCoordAttribute, 2, GLES20.GL_FLOAT, false, 0, texCoordsBaseAddress);

    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

    ShaderUtil.checkGLError(TAG, "After glBindBuffers");

    // Set the ModelViewProjection matrix in the shader.
    //GLES20.glUniformMatrix4fv(modelViewUniform, 1, false, modelViewMatrix, 0);
    GLES20.glUniformMatrix4fv(modelViewProjectionUniform, 1, false, modelViewProjectionMatrix, 0);
    GLES20.glUniform1f(whichPassUniform, 1f);
    GLES20.glUniform1i(textureUniform, 0);

    // Enable vertex arrays
    GLES20.glEnableVertexAttribArray(positionAttribute);
    //GLES20.glEnableVertexAttribArray(normalAttribute);
    GLES20.glEnableVertexAttribArray(texCoordAttribute);

    ShaderUtil.checkGLError(TAG, "After glEnableVertexAttribArray");

    //GLES20.glDepthMask(false);
    GLES20.glEnable(GLES20.GL_BLEND);
    // Grid, additive blending function.
    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

    GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId);
    GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, 0);
    GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

    ShaderUtil.checkGLError(TAG, "After glDrawElements");

    GLES20.glDisable(GLES20.GL_BLEND);

    GLES20.glDisable(GLES20.GL_CULL_FACE);

    // Disable vertex arrays
    GLES20.glDisableVertexAttribArray(positionAttribute);
    //GLES20.glDisableVertexAttribArray(normalAttribute);
    GLES20.glDisableVertexAttribArray(texCoordAttribute);

    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

    ShaderUtil.checkGLError(TAG, "After draw2");
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
