package com.nostra13.universalimageloader.core.assist.pool;

import android.util.Log;

import java.util.LinkedList;
import java.util.Queue;

public class ByteArrayPool {
    public static final String TAG = ByteArrayPool.class.getSimpleName();

    public static int MAX_SIZE = 20;
    public static final int DEFAULT_BUFFER_SIZE = 32 * 1024; // 32 Kb
    private static final Queue<byte[]> queue = new LinkedList<byte[]>();

    public static synchronized  byte[] acquire(int size) {
        return acquire();
    }

    public static synchronized  byte[] acquire() {
        byte[] result = queue.poll();
        if(result == null) {
            result = new byte[DEFAULT_BUFFER_SIZE];
        }
        return result;
    }

    public static synchronized void release(byte[] byteArray) {
        if(byteArray != null && byteArray.length > 1 && queue.size() < MAX_SIZE) {
            queue.add(byteArray);
        } else  {
            Log.e(TAG, "ByteArray CANNOT be released - " + queue.size() + " - ");
        }
    }

    public static synchronized void clear() {
        queue.clear();
    }

    private static void printStackTrace() {
        StringBuilder str = new StringBuilder();
        str.append("--------------------------------------------------------\n");
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for(StackTraceElement el: stackTrace) {
            str.append("\t");
            str.append(el.toString());
            str.append("\n");
        }
        str.append("++++++++++++++++++++++++++++++++++++++++++++++++++++++++\n");
        Log.d("Pool", str.toString());
    }
}