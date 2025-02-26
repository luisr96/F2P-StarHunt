package com.starhunt;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.Instant;

public class InstantAdapter extends TypeAdapter<Instant> {
    @Override
    public void write(JsonWriter out, Instant instant) throws IOException {
        out.value(instant != null ? instant.toEpochMilli() : null);
    }

    @Override
    public Instant read(JsonReader in) throws IOException {
        return Instant.ofEpochMilli(in.nextLong());
    }
}