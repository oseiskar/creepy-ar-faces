package xyz.osei.creepyarfaces;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;

import com.google.ar.core.Pose;

import java.io.IOException;

abstract class FaceRenderer {
  private static final String TAG = FaceRenderer.class.getSimpleName();

  private int program;

  // Shader location: model view projection matrix.
  //private int modelViewUniform;
  protected int modelViewProjectionUniform;

  // Shader location: object attributes.
  protected int positionAttribute;
  //protected int normalAttribute;
  protected int texCoordAttribute;

  protected int textureUniform;

  // Temporary matrices allocated here to reduce number of allocations for each frame.
  protected final float[] modelMatrix = new float[16];
  protected final float[] modelViewMatrix = new float[16];
  protected final float[] modelViewProjectionMatrix = new float[16];

  private final FaceGeometry faceGeometry;
  private final String vertexShaderName;
  private final String fragmentShaderName;

  public FaceRenderer(FaceGeometry geometry, String vertexShader, String fragmentShader) {
    faceGeometry = geometry;
    vertexShaderName = vertexShader;
    fragmentShaderName = fragmentShader;
  }

  /**
   * Creates and initializes OpenGL resources needed for rendering the model.
   *
   * @param context Context for loading the shader and below-named model and texture assets.
   */
  public void createOnGlThread(Context context)
      throws IOException {
    final int vertexShader =
        ShaderUtil.loadGLShader(TAG, context, GLES20.GL_VERTEX_SHADER, vertexShaderName);
    final int fragmentShader =
        ShaderUtil.loadGLShader(TAG, context, GLES20.GL_FRAGMENT_SHADER, fragmentShaderName);

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

    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

    Matrix.setIdentityM(modelMatrix, 0);
    ShaderUtil.checkGLError(TAG, "end FaceRenderer.createOnGlThread");
  }

  public void updateModelMatrix(Pose pose) {
    pose.toMatrix(this.modelMatrix, 0);
  }

  public void draw(
          float[] cameraView,
          float[] cameraPerspective,
          int faceTextureId) {

    if (!faceGeometry.isReady()) return;

    ShaderUtil.checkGLError(TAG, "Before draw");

    GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT);

    Matrix.multiplyMM(modelViewMatrix, 0, cameraView, 0, modelMatrix, 0);
    Matrix.multiplyMM(modelViewProjectionMatrix, 0, cameraPerspective, 0, modelViewMatrix, 0);

    GLES20.glUseProgram(program);
    ShaderUtil.checkGLError(TAG, "After glUseProgram");

    GLES20.glCullFace(GLES20.GL_FRONT);
    GLES20.glEnable(GLES20.GL_CULL_FACE);

    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, faceTextureId);
    GLES20.glUniform1i(textureUniform, 0);

    faceGeometry.bindGeometryBuffers(positionAttribute, texCoordAttribute, -1);

    // Set the ModelViewProjection matrix in the shader.
    //GLES20.glUniformMatrix4fv(modelViewUniform, 1, false, modelViewMatrix, 0);
    GLES20.glUniformMatrix4fv(modelViewProjectionUniform, 1, false, modelViewProjectionMatrix, 0);
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

    faceGeometry.drawElements();

    faceGeometry.unbindGeometryBuffers(positionAttribute, texCoordAttribute, -1);

    GLES20.glDisable(GLES20.GL_BLEND);
    GLES20.glDisable(GLES20.GL_CULL_FACE);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

    ShaderUtil.checkGLError(TAG, "After draw");
  }
}

class FaceRenderer4Eyes extends FaceRenderer {
  public FaceRenderer4Eyes(FaceGeometry geometry) {
    super(geometry,"shaders/uv.vert", "shaders/4eyes.frag");
  }

  @Override
  public void updateModelMatrix(Pose pose) {
    /*float rotAx[] = new float[]{ 0, 0, 1 };
    double ang = Math.PI;
    float si = (float)Math.sin(ang*0.5);
    float co = (float)Math.cos(ang*0.5);*/
    pose
            //.compose(Pose.makeRotation(rotAx[0]*si, rotAx[1]*si, rotAx[2]*si, co))
            .compose(Pose.makeTranslation(0,0.02f,0))
            .toMatrix(this.modelMatrix, 0);
  }
}

class FaceRendererUpsideDown extends FaceRenderer {
  public FaceRendererUpsideDown(FaceGeometry geometry) {
    super(geometry,"shaders/uv.vert", "shaders/upsidedown.frag");
  }
}

class FaceRendererLargeNose extends FaceRenderer {
  public FaceRendererLargeNose(FaceGeometry geometry) {
    super(geometry,"shaders/uv.vert", "shaders/largenose.frag");
  }
}