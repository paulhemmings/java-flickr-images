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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static spark.Spark.get;

public class EntryPoint {

    public static void main(String[] args) {
      get("/images/:filter", "text/html", (request, response) -> {
            return buildResponse(request.params(":filter"));
      });
    }

    private static List<String> buildResponse(String filter) {
        return  buildTagStream(filter)                      // split filter into stream of tags
                .map(EntryPoint::buildUrl)                  // create a flickr URL for each tag
                .map(EntryPoint::callUrl)                   // call that url, returns a TagItemsResponse
                .map(EntryPoint::buildElementStream)        // build an element for each item in tag items
                .flatMap(x -> x)                            // flatten into one map
                .collect(Collectors.toList());              // collect into a list
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
        return Arrays.asList(tagItemsResponse.items).stream()
                .map(EntryPoint::buildElement);
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
