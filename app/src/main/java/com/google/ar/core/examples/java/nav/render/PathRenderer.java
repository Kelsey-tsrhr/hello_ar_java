package com.google.ar.core.examples.java.nav.render;

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.SystemClock;

import com.google.ar.core.examples.java.common.samplerender.SampleRender;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

/**
 * 使用世界坐标带状面片来绘制导航路径：
 * 1. 宽度以“米”为单位定义，因此透视投影后会自然呈现“近大远小”；
 * 2. 保留流动效果；
 * 3. 不再依赖固定像素宽度的 glLineWidth。
 */
public class PathRenderer {

    private static final String VERTEX_SHADER_CODE =
            "uniform mat4 u_ModelViewProjection;\n" +
                    "attribute vec4 a_Position;\n" +
                    "attribute float a_DistAlongPath;\n" +
                    "varying float v_DistAlongPath;\n" +
                    "void main() {\n" +
                    "    v_DistAlongPath = a_DistAlongPath;\n" +
                    "    gl_Position = u_ModelViewProjection * a_Position;\n" +
                    "}";

    private static final String FRAGMENT_SHADER_CODE =
            "precision mediump float;\n" +
                    "uniform vec4 u_Color;\n" +
                    "uniform float u_Time;\n" +
                    "varying float v_DistAlongPath;\n" +
                    "void main() {\n" +
                    "    float phase = mod(v_DistAlongPath - u_Time * 1.4, 1.0);\n" +
                    "    float head = smoothstep(0.12, 0.38, phase);\n" +
                    "    float tail = 1.0 - smoothstep(0.72, 0.98, phase);\n" +
                    "    float flow = max(0.30, head * tail);\n" +
                    "    gl_FragColor = vec4(u_Color.rgb, u_Color.a * flow);\n" +
                    "}";

    private static final float[] PATH_COLOR = {1.0f, 1.0f, 0.0f, 0.92f};

    /** 半宽，单位：米。可按视觉效果微调。 */
    private static final float PATH_HALF_WIDTH_METERS = 0.10f;

    private static final float EPS = 1e-5f;

    private int program;
    private int positionAttribute;
    private int distAttribute;
    private int modelViewProjectionUniform;
    private int colorUniform;
    private int timeUniform;

    private final float[] mvpMatrix = new float[16];
    private final long startTimeMillis;

    public PathRenderer(SampleRender render) {
        startTimeMillis = SystemClock.uptimeMillis();
        initGl();
    }

    private void initGl() {
        program = createProgram(VERTEX_SHADER_CODE, FRAGMENT_SHADER_CODE);
        positionAttribute = GLES20.glGetAttribLocation(program, "a_Position");
        distAttribute = GLES20.glGetAttribLocation(program, "a_DistAlongPath");
        modelViewProjectionUniform = GLES20.glGetUniformLocation(program, "u_ModelViewProjection");
        colorUniform = GLES20.glGetUniformLocation(program, "u_Color");
        timeUniform = GLES20.glGetUniformLocation(program, "u_Time");
    }

