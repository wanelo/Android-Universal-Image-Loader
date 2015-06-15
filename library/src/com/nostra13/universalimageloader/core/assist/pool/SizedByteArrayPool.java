package com.nostra13.universalimageloader.core.assist.pool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class SizedByteArrayPool {
    private static final String TAG = ByteArrayPool.class.getSimpleName();

    public static final int DEFAULT_BUFFER_SIZE = 32 * 1024; // 32 Kb

    private static List<byte[]> mBuffersByLastUse = new LinkedList<>();
    private static List<byte[]> mBuffersBySize = new ArrayList<>(64);
    private static int mCurrentSize = 0;
    private static int mSizeLimit = 2 * 1024 * 1024; //2MB

    /** Compares buffers by size */
    protected static final Comparator<byte[]> BUF_COMPARATOR = new Comparator<byte[]>() {
        @Override
        public int compare(byte[] lhs, byte[] rhs) {
            return lhs.length - rhs.length;
        }
    };

    public static synchronized byte[] acquire(int len) {
        for (int i = 0; i < mBuffersBySize.size(); i++) {
            byte[] buf = mBuffersBySize.get(i);
            if (buf.length >= len) {
                mCurrentSize -= buf.length;
                mBuffersBySize.remove(i);
                mBuffersByLastUse.remove(buf);
                return buf;
            }
        }
        return new byte[len];
    }

    public static synchronized void release(byte[] buf) {
        if (buf == null || buf.length > mSizeLimit || buf.length < 2) {
            return;
        }

        mBuffersByLastUse.add(buf);
        int pos = Collections.binarySearch(mBuffersBySize, buf, BUF_COMPARATOR);
        if (pos < 0) {
            pos = -pos - 1;
        }
        mBuffersBySize.add(pos, buf);
        mCurrentSize += buf.length;
        //Arrays.fill(byteArray, (byte) 0);
        trim();
    }

    private static synchronized void trim() {
        while (mCurrentSize > mSizeLimit) {
            byte[] buf = mBuffersByLastUse.remove(0);
            mBuffersBySize.remove(buf);
            mCurrentSize -= buf.length;
        }
    }

    public static synchronized void clear() {
        mBuffersByLastUse.clear();
        mBuffersBySize.clear();
        mCurrentSize = 0;
    }
}
