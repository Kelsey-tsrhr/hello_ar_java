package com.google.ar.core.examples.java.nav.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class ImageUtils {

    /**
     * 将 ARCore/Camera2 的 YUV_420_888 Image 转换为 JPEG 字节数组
     */
    public static byte[] yuvToJpeg(Image image) {
        try {
            // 确保格式正确
            if (image.getFormat() != ImageFormat.YUV_420_888) {
                throw new IllegalArgumentException("Image must be in YUV_420_888 format");
            }

            int width = image.getWidth();
            int height = image.getHeight();

            // 获取平面数据
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            // NV21 格式是 YUV420SP 的一种：先存 Y，再交叉存 V 和 U (V, U, V, U...)
            // 许多手机的 rowStride != width，需要剔除 padding
            byte[] nv21 = new byte[width * height * 3 / 2];

            // 1. 复制 Y 分量 (逐行复制以去除 padding)
            int yRowStride = planes[0].getRowStride();
            int yPixelStride = planes[0].getPixelStride(); // 通常为 1
            int yPos = 0;
            for (int row = 0; row < height; row++) {
                yBuffer.position(row * yRowStride);
                yBuffer.get(nv21, yPos, width);
                yPos += width;
            }

            // 2. 复制 U/V 分量 (交叉为 NV21: V, U, V, U...)
            // 注意：ARCore 获取的 Image 通常 U 和 V 的 pixelStride 为 2 (即交叉存储)
            // NV21 要求 V 在前，U 在后
            int uvRowStride = planes[1].getRowStride(); // U 和 V 的 stride 通常相同
            int uvPixelStride = planes[1].getPixelStride();
            int uvHeight = height / 2;
            int uvWidth = width / 2;

            int uvPos = width * height; // 从 Y 数据之后开始填充

            // 遍历每一行 UV 数据
            for (int row = 0; row < uvHeight; row++) {
                for (int col = 0; col < uvWidth; col++) {
                    int bufferIndex = row * uvRowStride + col * uvPixelStride;

                    // 获取 V (NV21 V first)
                    // 即使 vBuffer 里可能有 padding，通过计算 index 精确获取
                    byte v = vBuffer.get(bufferIndex);

                    // 获取 U
                    byte u = uBuffer.get(bufferIndex);

                    nv21[uvPos++] = v;
                    nv21[uvPos++] = u;
                }
            }

            // 创建 YuvImage 对象
            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // 压缩为 JPEG，质量 80%
            yuvImage.compressToJpeg(new Rect(0, 0, width, height), 80, out);
            return out.toByteArray();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    /**
     * 将 YUV_420_888 Image 转为 Bitmap，供本地视觉模型使用。
     * 当前实现复用 JPEG 转码链路，优先保证兼容性与稳定性。
     */
    public static Bitmap yuvToBitmap(Image image) {
        byte[] jpegBytes = yuvToJpeg(image);
        if (jpegBytes == null || jpegBytes.length == 0) return null;
        try {
            return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}