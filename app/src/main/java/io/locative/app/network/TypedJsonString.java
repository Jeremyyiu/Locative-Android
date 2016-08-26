package io.locative.app.network;

import retrofit.mime.TypedString;

/**
 * Created by Jasper De Vrient on 25/08/2016.
 */
public class TypedJsonString extends TypedString {
    public TypedJsonString(String body) {
        super(body);
    }

    @Override public String mimeType() {
        return "application/json";
    }
}