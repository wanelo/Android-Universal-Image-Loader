package com.nostra13.universalimageloader.core.assist.io;

import android.util.Log;

import com.nostra13.universalimageloader.core.assist.pool.ByteArrayPool;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class RecyclableBufferedOutputStream extends BufferedOutputStream {

    public RecyclableBufferedOutputStream(OutputStream out) {
        this(out, ByteArrayPool.DEFAULT_BUFFER_SIZE);
    }

    public RecyclableBufferedOutputStream(OutputStream out, int size) {
        super(out, 1);
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
                Log.e(ByteArrayPool.TAG, "RecyclableBufferedOutputStream stream leak!");
            }
        } finally {
            super.finalize();
        }
    }
}
