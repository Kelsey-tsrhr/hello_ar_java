package com.google.ar.core.examples.java.nav.api.model;

import java.util.List;

public class RelativeLocalizationResponse {
    public int code;
    public String message;
    public double server_time;
    public Data data; // 这里不再是 List，而是一个 Data 对象

    public static class Data {
        public String image_name;
        public double scale_factor_used;
        // 真正包含坐标数据的数组在这里：
        public List<RelativeNode> relative_map;
        public Meta meta;
    }

    public static class RelativeNode {
        public int image_id;     // 对应服务器返回的 image_id
        public String name;
        public float[] position; // [x, y, z] 相对相机坐标
        public String type;      // "doorplate" 或 "camera"
    }

    public static class Meta {
        public int num_inliers;
        public List<Double> camera_params;
    }
}