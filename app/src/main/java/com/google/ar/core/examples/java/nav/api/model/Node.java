package com.google.ar.core.examples.java.nav.api.model;

/**
 * 导航节点 (Domain Model)
 * 用于 App 内部的路径规划算法
 */
public class Node {
    public String id;
    public float x;
    public float y;
    public float z;

    // ----- 新增字段：适配 ARCore 相对坐标系架构 -----

    // 存储经过 Pose.compose() 转换后的真实 ARCore 世界坐标
    public float arX;
    public float arY;
    public float arZ;

    // 节点类型："doorplate" (门牌号) 或 "camera" (历史轨迹点)
    public String type;

    // 保留原有的构造函数，以防你其他代码报错
    public Node(String id, float x, float y, float z) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    // 新增一个空构造函数，方便我们在网络回调里灵活赋值
    public Node() {
    }
}