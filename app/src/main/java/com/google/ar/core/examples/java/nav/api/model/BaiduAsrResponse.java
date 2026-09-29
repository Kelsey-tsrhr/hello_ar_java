package com.google.ar.core.examples.java.nav.api.model;

import java.util.List;

public class BaiduAsrResponse {
    public String corpus_no;
    public String err_msg;
    public int err_no;
    public List<String> result;
    public String sn;

    public boolean isSuccess() {
        return err_no == 0 && result != null && !result.isEmpty();
    }

    public String getBestResult() {
        return (result == null || result.isEmpty()) ? null : result.get(0);
    }
}
