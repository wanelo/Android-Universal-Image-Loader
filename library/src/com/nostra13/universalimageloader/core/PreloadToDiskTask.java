/*******************************************************************************
 * Copyright 2011-2013 Sergey Tarasevich
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.nostra13.universalimageloader.core;

import android.graphics.Bitmap;
import android.os.Handler;
import android.view.View;

import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.assist.FailReason.FailType;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.assist.ImageSize;
import com.nostra13.universalimageloader.core.assist.ViewScaleType;
import com.nostra13.universalimageloader.core.decode.ImageDecoder;
import com.nostra13.universalimageloader.core.decode.ImageDecodingInfo;
import com.nostra13.universalimageloader.core.download.ImageDownloader;
import com.nostra13.universalimageloader.core.download.ImageDownloader.Scheme;
import com.nostra13.universalimageloader.core.imageaware.ImageAware;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;
import com.nostra13.universalimageloader.core.listener.ImageLoadingProgressListener;
import com.nostra13.universalimageloader.utils.IoUtils;
import com.nostra13.universalimageloader.utils.L;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

final class PreloadToDiskTask implements Runnable, IoUtils.CopyListener {

    private static final String LOG_WAITING_FOR_RESUME = "Preload ImageLoader is paused. Waiting...  [%s]";
    private static final String LOG_RESUME_AFTER_PAUSE = "Preload .. Resume loading [%s]";
    private static final String LOG_DELAY_BEFORE_LOADING = "Preload Delay %d ms before loading...  [%s]";
    private static final String LOG_START_DISPLAY_IMAGE_TASK = "Start preload image task [%s]";
    private static final String LOG_LOAD_IMAGE_FROM_NETWORK = "PreLoad image from network [%s]";
    private static final String LOG_RESIZE_CACHED_IMAGE_FILE = "Preload Resize image in disk cache [%s]";
    private static final String LOG_CACHE_IMAGE_ON_DISK = "Preload Cache image on disk [%s]";
    private static final String DIDNT_LOG_CACHE_IMAGE_ON_DISK = "DID NOT Preload Cache image on disk [%s]";
    private static final String LOG_PROCESS_IMAGE_BEFORE_CACHE_ON_DISK = "Preload Process image before cache on disk [%s]";
    private static final String LOG_TASK_INTERRUPTED = "Preload Task was interrupted [%s]";

    private static final String ERROR_PROCESSOR_FOR_DISK_CACHE_NULL = "Bitmap processor for disk cache returned null [%s]";

    private final ImageLoaderEngine engine;
    private final ImageLoadingInfo imageLoadingInfo;
    private final Handler handler;

    // Helper references
    private final ImageLoaderConfiguration configuration;
    private final ImageDownloader downloader;
    private final ImageDownloader networkDeniedDownloader;
    private final ImageDownloader slowNetworkDownloader;
    private final ImageDecoder decoder;
    final String uri;
    private final String memoryCacheKey;
    final ImageAware imageAware;
    final DisplayImageOptions options;
    final ImageLoadingListener listener;
    final ImageLoadingProgressListener progressListener;

    public PreloadToDiskTask(ImageLoaderEngine engine, ImageLoadingInfo imageLoadingInfo, Handler handler) {
        this.engine = engine;
        this.imageLoadingInfo = imageLoadingInfo;
        this.handler = handler;

        configuration = engine.configuration;
        downloader = configuration.downloader;
        networkDeniedDownloader = configuration.networkDeniedDownloader;
        slowNetworkDownloader = configuration.slowNetworkDownloader;
        decoder = configuration.decoder;
        uri = imageLoadingInfo.uri;
        memoryCacheKey = imageLoadingInfo.memoryCacheKey;
        imageAware = imageLoadingInfo.imageAware;
        options = imageLoadingInfo.options;
        listener = imageLoadingInfo.listener;
        progressListener = imageLoadingInfo.progressListener;
    }

    @Override
    public void run() {
        if(!options.isCacheOnDisk()) {
            cleanPreloadLock();
            return;
        }

        if (waitIfPaused()) {
            cleanPreloadLock();
            return;
        }

        if(engine.getPausedPreload().get()) {
            cleanPreloadLock();
            return;
        }

        if (delayIfNeed()) {
            cleanPreloadLock();
            return;
        }

        File file = configuration.diskCache.get(uri);

        boolean cachedOnDisk = (file != null) && file.exists();
        if(!engine.isPreloadActive(uri) || cachedOnDisk){
            cleanPreloadLock();
            return;
        }

        ReentrantLock loadFromUriLock = imageLoadingInfo.loadFromUriLock;
        L.d(LOG_START_DISPLAY_IMAGE_TASK, memoryCacheKey);
        if (loadFromUriLock.isLocked()) {
            cleanPreloadLock();
            return;
        }

        loadFromUriLock.lock();

        try {
            L.d(LOG_LOAD_IMAGE_FROM_NETWORK, memoryCacheKey);
            tryCacheImageOnDisk();
        } catch (IllegalStateException e) {
            fireFailEvent(FailType.NETWORK_DENIED, null);
        } catch (OutOfMemoryError e) {
            L.e(e);
            fireFailEvent(FailType.OUT_OF_MEMORY, e);
        } catch (Throwable e) {
            L.e(e);
            fireFailEvent(FailType.UNKNOWN, e);
        } finally {
            loadFromUriLock.unlock();
            cleanPreloadLock();
        }
    }

    private void cleanPreloadLock() {
        engine.finishPreload(uri);
    }

    /** @return <b>true</b> - if task should be interrupted; <b>false</b> - otherwise */
    private boolean waitIfPaused() {
        AtomicBoolean pause = engine.getPause();
        if (pause.get()) {
            synchronized (engine.getPauseLock()) {
                if (pause.get()) {
                    L.d(LOG_WAITING_FOR_RESUME, memoryCacheKey);
                    try {
                        engine.getPauseLock().wait();
                    } catch (InterruptedException e) {
                        L.e(LOG_TASK_INTERRUPTED, memoryCacheKey);
                        return true;
                    }
                    L.d(LOG_RESUME_AFTER_PAUSE, memoryCacheKey);
                }
            }
        }
        return checkTaskIsInterrupted();
    }

    /** @return <b>true</b> - if task should be interrupted; <b>false</b> - otherwise */
    private boolean delayIfNeed() {
        if (options.shouldDelayBeforeLoading()) {
            L.d(LOG_DELAY_BEFORE_LOADING, options.getDelayBeforeLoading(), memoryCacheKey);
            try {
                Thread.sleep(options.getDelayBeforeLoading());
            } catch (InterruptedException e) {
                L.e(LOG_TASK_INTERRUPTED, memoryCacheKey);
                return true;
            }
            return checkTaskIsInterrupted();
        }
        return false;
    }

    private boolean checkTaskIsInterrupted() {
        boolean interrupted = Thread.currentThread().isInterrupted();
        if (interrupted) L.d(LOG_TASK_INTERRUPTED);
        return interrupted;
    }

    /** @return <b>true</b> - if image was downloaded successfully; <b>false</b> - otherwise */
    private boolean tryCacheImageOnDisk() throws TaskCancelledException {
        boolean loaded;
        try {
            loaded = downloadImage();
            if (loaded) {
                L.d(LOG_CACHE_IMAGE_ON_DISK, memoryCacheKey);
                int width = configuration.maxImageWidthForDiskCache;
                int height = configuration.maxImageHeightForDiskCache;
                if (width > 0 || height > 0) {
                    L.d(LOG_RESIZE_CACHED_IMAGE_FILE, memoryCacheKey);
                    resizeAndSaveImage(width, height); // TODO : process boolean result
                }
            }
        } catch (Throwable e) {
            L.e(e);
            loaded = false;
        }

        if(!loaded) {
            L.d(DIDNT_LOG_CACHE_IMAGE_ON_DISK, memoryCacheKey);
        }
        
        return loaded;
    }

    private boolean downloadImage() throws IOException {
        InputStream is = getDownloader().getStream(uri, options.getExtraForDownloader());
        return configuration.diskCache.save(uri, is, this);
    }

    /** Decodes image file into Bitmap, resize it and save it back */
    private boolean resizeAndSaveImage(int maxWidth, int maxHeight) throws Throwable {
        // Decode image file, compress and re-save it
        boolean saved = false;
        File targetFile = configuration.diskCache.get(uri);
        if (targetFile != null && targetFile.exists()) {
            ImageSize targetImageSize = new ImageSize(maxWidth, maxHeight);
            DisplayImageOptions specialOptions = new DisplayImageOptions.Builder().cloneFrom(options)
                    .imageScaleType(ImageScaleType.IN_SAMPLE_INT).build();
            ImageDecodingInfo decodingInfo = new ImageDecodingInfo(memoryCacheKey,
                    Scheme.FILE.wrap(targetFile.getAbsolutePath()), uri, targetImageSize, ViewScaleType.FIT_INSIDE,
                    getDownloader(), specialOptions);
            Bitmap bmp = decoder.decode(decodingInfo);
            if (bmp != null && configuration.processorForDiskCache != null) {
                L.d(LOG_PROCESS_IMAGE_BEFORE_CACHE_ON_DISK, memoryCacheKey);
                bmp = configuration.processorForDiskCache.process(bmp);
                if (bmp == null) {
                    L.e(ERROR_PROCESSOR_FOR_DISK_CACHE_NULL, memoryCacheKey);
                }
            }
            if (bmp != null) {
                saved = configuration.diskCache.save(uri, bmp);
                bmp.recycle();
                decoder.release(bmp, options);
            }
        }
        return saved;
    }

    @Override
    public boolean onBytesCopied(int current, int total) {
        return true;
    }

    private void fireFailEvent(final FailType failType, final Throwable failCause) {
        if (isTaskInterrupted()) return;
        Runnable r = new Runnable() {
            @Override
            public void run() {
                if (options.shouldShowImageOnFail()) {
                    imageAware.setImageDrawable(options.getImageOnFail(configuration.resources));
                }

                View view = imageAware != null ? imageAware.getWrappedView() : null;
                if(listener != null) {
                    listener.onLoadingFailed(uri, view, new FailReason(failType, failCause));
                }
            }
        };
        runTask(r, false, handler, engine);
    }

    private ImageDownloader getDownloader() {
        ImageDownloader d;
        if (engine.isNetworkDenied()) {
            d = networkDeniedDownloader;
        } else if (engine.isSlowNetwork()) {
            d = slowNetworkDownloader;
        } else {
            d = downloader;
        }
        return d;
    }

    /** @return <b>true</b> - if current task was interrupted; <b>false</b> - otherwise */
    private boolean isTaskInterrupted() {
        if (Thread.interrupted()) {
            L.d(LOG_TASK_INTERRUPTED, memoryCacheKey);
            return true;
        }
        return false;
    }

    String getLoadingUri(){
        return uri;
    }

    static void runTask(Runnable r, boolean sync, Handler handler, ImageLoaderEngine engine) {
        if (sync) {
            r.run();
        } else if (handler == null) {
            engine.fireCallback(r);
        } else {
            handler.post(r);
        }
    }

    /**
     * Exceptions for case when task is cancelled (thread is interrupted, image view is reused for another task, view is
     * collected by GC).
     *
     * @author Sergey Tarasevich (nostra13[at]gmail[dot]com)
     * @since 1.9.1
     */
    class TaskCancelledException extends Exception {
    }
}
