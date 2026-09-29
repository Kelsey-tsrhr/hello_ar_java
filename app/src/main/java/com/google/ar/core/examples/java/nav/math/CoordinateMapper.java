// CoordinateMapper.java

package com.google.ar.core.examples.java.nav.math;

import android.opengl.Matrix;
import android.util.Log;
import com.google.ar.core.Pose;

public class CoordinateMapper {
    // T_offset = T_AR_World * (T_Server_World)^-1
    private final float[] serverToArTransform = new float[16];
    private boolean isLocalized = false;
    private final float[] arToServerTransform = new float[16];
    private final float[] invertedOffset = new float[16];
    public CoordinateMapper() {
        Matrix.setIdentityM(serverToArTransform, 0);
    }

    /**
     * 核心校准方法
     * @param arPoseAtCapture ARCore 此时的位姿
     * @param serverPose 经过 transformServerPoseToGL 修正后的服务器位姿
     */
    public void calibrate(Pose arPoseAtCapture, Pose serverPose) {
        float[] tArCam = new float[16];
        arPoseAtCapture.toMatrix(tArCam, 0);

        float[] tServerCam = new float[16];
        serverPose.toMatrix(tServerCam, 0);

        // 计算 T_Server_Cam 的逆矩阵 -> T_Cam_Server
        float[] tCamServer = new float[16];
        Matrix.invertM(tCamServer, 0, tServerCam, 0);

        // T_offset = T_AR_Cam * T_Cam_Server
        // 这样: P_AR = T_offset * P_Server
        Matrix.multiplyMM(serverToArTransform, 0, tArCam, 0, tCamServer, 0);

        isLocalized = true;
        Log.d("CoordinateMapper", "Calibrated successfully. Matrix updated.");
    }

    public boolean isLocalized() {
        return isLocalized;
    }

    public float[] mapServerPointToAr(float x, float y, float z) {
        if (!isLocalized) return null;

        // 注意：这里的 XYZ 必须是已经经过与 Camera 相同的坐标系修正后的坐标
        // 即 NavigationActivity 中 fixServerCoords 处理过的坐标
        float[] pointServer = {x, y, z, 1.0f};
        float[] pointAr = new float[4];

        Matrix.multiplyMV(pointAr, 0, serverToArTransform, 0, pointServer, 0);

        return new float[]{pointAr[0], pointAr[1], pointAr[2]};
    }

    public float[] mapArPointToServer(float arX, float arY, float arZ) {
        if (!isLocalized) {
            return null;
        }

        // 1. AR 世界坐标点
        float[] arPoint = {arX, arY, arZ, 1.0f};

        // 2. 求逆矩阵 (ServerToAr 变换矩阵的逆就是 ArToServer)
        float[] arToServerTransform = new float[16];
        android.opengl.Matrix.invertM(arToServerTransform, 0, serverToArTransform, 0);

        // 3. 矩阵乘法
        float[] serverPoint = new float[4];
        android.opengl.Matrix.multiplyMV(serverPoint, 0, arToServerTransform, 0, arPoint, 0);

        return new float[]{serverPoint[0], serverPoint[1], serverPoint[2]};
    }
}