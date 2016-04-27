package com.fezrestia.android.application.unlimitedburstshot.storage;

import java.util.ArrayList;
import java.util.List;

public class RingBuffer {
    // Buffer list.
    private List<byte[]> mBufferList = new ArrayList<byte[]>();

    // Current index.
    private int mCurrentBufferIndex = 0;

    // CONSTRUCTOR.
    public RingBuffer(int numberOfBuffer, int lengthOfOneBuffer) {
        // Clear.
        mBufferList.clear();

        // Create and add buffers.
        for (int i = 0; i < numberOfBuffer; ++i) {
            byte[] frame = new byte[lengthOfOneBuffer];
            mBufferList.add(frame);
        }
    }

    public synchronized void release() {
        mBufferList.clear();
    }

    public synchronized byte[] getCurrent() {
        return mBufferList.get(mCurrentBufferIndex);
    }

    public synchronized byte[] getNext() {
        // Return next item.
        return mBufferList.get(getNextIndex());
    }

    public synchronized void increment() {
        // Change to next index.
        mCurrentBufferIndex = getNextIndex();
    }

    private int getNextIndex() {
        if ((mBufferList.size() - 1) <= mCurrentBufferIndex) {
            // Current index is now on the end of list. Return 0.
            return 0;
        } else {
            return mCurrentBufferIndex + 1;
        }
    }
}
