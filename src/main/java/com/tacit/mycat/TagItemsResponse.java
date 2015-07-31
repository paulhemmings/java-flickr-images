package com.tacit.mycat;

/**
 * Created by paulhemmings on 7/31/15.
 */
public class TagItemsResponse {
    public Items[] items;
    public static class Items {
        public Media media;
        public static class Media {
            public String m;
        }
    }
}
