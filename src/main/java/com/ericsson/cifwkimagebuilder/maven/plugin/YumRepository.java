package com.ericsson.cifwkimagebuilder.maven.plugin;


import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class YumRepository {
    private final Log log;

    public YumRepository(final Log log) {
        this.log = log;
    }

    public String buildProductRepo(final String productName, final String productVersion,
                                   final String kgbArtifacts) throws MojoExecutionException {
        final List<BasicNameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair("product", productName));
        postParams.add(new BasicNameValuePair("drop", productVersion));
        String kgbPackageList = "none";
        if (kgbArtifacts != null) {
            kgbPackageList = kgbArtifacts;
        }
        postParams.add(new BasicNameValuePair("addArtifacts", kgbPackageList));
        final String restUrl = PluginProperties.getProperty(Property.REPO_CREATE_URL);
        return this.post(restUrl, postParams);
    }

    public void deleteProductRepo(final String repoUrl) throws MojoExecutionException {
        final List<BasicNameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair("repo", repoUrl));
        final String restUrl = PluginProperties.getProperty(Property.REPO_DELETE_URL);
        this.post(restUrl, postParams);
    }

    private String post(final String restUrl, final List<BasicNameValuePair> params) throws MojoExecutionException {
        final HttpClient client = this.getHttpClient();
        final HttpPost post = new HttpPost(restUrl);
        try {
            post.setEntity(new UrlEncodedFormEntity(params));
            this.log.info("Posting: " + restUrl + " " + params.toString());
            final HttpResponse response = client.execute(post);
            final BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            String line;
            String buffer = "";
            boolean wasError = false;
            while ((line = rd.readLine()) != null) {
                buffer += line + "\n";
                if (line.toLowerCase().contains("error")) {
                    wasError = true;
                }
            }
            if (wasError) {
                throw new MojoExecutionException(buffer);
            }
            return buffer.trim();
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private HttpClient getHttpClient() throws MojoExecutionException {
        final SSLContextBuilder builder = new SSLContextBuilder();
        try {
            builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
            final SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build());
            return HttpClients.custom().setSSLSocketFactory(sslsf).build();
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            throw new MojoExecutionException("Failed to get https client!", e);
        }
    }
}
