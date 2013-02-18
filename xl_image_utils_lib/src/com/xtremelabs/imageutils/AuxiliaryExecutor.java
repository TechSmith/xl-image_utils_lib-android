package com.xtremelabs.imageutils;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.os.AsyncTask;

// TODO Build unit tests
class AuxiliaryExecutor {

	private final ThreadPoolExecutor mExecutor;
	private final AuxiliaryBlockingQueue mQueue;
	private final QueuingMaps mQueuingMaps = new QueuingMaps();

	private AuxiliaryExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, AuxiliaryBlockingQueue queue) {
		mQueue = queue;
		mExecutor = new AuxiliaryThreadPool(corePoolSize, maximumPoolSize, keepAliveTime, unit, mQueue);
	}

	public synchronized void execute(final Prioritizable prioritizable) {
		if (!prioritizable.isCancelled()) {
			mQueuingMaps.put(prioritizable);
			new AsyncTask<Void, Void, Void>() {
				@Override
				protected Void doInBackground(Void... params) {
					mExecutor.execute(prioritizable);
					return null;
				}
			}.execute();
		}
	}

	public synchronized void notifyRequestComplete(Request<?> request) {
		mQueuingMaps.onComplete(request);
	}

	public synchronized boolean cancel(Prioritizable prioritizable) {
		return mQueuingMaps.cancel(prioritizable);
	}

	private synchronized void notifyBeforeExecuteCalled(Runnable r) {
		Prioritizable prioritizable = (Prioritizable) r;
		if (!prioritizable.isCancelled())
			mQueuingMaps.notifyExecuting(prioritizable);
	}

	static class Builder {
		private int mCorePoolSize = 1;
		private int mAdditionalThreads = 0;
		private long mKeepAliveTime = 0;
		private TimeUnit mTimeUnit = TimeUnit.MILLISECONDS;
		private final PriorityAccessor[] mPriorityAccessors;

		public Builder(PriorityAccessor[] accessors) {
			mPriorityAccessors = accessors;
		}

		public Builder setCorePoolSize(int corePoolSize) {
			mCorePoolSize = corePoolSize;
			return this;
		}

		public Builder setNumExtraAvailableThreads(int additionalThreads) {
			mAdditionalThreads = additionalThreads;
			return this;
		}

		public Builder setKeepAliveTime(int keepAliveTime, TimeUnit timeUnit) {
			mKeepAliveTime = keepAliveTime;
			mTimeUnit = timeUnit;
			return this;
		}

		public AuxiliaryExecutor create() {
			AuxiliaryBlockingQueue queue = new AuxiliaryBlockingQueue(mPriorityAccessors);
			AuxiliaryExecutor executor = new AuxiliaryExecutor(mCorePoolSize, mCorePoolSize + mAdditionalThreads, mKeepAliveTime, mTimeUnit, queue);
			return executor;
		}
	}

	private class AuxiliaryThreadPool extends ThreadPoolExecutor {
		private AuxiliaryThreadPool(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
			super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
		}

		@Override
		protected void beforeExecute(Thread t, Runnable r) {
			notifyBeforeExecuteCalled(r);

			super.beforeExecute(t, r);
		}
	}

	public synchronized void notifySwap(final CacheKey cacheKey, final int targetIndex, final int memoryIndex, final int diskIndex) {
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				mQueue.notifySwap(cacheKey, targetIndex, memoryIndex, diskIndex);
				return null;
			}
		}.execute();
	}
}
