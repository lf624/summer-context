package com.learn.summer.io;

import java.io.IOException;
import java.io.InputStream;

public interface InputStreamCallback<T> {
    T doWithInputStream(InputStream input) throws IOException;
}
