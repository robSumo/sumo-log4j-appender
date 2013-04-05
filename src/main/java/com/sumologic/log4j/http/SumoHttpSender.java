package com.sumologic.log4j.http;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.helpers.LogLog;

import java.io.IOException;

/**
 * Author: Jose Muniz (jose@sumologic.com)
 * Date: 4/4/13
 * Time: 8:16 PM
 */
public class SumoHttpSender {

    private long retryInterval = 10000L;

    private String url = null;
    private int connectionTimeout = 1000;
    private int socketTimeout = 60000;
    private HttpClient httpClient = null;


    public void setRetryInterval(long retryInterval) {
        this.retryInterval = retryInterval;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    public boolean isInitialized() {
        return httpClient != null;
    }

    public void init() {
        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, connectionTimeout);
        HttpConnectionParams.setSoTimeout(params, socketTimeout);
        httpClient = new DefaultHttpClient(new ThreadSafeClientConnManager(), params);
    }

    public void close() {
        httpClient.getConnectionManager().shutdown();
        httpClient = null;
    }

    public void send(String body, String name) {
        keepTrying(body, name);
    }

    private void keepTrying(String body, String name) {
        boolean success = false;
        do {
            try {
                trySend(body, name);
                success = true;
            } catch (Exception e) {
                try {
                    Thread.sleep(retryInterval);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }
        } while (! success);
    }

    private void trySend(String body, String name) {
        HttpPost post = null;
        try {
            post = new HttpPost(url);
            post.setHeader("X-Sumo-Name", name);
            post.setEntity(new StringEntity(body, HTTP.PLAIN_TEXT_TYPE, HTTP.UTF_8));
            HttpResponse response = httpClient.execute(post);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                LogLog.warn(String.format("Received HTTP error from Sumo Service: %d", statusCode));
            }
            //need to consume the body if you want to re-use the connection.
            LogLog.debug("Successfully sent log request to Sumo Logic");
            EntityUtils.consume(response.getEntity());
        } catch (IOException e) {
            LogLog.warn("Could not send log to Sumo Logic", e);
            try { post.abort(); } catch (Exception ignore) {}
        }
    }

}
