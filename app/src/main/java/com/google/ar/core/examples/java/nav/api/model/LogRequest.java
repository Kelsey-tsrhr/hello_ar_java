package com.google.ar.core.examples.java.nav.api.model;

/**
 * 专门用于向服务器发送调试日志的数据模型
 */
public class LogRequest {
    public String tag;       // 日志标签，方便服务器过滤 (例如 "AR_POSE_DEBUG")
    public String message;   // 简短描述
    public String details;   // 详细的 JSON 或长文本数据

    public LogRequest(String tag, String message, String details) {
        this.tag = tag;
        this.message = message;
        this.details = details;
    }
}