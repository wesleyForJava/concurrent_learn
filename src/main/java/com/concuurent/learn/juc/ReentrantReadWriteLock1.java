package com.concuurent.learn.juc;

import java.util.Collection;



/*
 *
 *
 *
 *
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

/**
 *  @author Wesley
 *  <p>
 *  解决线程安全问题使用ReentrantLock就可以，但是ReentrantLock是独占锁，
 *  某时只有一个线程可以获取该锁，而实际中会有写少读多的场景，
 *  显然ReentrantLock满足不了这个需求，所以ReentrantReadWriteLock应运而生。
 *  ReentrantReadWriteLock采用读写分离的策略，允许多个线程可以同时获取读锁。
 *  </p>
 *  读写锁的内部维护了一个ReadLock和一个WriteLock，它们依赖Sync实现具体功能而Sync继承自AQS，
 *  并且也提供了公平和非公平的实现。下面只介绍非公平的读写锁实现。我们知道AQS中只维护了一个state状态，
 *  而ReentrantReadWriteLock需要维护读状态和写状态，一个state怎么表示写和读两种状态呢？
 *  ReentrantReadWriteLock巧妙地使用state的高16位表示读状态，也就是获取到读锁的次数；
 *  
 */
public class ReentrantReadWriteLock1
        implements ReadWriteLock, java.io.Serializable {
    private static final long serialVersionUID = -6992448646407690164L;
    /** Inner class providing readlock */
    private final ReentrantReadWriteLock1.ReadLock readerLock;
    /** Inner class providing writelock */
    private final ReentrantReadWriteLock1.WriteLock writerLock;
    /** Performs all synchronization mechanics */
    final Sync sync;

    /**
     * Creates a new {@code ReentrantReadWriteLock} with
     * default (nonfair) ordering properties.
     */
    public ReentrantReadWriteLock1() {
        this(false);
    }

    /**
     * Creates a new {@code ReentrantReadWriteLock} with
     * the given fairness policy.
     *
     * @param fair {@code true} if this lock should use a fair ordering policy
     */
    public ReentrantReadWriteLock1(boolean fair) {
        sync = fair ? new FairSync() : new NonfairSync();
        readerLock = new ReadLock(this);
        writerLock = new WriteLock(this);
    }

    public ReentrantReadWriteLock1.WriteLock writeLock() { return writerLock; }
    public ReentrantReadWriteLock1.ReadLock  readLock()  { return readerLock; }

    /**
     * Synchronization implementation for ReentrantReadWriteLock.
     * Subclassed into fair and nonfair versions.
     */
    abstract static class Sync extends AbstractQueuedSynchronizer1 {
        private static final long serialVersionUID = 6317671515068378041L;

        /*
         * Read vs write count extraction constants and functions.
         * Lock state is logically divided into two unsigned shorts:
         * The lower one representing the exclusive (writer) lock hold count,
         * and the upper the shared (reader) hold count.
         */
        /**使用低16位表示获取到写锁的线程的可重入次数。*/
        static final int SHARED_SHIFT   = 16;
        /**共享锁(读锁)单位状态值65535*/
        static final int SHARED_UNIT    = (1 << SHARED_SHIFT);
       /**共享锁线程最大个数65535*/
        static final int MAX_COUNT      = (1 << SHARED_SHIFT) - 1;
		/** 排它锁(写锁)掩码 二进制 15个1 */
        static final int EXCLUSIVE_MASK = (1 << SHARED_SHIFT) - 1;

        /** 返回读锁线程个数  */
        static int sharedCount(int c)    { return c >>> SHARED_SHIFT; }
        /** 返回写锁可重入个数  */
        static int exclusiveCount(int c) { return c & EXCLUSIVE_MASK; }


        static final class HoldCounter {
            int count = 0;
            // Use id, not reference, to avoid garbage retention
            //线程id
            final long tid = getThreadId(Thread.currentThread());
        }

        /**
         * ThreadLocalHoIdCounter继承了ThreadLocal，因而initialValue方法返回一个HoldCounter对象。
         */
        static final class ThreadLocalHoldCounter
            extends ThreadLocal<HoldCounter> {
            public HoldCounter initialValue() {
                return new HoldCounter();
            }
        }

        /**
         *readHolds是ThreadLocal变量，用来存放除去第一个获取读锁线程外的其他线程获取读锁的可重入次数。
         */
        private transient ThreadLocalHoldCounter readHolds;

        /**
         * <p>cachedHoldCounter用来记录最后一个获取读锁的线程获取读锁的可重入次数。</p>
         */
        private transient HoldCounter cachedHoldCounter;

        /**
         *      <p>  其中firstReader用来记录第一个获取到读锁的线程，
         */
        private transient Thread firstReader = null;
        /**
	    *    firstReaderHoldCount则记录第一个获取到读锁的线程获取读锁的可重入次数。
	    */
        private transient int firstReaderHoldCount;

        Sync() {
            readHolds = new ThreadLocalHoldCounter();
            setState(getState()); // ensures visibility of readHolds
        }
           /*
         * Acquires and releases use the same code for fair and
         * nonfair locks, but differ in whether/how they allow barging
         * when queues are non-empty.
         */

        /**
         * Returns true if the current thread, when trying to acquire
         * the read lock, and otherwise eligible to do so, should block
         * because of policy for overtaking other waiting threads.
         */
        abstract boolean readerShouldBlock();

        /**
         * Returns true if the current thread, when trying to acquire
         * the write lock, and otherwise eligible to do so, should block
         * because of policy for overtaking other waiting threads.
         */
        abstract boolean writerShouldBlock();

        /**
          * <p>tryRelease首先通过isHeldExclusively判断是否当前线程是该写锁的持有者，如果不是则抛出异常，否则执行代码(7)，
          * <p> 这说明当前线程持有写锁，持有写锁说明状态值的高16位为0，所以这里nextc值是当线程写锁的剩余可重入次数。
          * <p> 代码（8)判断当前可重入次数是否为0，如果fre巳为true则说明可重入次数为0，所以当前线程会释放写锁，
          * <p>将当前锁的持有者设为null。如果free为false则简单地更新可重入次数。
         */
        //TODO 
        protected final boolean tryRelease(int releases) {
        	//6.看是否是写锁拥有者调用的unlock
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
//            7.获取可重入值
            int nextc = getState() - releases;
            boolean free = exclusiveCount(nextc) == 0;
            //8.如果写锁可重入值为0则释放锁，否则只是简单的更新状态值
            if (free)
                setExclusiveOwnerThread(null);
            setState(nextc);
            return free;
        }

        protected final boolean tryAcquire(int acquires) {
            /*
             * Walkthrough:
             * 1. If read count nonzero or write count nonzero
             *    and owner is a different thread, fail.
             * 2. If count would saturate, fail. (This can only
             *    happen if count is already nonzero.)
             * 3. Otherwise, this thread is eligible for lock if
             *    it is either a reentrant acquire or
             *    queue policy allows it. If so, update state
             *    and set owner.
             */
            Thread current = Thread.currentThread();
            int c = getState();
            int w = exclusiveCount(c);
            //1. c != 0说明读锁或者写锁已经被某个线程获取
            if (c != 0) {
                //2 w = 0说明已经有线程获取了读锁 ||  w!=0并且当前线程不是线程拥有者则返回
                if (w == 0 || current != getExclusiveOwnerThread())
                    return false;
                //3说明当前线程获取了写锁，判断可重入次数
                if (w + exclusiveCount(acquires) > MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
                // 4设置可重入次数
                setState(c + acquires);
                return true;
            }
            //5写一个线程获取写锁
            if (writerShouldBlock() ||
                !compareAndSetState(c, c + acquires))
                return false;
            setExclusiveOwnerThread(current);
            return true;
            
//            代码(l)中，如果当前AQS状态值不为0则说明当前己经有线程获取到了读锁或者写锁。
//            在代码2中，如果w==O说明状态值的低16位为0，而AQS状态值c不为0,则说明高16位不为0，
//            这暗示己经有线程获取了读锁所以直接返回false。而如果w!=O则说明当前已经有线程获取了该写锁，
//            再看当前线程是不是该锁的持有者，如果不是则返回false。执行到代码3，说明当前线程之前已经获取到了该锁，
//            所以判断该线程的可重入次数是不是超过了最大值，是则抛出异常，否则执行代码4，增加当前线程的可重入次数，
//            然后返回true.如果AQS的状态值c等于0则说明目前没有线程获取到读锁和写锁，所以执行代码5。
        }

        protected final boolean tryReleaseShared(int unused) {
            Thread current = Thread.currentThread();
            if (firstReader == current) {
                // assert firstReaderHoldCount > 0;
                if (firstReaderHoldCount == 1)
                    firstReader = null;
                else
                    firstReaderHoldCount--;
            } else {
                HoldCounter rh = cachedHoldCounter;
                if (rh == null || rh.tid != getThreadId(current))
                    rh = readHolds.get();
                int count = rh.count;
                if (count <= 1) {
                    readHolds.remove();
                    if (count <= 0)
                        throw unmatchedUnlockException();
                }
                --rh.count;
            }
//            循环直到自己的读计数-1，CAS更新成功
//            在无限循环里面，首先获取当前AQS状态值并将其保存到变量c，
//            然后变量c被减去一个读计数单位后使用CAS操作更新AQS状态值，
//            如果更新成功则查看当前AQS状态值是否为0，为0则说明当前己经没有读线程占用读锁，
//            则tryReleaseShared返回true。然后会调用doReleaseShared方法释放一个由于获取写锁而被阻塞的线程，
//            如果当前AQS状态值不为0，则说明当前还有其他线程持有了读锁，
//            所以tryReleaseShared返回false。
//            如果tryReleaseShared中的CAS更新AQS状态值失败，则自旋重试直到成功。
            for (;;) {
                int c = getState();
                int nextc = c - SHARED_UNIT;
                if (compareAndSetState(c, nextc))
                    // Releasing the read lock has no effect on readers,
                    // but it may allow waiting writers to proceed if
                    // both read and write locks are now free.
                    return nextc == 0;
            }
        }

        private IllegalMonitorStateException unmatchedUnlockException() {
            return new IllegalMonitorStateException(
                "attempt to unlock read lock, not locked by current thread");
        }

        protected final int tryAcquireShared(int unused) {
        	//1.获取当前线程的状态值
            Thread current = Thread.currentThread();
            int c = getState();
            //2.判断是否被写锁占用
            if (exclusiveCount(c) != 0 &&
                getExclusiveOwnerThread() != current)
                return -1;
            //3.获取读锁计数
            int r = sharedCount(c);
            //4.尝试获取锁，多个读线程只有一个会成功，不成功的进入fullTryAcquireShared()方法进行重试
            if (!readerShouldBlock() &&
                r < MAX_COUNT &&
                compareAndSetState(c, c + SHARED_UNIT)) {
            	//5.第一个线程获取读锁
                if (r == 0) {
                    firstReader = current;
                    firstReaderHoldCount = 1;
//               6.如果当前线程是第一个获取读锁的线程
                } else if (firstReader == current) {
                    firstReaderHoldCount++;
                } else {
                	//7.记录最后一个获取读锁的线程或记录其他线程读锁的可重入次数
                    HoldCounter rh = cachedHoldCounter;
                    if (rh == null || rh.tid != getThreadId(current))
                        cachedHoldCounter = rh = readHolds.get();
                    else if (rh.count == 0)
                        readHolds.set(rh);
                    rh.count++;
                }
                return 1;
            }
            //8.类似TryAcquireShared但是是自旋获取
            return fullTryAcquireShared(current);
//            如上代码首先获取了当前AQS的状态值，然后代码2查看是否有其他线程获取到了写锁，
//            如果是则直接返回-1，而后调用AQS的doAcquireShared方法把当前线程放入AQS阻塞队列。
//            如果当前要获取读锁的线程己经持有了写锁，则也可以获取读锁。但是需要注意，
//            当一个线程先获取了写锁，然后获取了读锁处理事情完毕后，要记得把读锁和写锁都释放掉，
//            不能只释放写锁。否则执行代码（3），得到获取到的读锁的个数，
//            到这里说明目前没有线程获取到写锁，但是可能有线程持有读锁，然后执行代码（4）。
//             如果队列里面存在一个元素，则判断第一个元素是不是正在尝试获取写锁，
//             如果不是，则当前线程判断当前获取读锁的线程是否达到了最大值。
//             最后执行CAS操作将AQS状态值的高16位值增加1、
//             代码5、6记录
//             第一个获取读锁的线程并统计该线程获取读锁的可重入数。
//             代码7使用cacheHoldCounter记录最后一个获取到读锁的线程和该线程获取读锁的可重入数，
//             readHolds记录了当前线程获取读锁的可重入数。
        }

        /**
         * fullTryAcquireShared的代码与tryAcquireShared类似，它们的不同之处在于，前者通过循环自旋获取。
         */
        final int fullTryAcquireShared(Thread current) {
            /*
             * This code is in part redundant with that in
             * tryAcquireShared but is simpler overall by not
             * complicating tryAcquireShared with interactions between
             * retries and lazily reading hold counts.
             */
            HoldCounter rh = null;
            for (;;) {
                int c = getState();
                if (exclusiveCount(c) != 0) {
                    if (getExclusiveOwnerThread() != current)
                        return -1;
                    // else we hold the exclusive lock; blocking here
                    // would cause deadlock.
                } else if (readerShouldBlock()) {
                    // Make sure we're not acquiring read lock reentrantly
                    if (firstReader == current) {
                        // assert firstReaderHoldCount > 0;
                    } else {
                        if (rh == null) {
                            rh = cachedHoldCounter;
                            if (rh == null || rh.tid != getThreadId(current)) {
                                rh = readHolds.get();
                                if (rh.count == 0)
                                    readHolds.remove();
                            }
                        }
                        if (rh.count == 0)
                            return -1;
                    }
                }
                if (sharedCount(c) == MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
                if (compareAndSetState(c, c + SHARED_UNIT)) {
                    if (sharedCount(c) == 0) {
                        firstReader = current;
                        firstReaderHoldCount = 1;
                    } else if (firstReader == current) {
                        firstReaderHoldCount++;
                    } else {
                        if (rh == null)
                            rh = cachedHoldCounter;
                        if (rh == null || rh.tid != getThreadId(current))
                            rh = readHolds.get();
                        else if (rh.count == 0)
                            readHolds.set(rh);
                        rh.count++;
                        cachedHoldCounter = rh; // cache for release
                    }
                    return 1;
                }
            }
        }

        /**
                   * 与tryAcquire方法类似，这里不再讲述，不同在于这里使用的是非公平策略。
         */
        final boolean tryWriteLock() {
            Thread current = Thread.currentThread();
            int c = getState();
            if (c != 0) {
                int w = exclusiveCount(c);
                if (w == 0 || current != getExclusiveOwnerThread())
                    return false;
                if (w == MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
            }
            if (!compareAndSetState(c, c + 1))
                return false;
            setExclusiveOwnerThread(current);
            return true;
        }

        /**
         * Performs tryLock for read, enabling barging in both modes.
         * This is identical in effect to tryAcquireShared except for
         * lack of calls to readerShouldBlock.
         */
        final boolean tryReadLock() {
            Thread current = Thread.currentThread();
            for (;;) {
                int c = getState();
                if (exclusiveCount(c) != 0 &&
                    getExclusiveOwnerThread() != current)
                    return false;
                int r = sharedCount(c);
                if (r == MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
                if (compareAndSetState(c, c + SHARED_UNIT)) {
                    if (r == 0) {
                        firstReader = current;
                        firstReaderHoldCount = 1;
                    } else if (firstReader == current) {
                        firstReaderHoldCount++;
                    } else {
                        HoldCounter rh = cachedHoldCounter;
                        if (rh == null || rh.tid != getThreadId(current))
                            cachedHoldCounter = rh = readHolds.get();
                        else if (rh.count == 0)
                            readHolds.set(rh);
                        rh.count++;
                    }
                    return true;
                }
            }
        }

        protected final boolean isHeldExclusively() {
            // While we must in general read state before owner,
            // we don't need to do so to check if current thread is owner
            return getExclusiveOwnerThread() == Thread.currentThread();
        }

        // Methods relayed to outer class

        final ConditionObject newCondition() {
            return new ConditionObject();
        }

        final Thread getOwner() {
            // Must read state before owner to ensure memory consistency
            return ((exclusiveCount(getState()) == 0) ?
                    null :
                    getExclusiveOwnerThread());
        }

        final int getReadLockCount() {
            return sharedCount(getState());
        }

        final boolean isWriteLocked() {
            return exclusiveCount(getState()) != 0;
        }

        final int getWriteHoldCount() {
            return isHeldExclusively() ? exclusiveCount(getState()) : 0;
        }

        final int getReadHoldCount() {
            if (getReadLockCount() == 0)
                return 0;

            Thread current = Thread.currentThread();
            if (firstReader == current)
                return firstReaderHoldCount;

            HoldCounter rh = cachedHoldCounter;
            if (rh != null && rh.tid == getThreadId(current))
                return rh.count;

            int count = readHolds.get().count;
            if (count == 0) readHolds.remove();
            return count;
        }

        /**
         * Reconstitutes the instance from a stream (that is, deserializes it).
         */
        private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
            s.defaultReadObject();
            readHolds = new ThreadLocalHoldCounter();
            setState(0); // reset to unlocked state
        }

        final int getCount() { return getState(); }
    }

    /**
     * Nonfair version of Sync
     */
    static final class NonfairSync extends Sync {
        private static final long serialVersionUID = -8159625535654395037L;
       
        //非公平锁writerShouldBlock方法实现
        final boolean writerShouldBlock() {
            return false; // writers can always barge
//            如果代码对于非公平来说总是返回false，则说明代码tryAcquire方法中代码5抢占式执行CAS尝试获取写锁，
//            获取成功则设置当前锁的持有者为当前线程并返回true，
//            否则返回false。
        }
        final boolean readerShouldBlock() {
            /* As a heuristic to avoid indefinite writer starvation,
             * block if the thread that momentarily appears to be head
             * of queue, if one exists, is a waiting writer.  This is
             * only a probabilistic effect since a new reader will not
             * block if there is a waiting writer behind other enabled
             * readers that have not yet drained from the queue.
             */
            return apparentlyFirstQueuedIsExclusive();
        }
    }

    /**
     * Fair version of Sync
     */
    static final class FairSync extends Sync {
        private static final long serialVersionUID = -2274990926593161451L;
        /**
	         * 这里还是使用hasQueuedPredecessors来判断当前线程节点是否有前驱节点，
	         *   如果有则当前线程放弃获取写锁的权限，直接返回false。
         */
        final boolean writerShouldBlock() {
            return hasQueuedPredecessors();
        }
        
        final boolean readerShouldBlock() {
            return hasQueuedPredecessors();
        }
    }

    /**
     * The lock returned by method {@link ReentrantReadWriteLock1#readLock}.
     */
    public static class ReadLock implements Lock, java.io.Serializable {
        private static final long serialVersionUID = -5992448646407690164L;
        private final Sync sync;

        /**
         * Constructor for use by subclasses
         *
         * @param lock the outer lock object
         * @throws NullPointerException if the lock is null
         */
        protected ReadLock(ReentrantReadWriteLock1 lock) {
            sync = lock.sync;
        }

        /**
         * <p> 获取读锁，如果当前没有其他线程持有写锁，则当前线程可以获取读锁，AQS的状态值state的高16位的值增加1，
         * <span>然后方法返回。否则如果其他一个线程持有写锁，则当前线程会被阻塞
         */
        public void lock() {
            sync.acquireShared(1);
        }

        /**
	          * 类似于lock()方法，它的不同之处在于，它会对中断进行响应，
	          * 也就是当其线程调用了该线程的interrupt()方法中断了当前线程，
	          * 当前线程会抛出异常InterruptedException异常。
         */
        public void lockInterruptibly() throws InterruptedException {
            sync.acquireSharedInterruptibly(1);
        }

        /**
	         * 尝试获取写锁，如果当前没有其他线程有写锁或者读锁，则当前线程获取写锁会成功，
	         * 然后返回true。如果当前己经有其他线程持有写锁或者读锁则该方法直接返回false,
	         * 且当前线程并不会被阻塞。如果当前线程已经持有了该写锁则简单增加AQS的态值后直接返回true
	     */
        public boolean tryLock() {
            return sync.tryReadLock();
        }

        /**
                 * 与tryAcquire()的不同之处在于，多了超时时间参数，如果尝试获取写锁失败则会把当前线程挂起指定时间，
                 * 待超时时间到后当前线程被激活，如果还是没有获取到写锁则返回false。另外，该方法会对中断进行响应，
                 * 也就是当其他线程调用了该线程的interrupt()方法中断了当前线程时，当前线程抛出InterruptedException异常。
         */
        public boolean tryLock(long timeout, TimeUnit unit)
                throws InterruptedException {
            return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
        }

        /**
         * Attempts to release this lock.
         *
         * <p>If the number of readers is now zero then the lock
         * is made available for write lock attempts.
         */
        public void unlock() {
            sync.releaseShared(1);
        }

        /**
         * Throws {@code UnsupportedOperationException} because
         * {@code ReadLocks} do not support conditions.
         *
         * @throws UnsupportedOperationException always
         */
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }

        /**
         * Returns a string identifying this lock, as well as its lock state.
         * The state, in brackets, includes the String {@code "Read locks ="}
         * followed by the number of held read locks.
         *
         * @return a string identifying this lock, as well as its lock state
         */
        public String toString() {
            int r = sync.getReadLockCount();
            return super.toString() +
                "[Read locks = " + r + "]";
        }
    }

    /**
      * <p>  写锁用WriteLock来实现。
     */
    public static class WriteLock implements Lock, java.io.Serializable {
        private static final long serialVersionUID = -4992448646407690164L;
        private final Sync sync;

        /**
         * Constructor for use by subclasses
         *
         * @param lock the outer lock object
         * @throws NullPointerException if the lock is null
         */
        protected WriteLock(ReentrantReadWriteLock1 lock) {
            sync = lock.sync;
        }

        /**
	         *写 锁是个独占锁，某只有一个线程可以获取该锁。
	         * 如果当前没有线程获取到读锁和写锁，则当前线程可以获取到写锁然后返回。
	         * 如果当前己经有线程获取到读锁和写锁，则当前请求写锁的线程会阻塞挂起。
	         * 另外，写锁是可重入锁，如果当前程己经获取了该锁，
	         * 再次获取只是简单地把可重入次数加1后直接返回。
         */
        public void lock() {
            //调用sync中的acquire方法
        	sync.acquire(1);
//        	lock()内部调用了AQS的acquire方法，其中tryAcquire是ReentrantReadWriteLock内部的sync类重写的
        }
      
        /**
                  *类似于lock()方法，它的不同之处在于，它会对中断进行响应，
	          * 也就是当其线程调用了该线程的interrupt()方法中断了当前线程，
	          * 当前线程会抛出异常InterruptedException异常。
         */
        public void lockInterruptibly() throws InterruptedException {
            sync.acquireInterruptibly(1);
        }

        /**
	         * 尝试获取写锁，如果当前没有其他线程有写锁或者读锁，则当前线程获取写锁会成功，
	         * 然后返回true。如果当前己经有其他线程持有写锁或者读锁则该方法直接返回false,
	         * 且当前线程并不会被阻塞。如果当前线程已经持有了该写锁则简单增加AQS的状态值高16位后直接返回true
	     */
        public boolean tryLock( ) {
            return sync.tryWriteLock();
        }

        /**
         *
                 * 与tryAcquire()的不同之处在于，多了超时时间参数，如果尝试获取读锁失败则会把当前线程挂起指定时间，
                 * 待超时时间到后当前线程被激活，如果还是没有获取到读锁则返回false。另外，该方法会对中断进行响应，
                 * 也就是当其他线程调用了该线程的interrupt()方法中断了当前线程时，当前线程抛出InterruptedException异常。
         */
        public boolean tryLock(long timeout, TimeUnit unit)
                throws InterruptedException {
            return sync.tryAcquireNanos(1, unit.toNanos(timeout));
        }

        /**
                  * 尝试释放锁，如果当前线程持有该锁，调用该方法会让该线程对该线程持有的AQS状态值减1，
                  * 如果减去1后当前状态值为0则当前线程会释放锁，否则仅仅减1而己。
                  * 如果当前线程没有持有该锁而调用了该方法则会抛出IllegalMonitorStateException异常
         */
        public void unlock() {
            sync.release(1);
        }

        /**
         * Returns a {@link Condition} instance for use with this
         * {@link Lock} instance.
         * <p>The returned {@link Condition} instance supports the same
         * usages as do the {@link Object} monitor methods ({@link
         * Object#wait() wait}, {@link Object#notify notify}, and {@link
         * Object#notifyAll notifyAll}) when used with the built-in
         * monitor lock.
         *
         * <ul>
         *
         * <li>If this write lock is not held when any {@link
         * Condition} method is called then an {@link
         * IllegalMonitorStateException} is thrown.  (Read locks are
         * held independently of write locks, so are not checked or
         * affected. However it is essentially always an error to
         * invoke a condition waiting method when the current thread
         * has also acquired read locks, since other threads that
         * could unblock it will not be able to acquire the write
         * lock.)
         *
         * <li>When the condition {@linkplain Condition#await() waiting}
         * methods are called the write lock is released and, before
         * they return, the write lock is reacquired and the lock hold
         * count restored to what it was when the method was called.
         *
         * <li>If a thread is {@linkplain Thread#interrupt interrupted} while
         * waiting then the wait will terminate, an {@link
         * InterruptedException} will be thrown, and the thread's
         * interrupted status will be cleared.
         *
         * <li> Waiting threads are signalled in FIFO order.
         *
         * <li>The ordering of lock reacquisition for threads returning
         * from waiting methods is the same as for threads initially
         * acquiring the lock, which is in the default case not specified,
         * but for <em>fair</em> locks favors those threads that have been
         * waiting the longest.
         *
         * </ul>
         *
         * @return the Condition object
         */
        public Condition newCondition() {
            return sync.newCondition();
        }

        /**
         * Returns a string identifying this lock, as well as its lock
         * state.  The state, in brackets includes either the String
         * {@code "Unlocked"} or the String {@code "Locked by"}
         * followed by the {@linkplain Thread#getName name} of the owning thread.
         *
         * @return a string identifying this lock, as well as its lock state
         */
        public String toString() {
            Thread o = sync.getOwner();
            return super.toString() + ((o == null) ?
                                       "[Unlocked]" :
                                       "[Locked by thread " + o.getName() + "]");
        }

        /**
         * Queries if this write lock is held by the current thread.
         * Identical in effect to {@link
         * ReentrantReadWriteLock1#isWriteLockedByCurrentThread}.
         *
         * @return {@code true} if the current thread holds this lock and
         *         {@code false} otherwise
         * @since 1.6
         */
        public boolean isHeldByCurrentThread() {
            return sync.isHeldExclusively();
        }

        /**
         * Queries the number of holds on this write lock by the current
         * thread.  A thread has a hold on a lock for each lock action
         * that is not matched by an unlock action.  Identical in effect
         * to {@link ReentrantReadWriteLock1#getWriteHoldCount}.
         *
         * @return the number of holds on this lock by the current thread,
         *         or zero if this lock is not held by the current thread
         * @since 1.6
         */
        public int getHoldCount() {
            return sync.getWriteHoldCount();
        }
    }

    // Instrumentation and status

    /**
     * Returns {@code true} if this lock has fairness set true.
     *
     * @return {@code true} if this lock has fairness set true
     */
    public final boolean isFair() {
        return sync instanceof FairSync;
    }

    /**
     * Returns the thread that currently owns the write lock, or
     * {@code null} if not owned. When this method is called by a
     * thread that is not the owner, the return value reflects a
     * best-effort approximation of current lock status. For example,
     * the owner may be momentarily {@code null} even if there are
     * threads trying to acquire the lock but have not yet done so.
     * This method is designed to facilitate construction of
     * subclasses that provide more extensive lock monitoring
     * facilities.
     *
     * @return the owner, or {@code null} if not owned
     */
    protected Thread getOwner() {
        return sync.getOwner();
    }

    /**
     * Queries the number of read locks held for this lock. This
     * method is designed for use in monitoring system state, not for
     * synchronization control.
     * @return the number of read locks held
     */
    public int getReadLockCount() {
        return sync.getReadLockCount();
    }

    /**
     * Queries if the write lock is held by any thread. This method is
     * designed for use in monitoring system state, not for
     * synchronization control.
     *
     * @return {@code true} if any thread holds the write lock and
     *         {@code false} otherwise
     */
    public boolean isWriteLocked() {
        return sync.isWriteLocked();
    }

    /**
     * Queries if the write lock is held by the current thread.
     *
     * @return {@code true} if the current thread holds the write lock and
     *         {@code false} otherwise
     */
    public boolean isWriteLockedByCurrentThread() {
        return sync.isHeldExclusively();
    }

    /**
     * Queries the number of reentrant write holds on this lock by the
     * current thread.  A writer thread has a hold on a lock for
     * each lock action that is not matched by an unlock action.
     *
     * @return the number of holds on the write lock by the current thread,
     *         or zero if the write lock is not held by the current thread
     */
    public int getWriteHoldCount() {
        return sync.getWriteHoldCount();
    }

    /**
     * Queries the number of reentrant read holds on this lock by the
     * current thread.  A reader thread has a hold on a lock for
     * each lock action that is not matched by an unlock action.
     *
     * @return the number of holds on the read lock by the current thread,
     *         or zero if the read lock is not held by the current thread
     * @since 1.6
     */
    public int getReadHoldCount() {
        return sync.getReadHoldCount();
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire the write lock.  Because the actual set of threads may
     * change dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive lock monitoring facilities.
     *
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedWriterThreads() {
        return sync.getExclusiveQueuedThreads();
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire the read lock.  Because the actual set of threads may
     * change dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive lock monitoring facilities.
     *
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedReaderThreads() {
        return sync.getSharedQueuedThreads();
    }

    /**
     * Queries whether any threads are waiting to acquire the read or
     * write lock. Note that because cancellations may occur at any
     * time, a {@code true} return does not guarantee that any other
     * thread will ever acquire a lock.  This method is designed
     * primarily for use in monitoring of the system state.
     *
     * @return {@code true} if there may be other threads waiting to
     *         acquire the lock
     */
    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    /**
     * Queries whether the given thread is waiting to acquire either
     * the read or write lock. Note that because cancellations may
     * occur at any time, a {@code true} return does not guarantee
     * that this thread will ever acquire a lock.  This method is
     * designed primarily for use in monitoring of the system state.
     *
     * @param thread the thread
     * @return {@code true} if the given thread is queued waiting for this lock
     * @throws NullPointerException if the thread is null
     */
    public final boolean hasQueuedThread(Thread thread) {
        return sync.isQueued(thread);
    }

    /**
     * Returns an estimate of the number of threads waiting to acquire
     * either the read or write lock.  The value is only an estimate
     * because the number of threads may change dynamically while this
     * method traverses internal data structures.  This method is
     * designed for use in monitoring of the system state, not for
     * synchronization control.
     *
     * @return the estimated number of threads waiting for this lock
     */
    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire either the read or write lock.  Because the actual set
     * of threads may change dynamically while constructing this
     * result, the returned collection is only a best-effort estimate.
     * The elements of the returned collection are in no particular
     * order.  This method is designed to facilitate construction of
     * subclasses that provide more extensive monitoring facilities.
     *
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }

    /**
     * Queries whether any threads are waiting on the given condition
     * associated with the write lock. Note that because timeouts and
     * interrupts may occur at any time, a {@code true} return does
     * not guarantee that a future {@code signal} will awaken any
     * threads.  This method is designed primarily for use in
     * monitoring of the system state.
     *
     * @param condition the condition
     * @return {@code true} if there are any waiting threads
     * @throws IllegalMonitorStateException if this lock is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this lock
     * @throws NullPointerException if the condition is null
     */
    public boolean hasWaiters(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer1.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.hasWaiters((AbstractQueuedSynchronizer1.ConditionObject)condition);
    }

    /**
     * Returns an estimate of the number of threads waiting on the
     * given condition associated with the write lock. Note that because
     * timeouts and interrupts may occur at any time, the estimate
     * serves only as an upper bound on the actual number of waiters.
     * This method is designed for use in monitoring of the system
     * state, not for synchronization control.
     *
     * @param condition the condition
     * @return the estimated number of waiting threads
     * @throws IllegalMonitorStateException if this lock is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this lock
     * @throws NullPointerException if the condition is null
     */
    public int getWaitQueueLength(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer1.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitQueueLength((AbstractQueuedSynchronizer1.ConditionObject)condition);
    }

    /**
     * Returns a collection containing those threads that may be
     * waiting on the given condition associated with the write lock.
     * Because the actual set of threads may change dynamically while
     * constructing this result, the returned collection is only a
     * best-effort estimate. The elements of the returned collection
     * are in no particular order.  This method is designed to
     * facilitate construction of subclasses that provide more
     * extensive condition monitoring facilities.
     *
     * @param condition the condition
     * @return the collection of threads
     * @throws IllegalMonitorStateException if this lock is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this lock
     * @throws NullPointerException if the condition is null
     */
    protected Collection<Thread> getWaitingThreads(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer1.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitingThreads((AbstractQueuedSynchronizer1.ConditionObject)condition);
    }

    /**
     * Returns a string identifying this lock, as well as its lock state.
     * The state, in brackets, includes the String {@code "Write locks ="}
     * followed by the number of reentrantly held write locks, and the
     * String {@code "Read locks ="} followed by the number of held
     * read locks.
     *
     * @return a string identifying this lock, as well as its lock state
     */
    public String toString() {
        int c = sync.getCount();
        int w = Sync.exclusiveCount(c);
        int r = Sync.sharedCount(c);

        return super.toString() +
            "[Write locks = " + w + ", Read locks = " + r + "]";
    }

    /**
     * Returns the thread id for the given thread.  We must access
     * this directly rather than via method Thread.getId() because
     * getId() is not final, and has been known to be overridden in
     * ways that do not preserve unique mappings.
     */
    static final long getThreadId(Thread thread) {
        return UNSAFE.getLongVolatile(thread, TID_OFFSET);
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long TID_OFFSET;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> tk = Thread.class;
            TID_OFFSET = UNSAFE.objectFieldOffset
                (tk.getDeclaredField("tid"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