    public void draw(float[] viewMatrix, float[] projectionMatrix, List<float[]> points) {
        if (points == null || points.size() < 2 || viewMatrix == null || projectionMatrix == null) {
            return;
        }

        final int numPoints = points.size();
        final int vertexCount = numPoints * 2;

        FloatBuffer posBuffer = ByteBuffer
                .allocateDirect(vertexCount * 3 * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        FloatBuffer distBuffer = ByteBuffer
                .allocateDirect(vertexCount * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        float[] cameraForward = getCameraForward(viewMatrix);
        if (cameraForward == null) {
            cameraForward = new float[]{0f, 0f, -1f};
        }

        float[] lastRight = null;
        float totalDist = 0f;

        for (int i = 0; i < numPoints; i++) {
            float[] p = points.get(i);
            if (p == null || p.length < 3) continue;

            if (i > 0) {
                float[] prev = points.get(i - 1);
                totalDist += distance(prev, p);
            }

            float[] tangent = computeTangent(points, i);
            float[] right = computeRibbonRight(tangent, cameraForward);

            // 防止 strip 在相邻点处左右翻面
            if (lastRight != null && dot(right, lastRight) < 0f) {
                right[0] = -right[0];
                right[1] = -right[1];
                right[2] = -right[2];
            }
            lastRight = new float[]{right[0], right[1], right[2]};

            float lx = p[0] - right[0] * PATH_HALF_WIDTH_METERS;
            float ly = p[1] - right[1] * PATH_HALF_WIDTH_METERS;
            float lz = p[2] - right[2] * PATH_HALF_WIDTH_METERS;

            float rx = p[0] + right[0] * PATH_HALF_WIDTH_METERS;
            float ry = p[1] + right[1] * PATH_HALF_WIDTH_METERS;
            float rz = p[2] + right[2] * PATH_HALF_WIDTH_METERS;

            posBuffer.put(lx).put(ly).put(lz);
            distBuffer.put(totalDist);

            posBuffer.put(rx).put(ry).put(rz);
            distBuffer.put(totalDist);
        }

        posBuffer.position(0);
        distBuffer.position(0);

        GLES20.glUseProgram(program);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glDepthMask(false);

        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
        GLES20.glUniformMatrix4fv(modelViewProjectionUniform, 1, false, mvpMatrix, 0);
        GLES20.glUniform4fv(colorUniform, 1, PATH_COLOR, 0);

        float time = (SystemClock.uptimeMillis() - startTimeMillis) / 1000.0f;
        GLES20.glUniform1f(timeUniform, time);

        GLES20.glEnableVertexAttribArray(positionAttribute);
        GLES20.glVertexAttribPointer(positionAttribute, 3, GLES20.GL_FLOAT, false, 0, posBuffer);

        GLES20.glEnableVertexAttribArray(distAttribute);
        GLES20.glVertexAttribPointer(distAttribute, 1, GLES20.GL_FLOAT, false, 0, distBuffer);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, vertexCount);

        GLES20.glDisableVertexAttribArray(positionAttribute);
        GLES20.glDisableVertexAttribArray(distAttribute);

        GLES20.glDepthMask(true);
        GLES20.glUseProgram(0);
    }

    private float[] computeTangent(List<float[]> points, int index) {
        int last = points.size() - 1;

        float[] prev = points.get(Math.max(0, index - 1));
        float[] next = points.get(Math.min(last, index + 1));

        float dx = next[0] - prev[0];
        float dy = next[1] - prev[1];
        float dz = next[2] - prev[2];

        float len = length(dx, dy, dz);
        if (len < EPS) {
            return new float[]{1f, 0f, 0f};
        }
        return new float[]{dx / len, dy / len, dz / len};
    }

    private float[] computeRibbonRight(float[] tangent, float[] cameraForward) {
        float[] right = cross(cameraForward, tangent);
        float len = length(right[0], right[1], right[2]);

        if (len < EPS) {
            right = cross(new float[]{0f, 1f, 0f}, tangent);
            len = length(right[0], right[1], right[2]);
        }

        if (len < EPS) {
            return new float[]{1f, 0f, 0f};
        }

        return new float[]{right[0] / len, right[1] / len, right[2] / len};
    }

    private float[] getCameraForward(float[] viewMatrix) {
        float[] inverseView = new float[16];
        if (!Matrix.invertM(inverseView, 0, viewMatrix, 0)) {
            return null;
        }

        float[] originCam = {0f, 0f, 0f, 1f};
        float[] forwardCam = {0f, 0f, -1f, 1f};

        float[] originWorld = new float[4];
        float[] forwardWorld = new float[4];

        Matrix.multiplyMV(originWorld, 0, inverseView, 0, originCam, 0);
        Matrix.multiplyMV(forwardWorld, 0, inverseView, 0, forwardCam, 0);

        float dx = forwardWorld[0] - originWorld[0];
        float dy = forwardWorld[1] - originWorld[1];
        float dz = forwardWorld[2] - originWorld[2];
        float len = length(dx, dy, dz);

        if (len < EPS) return null;
        return new float[]{dx / len, dy / len, dz / len};
    }

    private float distance(float[] a, float[] b) {
        if (a == null || b == null) return 0f;
        float dx = b[0] - a[0];
        float dy = b[1] - a[1];
        float dz = b[2] - a[2];
        return length(dx, dy, dz);
    }

    private float[] cross(float[] a, float[] b) {
        return new float[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }

    private float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private float length(float x, float y, float z) {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    private int createProgram(String vertexCode, String fragmentCode) {
        int vs = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vs, vertexCode);
        GLES20.glCompileShader(vs);

        int fs = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fs, fragmentCode);
        GLES20.glCompileShader(fs);

        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, vs);
        GLES20.glAttachShader(p, fs);
        GLES20.glLinkProgram(p);
        return p;
    }
}