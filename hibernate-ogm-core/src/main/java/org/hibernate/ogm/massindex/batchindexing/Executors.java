/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * JBoss, Home of Professional Open Source
 * Copyright 2013 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.hibernate.ogm.massindex.batchindexing;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.hibernate.search.util.logging.impl.Log;
import org.hibernate.search.util.logging.impl.LoggerFactory;

/**
 * Helper class to create threads;
 * these threads are grouped and named to be identified in a profiler.
 * 
 * @author Sanne Grinovero
 */
public class Executors {

	private static final String THREAD_GROUP_PREFIX = "Hibernate OGM: ";
	public static final int QUEUE_MAX_LENGTH = 1000;

	private static final Log log = LoggerFactory.make();

	/**
	 * Creates a new fixed size ThreadPoolExecutor.
	 * It's using a blockingqueue of maximum 1000 elements and the rejection
	 * policy is set to CallerRunsPolicy for the case the queue is full.
	 * These settings are required to cap the queue, to make sure the
	 * timeouts are reasonable for most jobs.
	 *
	 * @param threads
	 *            the number of threads
	 * @param groupname
	 *            a label to identify the threadpool; useful for profiling.
	 * @return the new ExecutorService
	 */
	public static ThreadPoolExecutor newFixedThreadPool(int threads, String groupname) {
		return newFixedThreadPool( threads, groupname, QUEUE_MAX_LENGTH );
	}

	/**
	 * Creates a new fixed size ThreadPoolExecutor
	 * 
	 * @param threads
	 *            the number of threads
	 * @param groupname
	 *            a label to identify the threadpool; useful for profiling.
	 * @param queueSize
	 *            the size of the queue to store Runnables when all threads are busy
	 * @return the new ExecutorService
	 */
	public static ThreadPoolExecutor newFixedThreadPool(int threads, String groupname, int queueSize) {
		return new ThreadPoolExecutor( threads, threads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(
				queueSize ), new SearchThreadFactory( groupname ), new BlockPolicy() );
	}

	/**
	 * The thread factory, used to customize thread names
	 */
	private static class SearchThreadFactory implements ThreadFactory {

		final ThreadGroup group;
		final AtomicInteger threadNumber = new AtomicInteger( 1 );
		final String namePrefix;

		SearchThreadFactory(String groupname) {
			SecurityManager s = System.getSecurityManager();
			group = ( s != null ) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
			namePrefix = THREAD_GROUP_PREFIX + groupname + "-";
		}

		public Thread newThread(Runnable r) {
			Thread t = new Thread( group, r, namePrefix + threadNumber.getAndIncrement(), 0 );
			return t;
		}

	}

	/**
	 * A handler for rejected tasks that will have the caller block until
	 * space is available.
	 */
	public static class BlockPolicy implements RejectedExecutionHandler {

		/**
		 * Creates a <tt>BlockPolicy</tt>.
		 */
		public BlockPolicy() {
		}

		/**
		 * Puts the Runnable to the blocking queue, effectively blocking
		 * the delegating thread until space is available.
		 * 
		 * @param r
		 *            the runnable task requested to be executed
		 * @param e
		 *            the executor attempting to execute this task
		 */
		public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
			try {
				e.getQueue().put( r );
			}
			catch ( InterruptedException e1 ) {
				log.interruptedWorkError( r );
				Thread.currentThread().interrupt();
			}
		}
	}

}
