package com.nostra13.universalimageloader.core.assist.io;

import android.util.Log;

import com.nostra13.universalimageloader.core.assist.pool.ByteArrayPool;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

public class RecyclableBufferedInputStream extends BufferedInputStream {

    public RecyclableBufferedInputStream(InputStream in) {
        this(in, ByteArrayPool.DEFAULT_BUFFER_SIZE);
    }

    public RecyclableBufferedInputStream(InputStream in, int size) {
        super(in, 1);
        init(size);
    }

    private void init(int size) {
        buf = ByteArrayPool.acquire(size);
    }

    @Override
    public void close() throws IOException {
        ByteArrayPool.release(buf);
        super.close();
    }

    @Override
    public void finalize() throws Throwable {
        try {
            if (buf != null) {
                ByteArrayPool.release(buf);
                Log.e(ByteArrayPool.TAG, "RecyclableBufferedInputStream stream leak!");
            }
        } finally {
            super.finalize();
        }
    }
}