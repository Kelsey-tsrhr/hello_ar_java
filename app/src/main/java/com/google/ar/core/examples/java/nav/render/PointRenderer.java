package com.google.ar.core.examples.java.nav.render;

import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.Matrix;
import android.os.SystemClock;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

public class PointRenderer {

    private static final String VERTEX_SHADER_CODE =
            "uniform mat4 u_ModelViewProjection;\n" +
                    "uniform float u_BasePointSize;\n" +
                    "uniform float u_MinPointSize;\n" +
                    "uniform float u_MaxPointSize;\n" +
                    "attribute vec4 a_Position;\n" +
                    "void main() {\n" +
                    "    gl_Position = u_ModelViewProjection * a_Position;\n" +
                    "    float perspectiveScale = u_BasePointSize / max(gl_Position.w, 0.001);\n" +
                    "    gl_PointSize = clamp(perspectiveScale, u_MinPointSize, u_MaxPointSize);\n" +
                    "}";

    private static final String FRAGMENT_SHADER_CODE =
            "precision mediump float;\n" +
                    "uniform vec4 u_Color;\n" +
                    "uniform float u_Time;\n" +
                    "void main() {\n" +
                    "    vec2 coord = gl_PointCoord - vec2(0.5);\n" +
                    "    float dist = length(coord);\n" +
                    "    if (dist > 0.5) discard;\n" +
                    "    float alpha = (0.5 - dist) * 2.0;\n" +
                    "    float pulse = (sin(u_Time * 5.0) + 1.0) * 0.5;\n" +
                    "    gl_FragColor = vec4(u_Color.rgb, u_Color.a * alpha * (0.6 + 0.4 * pulse));\n" +
                    "}";

    private int program;
    private int positionAttribute;
    private int modelViewProjectionUniform;
    private int colorUniform;
    private int timeUniform;

    private int basePointSizeUniform;
    private int minPointSizeUniform;
    private int maxPointSizeUniform;

    private final float[] modelViewProjectionMatrix = new float[16];
    private final long startTimeMillis;

    private static final float[] COLOR_PATH_NODE = {0.0f, 0.6f, 1.0f, 0.7f};
    private static final float[] COLOR_DEST_NODE = {1.0f, 0.7f, 0.0f, 0.9f};

    // 路径骨架点：相对小一点
    private static final float PATH_BASE_SIZE = 42.0f;
    private static final float PATH_MIN_SIZE = 8.0f;
    private static final float PATH_MAX_SIZE = 34.0f;

    // 门牌号节点：相对更醒目
    private static final float DEST_BASE_SIZE = 110.0f;
    private static final float DEST_MIN_SIZE = 14.0f;
    private static final float DEST_MAX_SIZE = 72.0f;

    public PointRenderer() {
        startTimeMillis = SystemClock.uptimeMillis();
        initGl();
    }

    private void initGl() {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE);

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        positionAttribute = GLES20.glGetAttribLocation(program, "a_Position");
        modelViewProjectionUniform = GLES20.glGetUniformLocation(program, "u_ModelViewProjection");
        colorUniform = GLES20.glGetUniformLocation(program, "u_Color");
        timeUniform = GLES20.glGetUniformLocation(program, "u_Time");

        basePointSizeUniform = GLES20.glGetUniformLocation(program, "u_BasePointSize");
        minPointSizeUniform = GLES20.glGetUniformLocation(program, "u_MinPointSize");
        maxPointSizeUniform = GLES20.glGetUniformLocation(program, "u_MaxPointSize");
    }

    public void drawPathNodes(float[] viewMatrix, float[] projectionMatrix, List<float[]> points) {
        drawPoints(
                viewMatrix,
                projectionMatrix,
                points,
                COLOR_PATH_NODE,
                PATH_BASE_SIZE,
                PATH_MIN_SIZE,
                PATH_MAX_SIZE
        );
    }

    public void drawDestinations(float[] viewMatrix, float[] projectionMatrix, List<float[]> points) {
        drawPoints(
                viewMatrix,
                projectionMatrix,
                points,
                COLOR_DEST_NODE,
                DEST_BASE_SIZE,
                DEST_MIN_SIZE,
                DEST_MAX_SIZE
        );
    }

    private void drawPoints(float[] viewMatrix,
                            float[] projectionMatrix,
                            List<float[]> points,
                            float[] color,
                            float baseSize,
                            float minSize,
                            float maxSize) {
        if (points == null || points.isEmpty()) return;

        GLES30.glBindVertexArray(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        GLES20.glUseProgram(program);

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(true);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
        GLES20.glUniformMatrix4fv(modelViewProjectionUniform, 1, false, modelViewProjectionMatrix, 0);
        GLES20.glUniform4fv(colorUniform, 1, color, 0);

        GLES20.glUniform1f(basePointSizeUniform, baseSize);
        GLES20.glUniform1f(minPointSizeUniform, minSize);
        GLES20.glUniform1f(maxPointSizeUniform, maxSize);

        float time = (SystemClock.uptimeMillis() - startTimeMillis) / 1000.0f;
        GLES20.glUniform1f(timeUniform, time);

        int numPoints = points.size();
        ByteBuffer bb = ByteBuffer.allocateDirect(numPoints * 3 * 4);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer vb = bb.asFloatBuffer();

        for (float[] p : points) {
            vb.put(p[0]);
            vb.put(p[1]);
            vb.put(p[2]);
        }
        vb.position(0);

        GLES20.glEnableVertexAttribArray(positionAttribute);
        GLES20.glVertexAttribPointer(positionAttribute, 3, GLES20.GL_FLOAT, false, 0, vb);

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, numPoints);

        GLES20.glDisableVertexAttribArray(positionAttribute);
        GLES20.glUseProgram(0);
    }

    private int loadShader(int type, String code) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);
        return shader;
    }
}