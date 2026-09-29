package com.google.ar.core.examples.java.nav.api.model;

import java.util.List;

public class LocalizationResponse {
    public int code;
    public String message;
    public double server_time;
    public Data data;

    public static class Data {
        public String image_name;
        public double scale_factor_used;
        public List<RelativeNode> relative_map; // 接收服务器发来的相对坐标列表
        public Meta meta;
    }

    // 对应 relative_map 里面的每一个元素
    public static class RelativeNode {
        public int image_id;
        public String name;
        public List<Float> position; // 服务器传来的 [x, y, z]
        public String type;          // "doorplate" 或 "camera"
    }

    public static class Meta {
        public int num_inliers;
        public List<Double> camera_params;
    }
}
