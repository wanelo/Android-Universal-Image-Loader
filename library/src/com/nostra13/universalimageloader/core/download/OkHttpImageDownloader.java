package com.nostra13.universalimageloader.core.download;

import android.content.Context;
import android.net.Uri;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.OkUrlFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

public class OkHttpImageDownloader extends BaseImageDownloader {

    private OkHttpClient client;
    private OkUrlFactory urlFactory;

    public OkHttpImageDownloader(Context context, int connectTimeoutSeconds, int readTimeOutSeconds) {
        super(context);
        this.client = new OkHttpClient();
        client.setReadTimeout(readTimeOutSeconds, TimeUnit.SECONDS);
        client.setConnectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS);
        urlFactory = new OkUrlFactory(client);
    }

    @Override
    protected HttpURLConnection createConnection(String url, Object extra) throws IOException {
        String encodedUrl = Uri.encode(url, ALLOWED_URI_CHARS);

        final HttpURLConnection connection = urlFactory.open(new URL(encodedUrl));
        connection.setConnectTimeout(this.connectTimeout);
        connection.setReadTimeout(this.readTimeout);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod("GET");

        return connection;
    }
}
