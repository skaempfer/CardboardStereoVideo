/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
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
 */

package com.google.vrtoolkit.cardboard.samples.treasurehunt;

import com.google.vr.sdk.base.GvrActivity;
import com.google.vr.sdk.base.GvrView;
import com.ofemobile.samples.stereovideovr.R;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.microedition.khronos.egl.EGLConfig;

/**
 * A Cardboard sample application.
 */
public class MainActivity extends GvrActivity implements GvrView.StereoRenderer {

  private static final String TAG = "MainActivity";

  private Vibrator vibrator;
  private GvrView gvrView;
  private VideoRenderer videoRenderer;
  private CardboardOverlayView overlayView;

  boolean renderStereo = true;

  private final float[] mMVPMatrix = new float[16];
  private final float[] mProjectionMatrix = new float[16];
  private final float[] mViewMatrix = new float[16];

  //private volatile int soundId = CardboardAudioEngine.INVALID_ID;

  /**
   * Converts a raw text file, saved as a resource, into an OpenGL ES shader.
   *
   * @param type The type of shader we will be creating.
   * @param resId The resource ID of the raw text file about to be turned into a shader.
   * @return The shader object handler.
   */
  public int loadGLShader(int type, int resId) {
    String code = readRawTextFile(resId);
    int shader = GLES20.glCreateShader(type);
    GLES20.glShaderSource(shader, code);
    GLES20.glCompileShader(shader);

    // Get the compilation status.
    final int[] compileStatus = new int[1];
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

    // If the compilation failed, delete the shader.
    if (compileStatus[0] == 0) {
      Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader));
      GLES20.glDeleteShader(shader);
      shader = 0;
    }

    if (shader == 0) {
      throw new RuntimeException("Error creating shader.");
    }

    return shader;
  }

  /**
   * Checks if we've had an error inside of OpenGL ES, and if so what that error is.
   *
   * @param label Label to report in case of error.
   */
  public static void checkGLError(String label) {
    int error;
    while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
      Log.e(TAG, label + ": glError " + error);
      throw new RuntimeException(label + ": glError " + error);
    }
  }

  /**
   * Sets the view to our CardboardView and initializes the transformation matrices we will use
   * to render our scene.
   */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.common_ui);
    gvrView = (GvrView) findViewById(R.id.cardboard_view);
    gvrView.setRenderer(this);

    vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

    overlayView = (CardboardOverlayView) findViewById(R.id.overlay);
  }

  @Override
  public void onPause() {
    super.onPause();
    Log.i(TAG, "onPause()");
    if (videoRenderer !=null)
      videoRenderer.pause();
  }

  @Override
  public void onResume() {
    super.onResume();
    Log.i(TAG, "onResume()");
    if (videoRenderer != null) {
      videoRenderer.start();
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    Log.i(TAG, "onStart()");
  }

  @Override
  public void onStop() {
    super.onStop();
    Log.i(TAG, "onStop()");
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.i(TAG, "onDestroy()");
    if (videoRenderer != null) {
      videoRenderer.cleanup();
    }
  }

  @Override
  public void onRendererShutdown() {
    Log.i(TAG, "onRendererShutdown");
  }

  @Override
  public void onNewFrame(com.google.vr.sdk.base.HeadTransform headTransform) {

  }

  @Override
  public void onDrawEye(com.google.vr.sdk.base.Eye eye) {
    //GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    //checkGLError("colorParam");

    // Set the camera position (View matrix)
    Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 4, 0f, 0f, 0f, 0f, 1.0f, 0.0f);

    // Calculate the projection and view transformation
    Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);

    videoRenderer.setMVPMatrix(mMVPMatrix);

    if (renderStereo)
      videoRenderer.render(eye.getType());
    else  //When stereo rendering turned off always render the bottom image.
      videoRenderer.render(1);
  }

  @Override
  public void onFinishFrame(com.google.vr.sdk.base.Viewport viewport) {

  }

  @Override
  public void onSurfaceChanged(int width, int height) {
    Log.i(TAG, "onSurfaceChanged");

    GLES20.glViewport(0,0,width,height);

    float ratio = (float) width / height;

    // this projection matrix is applied to object coordinates
    // in the onDrawFrame() method
    Matrix.frustumM(mProjectionMatrix, 0, -ratio, ratio, -1, 1, 3, 7);
  }

  /**
   * Creates the buffers we use to store information about the 3D world.
   *
   * <p>OpenGL doesn't use Java arrays, but rather needs data in a format it can understand.
   * Hence we use ByteBuffers.
   *
   * @param config The EGL configuration used when creating the surface.
   */
  @Override
  public void onSurfaceCreated(EGLConfig config) {
    Log.i(TAG, "onSurfaceCreated");
    GLES20.glClearColor(0.1f, 0.1f, 0.1f, 0.5f); // Dark background so text shows up well.

    //Setup video renderer:
    videoRenderer = new VideoRenderer(this);
    videoRenderer.setup();
    videoRenderer.start();

    checkGLError("onSurfaceCreated");
  }

  /**
   * Converts a raw text file into a string.
   *
   * @param resId The resource ID of the raw text file about to be turned into a shader.
   * @return The context of the text file, or null in case of error.
   */
   private String readRawTextFile(int resId) {
    InputStream inputStream = getResources().openRawResource(resId);
    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append("\n");
      }
      reader.close();
      return sb.toString();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * Called when the Cardboard trigger is pulled.
   *
   * TODO: Doesn't work anymore since switching from old CardboardActivity to GvrActivity. Should be fixed to re-enable switching from 3D mode to 2D mode.
   */
  @Override
  public void onCardboardTrigger() {
    Log.i(TAG, "onCardboardTrigger");

    renderStereo =!renderStereo;
    if (renderStereo)
      overlayView.show3DToast("Stereo 3D On");
    else
      overlayView.show3DToast("Stereo 3D Off");
//    if (isLookingAtObject()) {
//      score++;
//      overlayView.show3DToast("Found it! Look around for another one.\nScore = " + score);
//      hideObject();
//    } else {
//      overlayView.show3DToast("Look around to find the object!");
//    }

    // Always give user feedback.
    vibrator.vibrate(50);
  }
}
