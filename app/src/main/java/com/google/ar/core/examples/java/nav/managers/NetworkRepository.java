package com.google.ar.core.examples.java.nav.managers;

import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.google.ar.core.examples.java.nav.api.BaiduSpeechApi;
import com.google.ar.core.examples.java.nav.api.LocalizationApi;
import com.google.ar.core.examples.java.nav.api.model.BaiduAsrRequest;
import com.google.ar.core.examples.java.nav.api.model.BaiduAsrResponse;
import com.google.ar.core.examples.java.nav.api.model.LogRequest;
import com.google.ar.core.examples.java.nav.api.model.RelativeLocalizationResponse;
import com.google.ar.core.examples.java.nav.BuildConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.Locale;

public class NetworkRepository {
    private static final String TAG = "NetworkRepository";
    private static final String BAIDU_ASR_BASE_URL = "https://vop.baidu.com/";
    public static final int BAIDU_DEV_PID_MANDARIN = 1537;
    public static final int BAIDU_DEV_PID_ENGLISH = 1737;

    private final LocalizationApi localizationApi;
    private final BaiduSpeechApi baiduSpeechApi;

    private String baiduAsrToken;
    private String baiduCuid;
    private int baiduAsrDevPid = BAIDU_DEV_PID_MANDARIN;

    private static final String RELATIVE_LOCALIZATION_PATH_TEMPLATE =
            "api/v1/localization/query_relative/%d";

    private final String baseUrl;
    private int currentSceneId = 1;

    public interface SpeechRecognitionCallback {
        void onSuccess(BaiduAsrResponse response);
        void onFailure(String message, Throwable throwable);
    }

    public NetworkRepository(String baseUrl) {
        this(baseUrl,
                BuildConfig.BAIDU_ASR_TOKEN,
                BuildConfig.BAIDU_ASR_CUID);
    }

    public NetworkRepository(String baseUrl, String baiduAsrToken, String baiduCuid) {
        this.baseUrl = normalizeBaseUrl(baseUrl);

        OkHttpClient commonClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        Retrofit localRetrofit = new Retrofit.Builder()
                .baseUrl(this.baseUrl)
                .client(commonClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        localizationApi = localRetrofit.create(LocalizationApi.class);

        OkHttpClient baiduClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build();

        Retrofit baiduRetrofit = new Retrofit.Builder()
                .baseUrl(BAIDU_ASR_BASE_URL)
                .client(baiduClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        baiduSpeechApi = baiduRetrofit.create(BaiduSpeechApi.class);

        this.baiduAsrToken = baiduAsrToken;
        this.baiduCuid = baiduCuid;
    }

    public void configureBaiduSpeech(String token, String cuid) {
        this.baiduAsrToken = token;
        this.baiduCuid = cuid;
    }

    public void setBaiduAsrDevPid(int devPid) {
        baiduAsrDevPid = devPid == BAIDU_DEV_PID_ENGLISH
                ? BAIDU_DEV_PID_ENGLISH
                : BAIDU_DEV_PID_MANDARIN;
    }

    public int getBaiduAsrDevPid() {
        return baiduAsrDevPid;
    }

    public void queryRelativeMap(byte[] imageBytes, Callback<RelativeLocalizationResponse> callback) {
        RequestBody requestFile = RequestBody.create(MediaType.parse("image/jpeg"), imageBytes);
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", "query.jpg", requestFile);

        String relativeUrl = buildRelativeLocalizationPath();
        Log.d(TAG, "queryRelativeMap: scene=" + currentSceneId + ", url=" + relativeUrl);

        localizationApi.queryRelativeMap(relativeUrl, body).enqueue(callback);
    }

    public void sendLogToServer(String tag, String message, String details) {
        LogRequest request = new LogRequest(tag, message, details);
        localizationApi.sendClientLog(request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                Log.d("NetworkLogger", "日志已送达服务器: " + tag);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("NetworkLogger", "日志发送失败: " + t.getMessage());
            }
        });
    }

    public void recognizeSpeechPcm(byte[] pcmData, SpeechRecognitionCallback callback) {
        if (callback == null) return;

        if (pcmData == null || pcmData.length == 0) {
            callback.onFailure("录音数据为空", null);
            return;
        }
        if (TextUtils.isEmpty(baiduAsrToken)) {
            callback.onFailure("百度语音 token 未配置", null);
            return;
        }
        if (TextUtils.isEmpty(baiduCuid)) {
            callback.onFailure("百度语音 cuid 未配置", null);
            return;
        }

        String speechBase64 = Base64.encodeToString(pcmData, Base64.NO_WRAP);
        BaiduAsrRequest request = new BaiduAsrRequest(
                "pcm",
                16000,
                1,
                baiduCuid,
                baiduAsrToken,
                speechBase64,
                pcmData.length,
                baiduAsrDevPid
        );

        baiduSpeechApi.recognizeShortSpeech(request).enqueue(new Callback<BaiduAsrResponse>() {
            @Override
            public void onResponse(Call<BaiduAsrResponse> call, Response<BaiduAsrResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onFailure("百度语音识别请求失败: HTTP " + response.code(), null);
                    return;
                }

                BaiduAsrResponse body = response.body();
                if (!body.isSuccess()) {
                    String errMsg = body.err_msg == null ? "语音识别失败" : body.err_msg;
                    callback.onFailure("百度语音识别失败: " + errMsg + " (err_no=" + body.err_no + ")", null);
                    return;
                }

                callback.onSuccess(body);
            }

            @Override
            public void onFailure(Call<BaiduAsrResponse> call, Throwable t) {
                callback.onFailure("百度语音识别网络失败: " + (t != null ? t.getMessage() : "unknown"), t);
            }
        });
    }

    public void setSceneId(int sceneId) {
        currentSceneId = Math.max(1, sceneId);
    }

    public int getSceneId() {
        return currentSceneId;
    }

    private String buildRelativeLocalizationPath() {
        return String.format(Locale.US, RELATIVE_LOCALIZATION_PATH_TEMPLATE, currentSceneId);
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (TextUtils.isEmpty(baseUrl)) {
            throw new IllegalArgumentException("baseUrl can not be empty");
        }
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }
}
