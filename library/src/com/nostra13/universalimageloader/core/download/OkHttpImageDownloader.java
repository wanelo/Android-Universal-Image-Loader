package com.nostra13.universalimageloader.core.download;

import android.content.Context;
import android.net.Uri;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.OkUrlFactory;

public class OkHttpImageDownloader extends BaseImageDownloader {

    private OkUrlFactory factory;

    public OkHttpImageDownloader(Context context, int connectTimeoutSeconds, int readTimeOutSeconds) {
        super(context);
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.readTimeout(readTimeOutSeconds, TimeUnit.SECONDS);
        builder.connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS);
        OkHttpClient client = builder.build();
        factory = new OkUrlFactory(client);
    }

    @Override
    protected HttpURLConnection createConnection(String url, Object extra) throws IOException {
        String encodedUrl = Uri.encode(url, ALLOWED_URI_CHARS);

        final HttpURLConnection connection = factory.open(new URL(encodedUrl));
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod("GET");

        return connection;
    }
}
