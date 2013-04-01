package com.nostra13.universalimageloader.core.assist.deque;

import java.util.concurrent.locks.ReentrantLock;

public class CyclingLinkedBlockingDeque<T> extends LinkedBlockingDeque<T> {

    private static final int DEFAULT_CAPACITY = 40;

    public CyclingLinkedBlockingDeque() {
        this(DEFAULT_CAPACITY);
    }

    public CyclingLinkedBlockingDeque(int capacity) {
        super(capacity);
    }

    @Override
	public boolean offer(T e) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if(remainingCapacity() < 1) {
                removeFirst();
                return offerLast(e);
            } else {
                return super.offer(e);
            }
        } finally {
            lock.unlock();
        }
	}
}