package com.google.ar.core.examples.java.nav.api;

import com.google.ar.core.examples.java.nav.api.model.BaiduAsrRequest;
import com.google.ar.core.examples.java.nav.api.model.BaiduAsrResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface BaiduSpeechApi {
    @POST("server_api")
    Call<BaiduAsrResponse> recognizeShortSpeech(@Body BaiduAsrRequest request);
}
