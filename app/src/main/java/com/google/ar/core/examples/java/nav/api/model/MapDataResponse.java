package com.google.ar.core.examples.java.nav.api.model;

/**
 * 对应服务器 API 接口的 JSON 响应结构
 */
import java.util.List;
import java.util.Map;

public class MapDataResponse {
    public int code;
    public String message;
    public MapData data;

    public static class MapData {
        // 对应 JSON 里的 "doorplates" 字典
        public Map<String, Doorplate> doorplates;
        // 对应 JSON 里的 "cameras" 列表
        public List<CameraNode> cameras;
    }

    public static class Doorplate {
        public List<Float> position;
        public int count;
    }

    public static class CameraNode {
        public int image_id;
        public String name;
        public List<Float> position;
    }
}
