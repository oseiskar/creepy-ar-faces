package xyz.osei.creepyarfaces;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;

import com.google.ar.core.Pose;

import java.io.IOException;

class FaceRenderer {
  private static final String TAG = FaceRenderer.class.getSimpleName();

  private int program;

  // Shader location: model view projection matrix.
  protected int modelViewUniform;
  protected int modelViewProjectionUniform;

  // Shader location: object attributes.
  protected int positionAttribute;
  protected int normalAttribute;
  protected int texCoordAttribute;

  protected int textureUniform;

  // Temporary matrices allocated here to reduce number of allocations for each frame.
  protected final float[] modelMatrix = new float[16];
  protected final float[] modelViewMatrix = new float[16];
  protected final float[] modelViewProjectionMatrix = new float[16];

  private final FaceGeometry faceGeometry;
  private final String vertexShaderName;
  private final String fragmentShaderName;

  protected boolean usesFaceMapper = true;
  protected boolean usesNormals = false;

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

    if (usesNormals) modelViewUniform = GLES20.glGetUniformLocation(program, "u_ModelView");
    modelViewProjectionUniform = GLES20.glGetUniformLocation(program, "u_ModelViewProjection");

    positionAttribute = GLES20.glGetAttribLocation(program, "a_Position");
    if (usesNormals) normalAttribute = GLES20.glGetAttribLocation(program, "a_Normal");
    else normalAttribute = -1;
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

  public boolean needsFaceMapper() {
    return usesFaceMapper;
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

    faceGeometry.bindGeometryBuffers(positionAttribute, texCoordAttribute, normalAttribute);

    // Set the ModelViewProjection matrix in the shader.
    if (usesNormals) GLES20.glUniformMatrix4fv(modelViewUniform, 1, false, modelViewMatrix, 0);
    GLES20.glUniformMatrix4fv(modelViewProjectionUniform, 1, false, modelViewProjectionMatrix, 0);
    GLES20.glUniform1i(textureUniform, 0);

    // Enable vertex arrays
    GLES20.glEnableVertexAttribArray(positionAttribute);
    if (usesNormals) GLES20.glEnableVertexAttribArray(normalAttribute);
    GLES20.glEnableVertexAttribArray(texCoordAttribute);

    ShaderUtil.checkGLError(TAG, "After glEnableVertexAttribArray");

    //GLES20.glDepthMask(false);
    GLES20.glEnable(GLES20.GL_BLEND);
    // Grid, additive blending function.
    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

    faceGeometry.drawElements();

    faceGeometry.unbindGeometryBuffers(positionAttribute, texCoordAttribute, normalAttribute);

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
    pose
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

class FaceRenderWithTexture extends FaceRenderer {
  private static final String TAG = FaceRenderWithTexture.class.getSimpleName();

  private final int[] textures = new int[1];
  final String textureFilename;

  public FaceRenderWithTexture(FaceGeometry geometry, String textureName, String vertexShader, String fragmentShader) {
    super(geometry,vertexShader, fragmentShader);
    textureFilename = textureName;
    usesFaceMapper = false;
  }

  @Override
  public void draw(
          float[] cameraView,
          float[] cameraPerspective,
          int faceTextureId) {
    super.draw(cameraView, cameraPerspective, textures[0]);
  }

  public void createOnGlThread(Context context) throws IOException {
    super.createOnGlThread(context);
    Bitmap textureBitmap = BitmapFactory.decodeStream(context.getAssets().open(textureFilename));

    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glGenTextures(textures.length, textures, 0);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);

    GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, textureBitmap, 0);
    GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

    textureBitmap.recycle();

    ShaderUtil.checkGLError(TAG, "Texture loading");
  }
}

class FaceRendererUnshadedTexture extends FaceRenderWithTexture {
  public FaceRendererUnshadedTexture(FaceGeometry geometry, String textureName) {
    super(geometry,textureName,"shaders/uv.vert", "shaders/unshadedtexture.frag");
  }
}

class FaceRendererShadedTexture extends FaceRenderWithTexture {
  public FaceRendererShadedTexture(FaceGeometry geometry, String textureName) {
    super(geometry,textureName,"shaders/object.vert", "shaders/object.frag");
    usesNormals = true;
  }
}

class FaceRendererUV extends FaceRenderer {
  public FaceRendererUV(FaceGeometry geometry) {
    super(geometry,"shaders/uv.vert", "shaders/uv.frag");
    usesFaceMapper = false;
  }
}
