package com.tacit.mycat;

import com.google.gson.Gson;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static spark.Spark.get;

public class EntryPointConcurrent {

    public static void main(String[] args) {
        get("/images/:filter", "text/html", (request, response) -> {
            return new EntryPointConcurrent().buildResponse(request.params(":filter"));
        });
    }

    private List<String> buildResponse(String request) throws InterruptedException {

        // split filter into stream of tags
        Stream<String> filters = buildTagStream(request);

        // create a flickr URL for each tag
        List<String> urls = filters.map(this::buildUrl).collect(Collectors.toList());

        // build an executor for each url
        ExecutorService executor = getExecutor(urls.size());

        // make concurrent requests for all those urls
        List<TagItemsResponse> taggedItems = requestTaggedItems(urls, executor);

        // show down the pool (otherwise threads may be leaked when executor drops out of scope)
        executor.shutdown();

        // create a stream of elements for each of those tagged items
        Stream<String> elements = taggedItems.stream().map(this::buildElementStream).flatMap(x -> x);

        // return as a list
        return elements.collect(Collectors.toList());
    }

    private Stream<String> buildTagStream(String filter) {
        return Arrays.asList(filter.split(",")).stream();
    }

    private String buildUrl(String tag) {
        return "http://api.flickr.com/services/feeds/photos_public.gne?tags=" + tag + "&format=json&jsoncallback=?";
    }

    private String buildElement(TagItemsResponse.Items items) {
        return "<img src='" + items.media.m + "' />";
    }

    private Stream<String> buildElementStream(TagItemsResponse tagItemsResponse) {
        return Arrays.asList(tagItemsResponse.items).stream().map(this::buildElement);
    }

    private List<TagItemsResponse> requestTaggedItems(List<String> urls, ExecutorService executor) {
        return urls.stream()
                .map(url -> CompletableFuture.supplyAsync(() -> callUrl(url), executor))
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
    }

    private ExecutorService getExecutor(int size) {

        return Executors.newFixedThreadPool(Math.min(size * 2, 100), runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });
    }

    private TagItemsResponse callUrl(String url) {
        try {
            final HttpUriRequest httpGet = new HttpGet(url);
            HttpClient httpClient = HttpClients.createDefault();
            HttpResponse response = httpClient.execute(httpGet);
            String responseContent = EntityUtils.toString(response.getEntity());
            responseContent = responseContent.substring(1, responseContent.length()-1);
            return new Gson().fromJson(responseContent, TagItemsResponse.class);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

}
