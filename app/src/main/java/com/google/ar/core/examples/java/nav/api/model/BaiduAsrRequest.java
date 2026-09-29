package com.google.ar.core.examples.java.nav.api.model;

public class BaiduAsrRequest {
    public String format;
    public int rate;
    public int channel;
    public String cuid;
    public String token;
    public String speech;
    public int len;
    public Integer dev_pid;

    public BaiduAsrRequest(String format,
                           int rate,
                           int channel,
                           String cuid,
                           String token,
                           String speech,
                           int len,
                           Integer devPid) {
        this.format = format;
        this.rate = rate;
        this.channel = channel;
        this.cuid = cuid;
        this.token = token;
        this.speech = speech;
        this.len = len;
        this.dev_pid = devPid;
    }
}
