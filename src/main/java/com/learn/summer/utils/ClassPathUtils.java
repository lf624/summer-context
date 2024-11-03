package com.learn.summer.utils;

import com.learn.summer.io.InputStreamCallback;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public class ClassPathUtils {
    public static <T> T readInputStream(String path, InputStreamCallback<T> inputStreamCallback) {
        if(path.startsWith("/"))
            path = path.substring(1);
        try(InputStream input = getClassLoader().getResourceAsStream(path)) {
            if(input == null)
                throw new FileNotFoundException("File not found in classpath: " + path);
            return inputStreamCallback.doWithInputStream(input);
        }catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String readString(String path) {
        return readInputStream(path, (input) -> {
            byte[] data = input.readAllBytes();
            return new String(data, StandardCharsets.UTF_8);
        });
    }

    static ClassLoader getClassLoader() {
        ClassLoader c1 = null;
        c1 = Thread.currentThread().getContextClassLoader();
        if(c1 == null)
            c1 = ClassPathUtils.class.getClassLoader();
        return c1;
    }
}
