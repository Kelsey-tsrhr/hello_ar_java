package com.google.ar.core.examples.java.nav.api;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    private static Retrofit retrofit = null;

    // 请将此处修改为您服务器的真实 IP 地址
//    private static final String BASE_URL = "http://172.18.36.70:8000/";
//    private static final String BASE_URL = "http://149.88.66.182:6001/";
    private static final String BASE_URL = "https://uu73187-jlx8-a100731a.bjb1.seetacloud.com:8443";

    public static Retrofit getClient() {
        if (retrofit == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}