package com.google.ar.core.examples.java.nav.api;

import com.google.ar.core.examples.java.nav.api.model.LocalizationResponse;
import com.google.ar.core.examples.java.nav.api.model.LogRequest;
import com.google.ar.core.examples.java.nav.api.model.MapDataResponse;
import com.google.ar.core.examples.java.nav.api.model.RelativeLocalizationResponse;

import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Url;

public interface LocalizationApi {
    @Multipart
    @POST("/api/v1/localization/query")
    Call<LocalizationResponse> localizeImage(
            @Part MultipartBody.Part file
    );

    @GET("/api/v1/visualization/map")
    Call<MapDataResponse> getMapData();

    @Multipart
    @POST
    Call<RelativeLocalizationResponse> queryRelativeMap(
            @Url String url,
            @Part MultipartBody.Part file
    );

    @POST("/api/v1/localization/client_log")
    Call<ResponseBody> sendClientLog(@Body LogRequest logRequest);
}