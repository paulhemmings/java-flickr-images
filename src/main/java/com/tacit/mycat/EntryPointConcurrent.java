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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static spark.Spark.get;

public class EntryPointConcurrent {

    public static void main(String[] args) {
        get("/images/:filter", "text/html", (request, response) -> {
            return buildResponse(request.params(":filter"));
        });
    }

    private static List<String> buildResponse(String request) {

        // split filter into stream of tags
        Stream<String> filters = buildTagStream(request);

        // create a flickr URL for each tag
        List<String> urls = filters.map(EntryPointConcurrent::buildUrl).collect(Collectors.toList());

        // build an executor for each url
        Executor executor = getExecutor(urls.size());

        // make concurrent requests for all those urls
        Stream<TagItemsResponse> taggedItems = requestTaggedItems(urls, executor);

        // create a stream of elements for each of those tagged items
        Stream<String> elements = taggedItems.map(EntryPointConcurrent::buildElementStream).flatMap(x -> x);

        // return as a list
        return elements.collect(Collectors.toList());
    }

    private static Stream<String> buildTagStream(String filter) {
        return Arrays.asList(filter.split(",")).stream();
    }

    private static String buildUrl(String tag) {
        return "http://api.flickr.com/services/feeds/photos_public.gne?tags=" + tag + "&format=json&jsoncallback=?";
    }

    private static String buildElement(TagItemsResponse.Items items) {
        return "<img src='" + items.media.m + "' />";
    }

    private static Stream<String> buildElementStream(TagItemsResponse tagItemsResponse) {
        return Arrays.asList(tagItemsResponse.items).stream().map(EntryPointConcurrent::buildElement);
    }

    private static Stream<TagItemsResponse> requestTaggedItems(List<String> urls, Executor executor) {
        return urls.stream()
                .map(url -> CompletableFuture.supplyAsync(() -> callUrl(url), executor))
                .map(CompletableFuture::join);
    }

    private static Executor getExecutor(int size) {

        return Executors.newFixedThreadPool(Math.min(size * 2, 100), runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });
    }

    private static TagItemsResponse callUrl(String url) {
        try {
            final HttpUriRequest httpGet = new HttpGet(url);
            HttpClient httpClient = HttpClients.createDefault();
            HttpResponse response = httpClient.execute(httpGet);
            String responseContent = EntityUtils.toString(response.getEntity());
            responseContent = responseContent.substring(1, responseContent.length()-1);
            return new Gson().fromJson(responseContent, TagItemsResponse.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}
