package com.concuurent.learn.juc;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;

/**
 *LinkedBlockingQueue也是使用单向链表实现的，其也有两个Node，分别用来存放首、尾节点，<p>
 *并且还有一个初始值为0的原子变量count，用来记录队列元素个数。外还有两个ReentrantLock实例，<p>
 *分别用来控制元素入队和出队的原子性，其中takeLock用来控制同时只有一个线程可以从队列头获取元素，<p>
 *其他线程必须等待，putLock制同时只能有一个线程可以获取锁，在队列尾部添加元素，其他线程必须等待。<p>
 *另外，notEmpty和notFull是条件变量，它们内部都有一个条件队列用来存放进队和出队时被阻塞的线程，<p>
 *其实这是生产者一消费者模型。如下是独占锁的创建代码。
 *
 */
public class LinkedBlockingQueue<E> extends AbstractQueue<E>
        implements BlockingQueue<E>, java.io.Serializable {
    private static final long serialVersionUID = -6903933977591709194L;


    /**
     * Linked list node class
     */
    static class Node<E> {
        E item;

        /**
         * One of:
         * - the real successor Node
         * - this Node, meaning the successor is head.next
         * - null, meaning there is no successor (this is the last node)
         */
        Node<E> next;

        Node(E x) { item = x; }
    }

    /** The capacity bound, or Integer.MAX_VALUE if none */
    private final int capacity;

    /** 当前队列元素个数 */
    private final AtomicInteger count = new AtomicInteger();

    /**
     * Head of linked list.
     * Invariant: head.item == null
     */
    transient Node<E> head;

    /**
     * Tail of linked list.
     * Invariant: last.next == null
     */
    private transient Node<E> last;

    /** 执行take poll等操作时需要获取该锁 */
    private final ReentrantLock takeLock = new ReentrantLock();

    /** 当队列为空时，执行出队操作(比如take)的线程会被放入这个条件队列进行等待 */
    private final Condition notEmpty = takeLock.newCondition();

    /** 执行 put, offer等操作时需要获取该锁 */
    private final ReentrantLock putLock = new ReentrantLock();

    /** 当队列满时，执行进队操作(比如put)的线程会被放入这个条件队列进行等待 */
    private final Condition notFull = putLock.newCondition();

    /**
     * Signals a waiting take. Called only from put/offer (which do not
     * otherwise ordinarily lock takeLock.)
     */
    private void signalNotEmpty() {
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();
        try {
            notEmpty.signal();
        } finally {
            takeLock.unlock();
        }
//        1.方法的作用就是激活notEmpty的条件队列中因为调用notEmpty的await方法（比如调用take方法并且队列为空的时候）
//        而被阻塞的一个线程，这也说明了调用条件变量方法前要获取对应的锁。

//        2.综上可知，offer方法通过使用putLock锁保证了在队尾新增元素操作的原子性。
//        另外，调用条件变量的方法前一定要记得获取对应的锁，并且注意进队时只操作队列链表的尾节点。

    }

    /**
     * Signals a waiting put. Called only from take/poll.
     */
    private void signalNotFull() {
        final ReentrantLock putLock = this.putLock;
        putLock.lock();
        try {
            notFull.signal();
        } finally {
            putLock.unlock();
        }
    }

    /**
     * Links node at end of queue.
     *
     * @param node the node
     */
    private void enqueue(Node<E> node) {
        // assert putLock.isHeldByCurrentThread();
        // assert last.next == null;
        last = last.next = node;
    }

    /**
     * Removes a node from head of queue.
     *
     * @return the node
     */
    private E dequeue() {
        // assert takeLock.isHeldByCurrentThread();
        // assert head.item == null;
        Node<E> h = head;
        Node<E> first = h.next;
        h.next = h; // help GC
        head = first;
        E x = first.item;
        first.item = null;
        return x;
    }

    /**
     * Locks to prevent both puts and takes.
     */
    void fullyLock() {
        putLock.lock();
        takeLock.lock();
    }

    /**
     * Unlocks to allow both puts and takes.
     */
    void fullyUnlock() {
        takeLock.unlock();
        putLock.unlock();
    }

//     /**
//      * Tells whether both locks are held by current thread.
//      */
//     boolean isFullyLocked() {
//         return (putLock.isHeldByCurrentThread() &&
//                 takeLock.isHeldByCurrentThread());
//     }

    /**
     *当调用线程在LinkedBlockingQueue实例上执行take、poll等操作时要获取到takeLock锁，
     *从而保证同时只有一个线程可以操作链表头节点。
     *另外由于条件变量notEmpty内部的条件队列的维护使用的是takeLock的锁状态管理机制，
     *所以在调用notEmpty的await和signal方法前调用线程必须先获取到takeLock锁，
     *否则会抛出IllegalMonitorStateException异常。notEmpty内部则维护着一个条件队列，
     *当线程获取到takeLock后调用notEmpty的await方法时，调用线程会被阻塞，
     *然后该线程会被放到notEmpty内部的条件队列进行等待，直到有线程调用了notEmpty的signal方法。<p>
     *
     *·在LinkedBlockingQueue实例上执行put、offer操作时需要获取到putLock锁，
     *从而保证同时只有一个线程可以操作链表尾节点。
     *同样由于条件变量notFull内部的条件队列的维护使用的是putLock的锁状态管理机制，
     *所以在调用notFull的await和signal方法前调用线程必须先获取到putLock锁，
     *否则抛出IllegalMonitorStateException异常。notFull内部则护着一个条件队列，
     *当线程获取到putLock锁后调用notFull的await方法时，
     *调用线程会被阻塞，然后该线程会被放到notFull内部的条件队列进行等待，
     *直到有线程调用了notFull的signal方法。
     *如下是LinkedBlockingQueue的无参构造函数的代码。
     */
    public LinkedBlockingQueue() {
//    	该代码可知，默认队列容量为Ox7fffffff，
//    	用户也可以自己指定容量所以从一定程度上可以说LinkedBlockingQueue是有界阻塞队列。
        this(Integer.MAX_VALUE);
    }

    /**
     * Creates a {@code LinkedBlockingQueue} with the given (fixed) capacity.
     *
     * @param capacity the capacity of this queue
     * @throws IllegalArgumentException if {@code capacity} is not greater
     *         than zero
     */
    public LinkedBlockingQueue(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException();
        this.capacity = capacity;
        //初始化首尾节点，让他们指向哨兵节点
        last = head = new Node<E>(null);
    }

    /**
     * Creates a {@code LinkedBlockingQueue} with a capacity of
     * {@link Integer#MAX_VALUE}, initially containing the elements of the
     * given collection,
     * added in traversal order of the collection's iterator.
     *
     * @param c the collection of elements to initially contain
     * @throws NullPointerException if the specified collection or any
     *         of its elements are null
     */
    public LinkedBlockingQueue(Collection<? extends E> c) {
        this(Integer.MAX_VALUE);
        final ReentrantLock putLock = this.putLock;
        putLock.lock(); // Never contended, but necessary for visibility
        try {
            int n = 0;
            for (E e : c) {
                if (e == null)
                    throw new NullPointerException();
                if (n == capacity)
                    throw new IllegalStateException("Queue full");
                enqueue(new Node<E>(e));
                ++n;
            }
            count.set(n);
        } finally {
            putLock.unlock();
        }
    }

    // this doc comment is overridden to remove the reference to collections
    // greater in size than Integer.MAX_VALUE
    /**
     * Returns the number of elements in this queue.
     * 由于进行出队、入队操作时的count是加了锁的，所以结果相比ConcurrentLinkedQueue的size方法比较准确。
     * 这里考虑为何在ConcurrentLinkedQueue中需要遍历链表来获取size而不使用一个原子变量呢？
     * 这是因为使用原子变量保存队列元素个数需要保证入队、出队操作和原子变量作是原子性操作，
     * 而ConcurrentLinkedQueue使用的是CAS无锁算法所以无法做到这样。
     * @return the number of elements in this queue
     */
    public int size() {
        return count.get();
    }

    // this doc comment is a modified copy of the inherited doc comment,
    // without the reference to unlimited queues.
    /**
     * Returns the number of additional elements that this queue can ideally
     * (in the absence of memory or resource constraints) accept without
     * blocking. This is always equal to the initial capacity of this queue
     * less the current {@code size} of this queue.
     *
     * <p>Note that you <em>cannot</em> always tell if an attempt to insert
     * an element will succeed by inspecting {@code remainingCapacity}
     * because it may be the case that another thread is about to
     * insert or remove an element.
     */
    public int remainingCapacity() {
        return capacity - count.get();
    }

    /**
     * 向队列尾部插入一个元素，如果队列中有空闲则插入后直接返回，如果队列己满则阻塞当前线程，
     * 直到队列有空闲插入成功后返回。如果在阻塞时被其他线程设置了中断标志，
     * 则被阻塞线程会抛出InterruptedException异常而返回。
     * 另外，如果e元素为null则出NullPointerException异常。
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public void put(E e) throws InterruptedException {
    	//1.如果为空则抛出空指针异常
        if (e == null) throw new NullPointerException();
        // Note: convention in all put/take/etc is to preset local var
        // holding count negative to indicate failure unless set.
        //2.构建新节点，并获取独占锁putLock
        int c = -1;
        Node<E> node = new Node<E>(e);
        final ReentrantLock putLock = this.putLock;
        final AtomicInteger count = this.count;
        putLock.lockInterruptibly();
        try {
            /*
             * Note that count is used in wait guard even though it is
             * not protected by lock. This works because count can
             * only decrease at this point (all other puts are shut
             * out by lock), and we (or some other waiting put) are
             * signalled if it ever changes from capacity. Similarly
             * for all other uses of count in other wait guards.
             */
        	//3.如果队列满则等待
            while (count.get() == capacity) {
                notFull.await();
            }
            //4.进队列并递增计数
            enqueue(node);
            c = count.getAndIncrement();
            //5.
            if (c + 1 < capacity)
                notFull.signal();
        } finally {
        	//6.
            putLock.unlock();
        }
        //7.
        if (c == 0)
            signalNotEmpty();
//        A.	在代码（2）中使用putLock.locklntenuptibly（）获取独占锁，
//              相比在offer方法中获取独占锁的方法这个方法可以被中断。
//              具体地说就是当前线程在获取锁的过程中，如果被其他线程设置了中断标志则当前线程会抛出IntenuptedException异常，
//              所以put操作在获取锁的过程中是可被中断的。
//        B.	代码(3）判断如果当前队列己满，则调用notFull的await（）方法把当前线程放入notFull的条件队列，
//              当前线程被阻塞挂起后会释放获取到的putLock锁。由于putLock锁被释放了，
//              所现在其他线程就有机会获取到putLock锁了。
//        C.	另外代码（3）在判断队列是否为空时为何使用while循而不是if语句？
//              这是考虑到当前线程被虚假唤醒的问题，也就是其他线程没有调用notFull的singal方法时
//             notFull.await（）在某种情况下会自动返回。如果使用if句那么虚假唤醒后会执行代码（4）的元素入队操作，
//             并且递增计数器，而这时候队列己经满了，从而导致队列元素个数大于队列被设置的容量，进而导致程序出错。
//	                      而使用while循环时，假如notFull.await（）被虚假唤醒了，那么再次循环检查当前队列是否己满，
//	                      如果是则再次进行等待。

    }

    /**
     * Inserts the specified element at the tail of this queue, waiting if
     * necessary up to the specified wait time for space to become available.
     *
     * @return {@code true} if successful, or {@code false} if
     *         the specified waiting time elapses before space is available
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean offer(E e, long timeout, TimeUnit unit)
        throws InterruptedException {

        if (e == null) throw new NullPointerException();
        long nanos = unit.toNanos(timeout);
        int c = -1;
        final ReentrantLock putLock = this.putLock;
        final AtomicInteger count = this.count;
        putLock.lockInterruptibly();
        try {
            while (count.get() == capacity) {
                if (nanos <= 0)
                    return false;
                nanos = notFull.awaitNanos(nanos);
            }
            enqueue(new Node<E>(e));
            c = count.getAndIncrement();
            if (c + 1 < capacity)
                notFull.signal();
        } finally {
            putLock.unlock();
        }
        if (c == 0)
            signalNotEmpty();
        return true;
    }

    /**
     *向队列尾部插入一个元素，如果队列中有空闲则插入成功后返回true，<br>
     *如果队列己满则丢弃当前元素然后返回false。<br>
     *如果e元素为null则抛出NullPointerException异常。<br>
     *另外，该方法是非阻塞的。<br>
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e) {
    	//1.为空元素则抛出空指针异常
        if (e == null) throw new NullPointerException();
        //2.如果当前队列满则丢弃将要放入的元素，然后返回fasle
        final AtomicInteger count = this.count;
        if (count.get() == capacity)
            return false;
        //3.构造新节点，获取putLock独占锁
        int c = -1;
        Node<E> node = new Node<E>(e);
        final ReentrantLock putLock = this.putLock;
        putLock.lock();
        try {
        	//4.如果队列不满则进队列，并递增元素计数
            if (count.get() < capacity) {
                enqueue(node);
                c = count.getAndIncrement();
                //5.
                if (c + 1 < capacity)
                    notFull.signal();
            }
        } finally {
        	//6.释放锁
            putLock.unlock();
        }
        //7.
        if (c == 0)
            signalNotEmpty();
        //8.
        return c >= 0;
        
//        i.	代码（2）判断如果当前队列己满则丢弃当前元素并返回false。
//        ii.	代码（3）获取到putLock锁，当前线程获取到该锁后，
//              则其他调用put和offer操作的线程将会被阻塞（阻塞的线程被放到putLock锁的AQS阻塞队列）。
//        iii.	代码（4）这里重新判断当前队列是否满，
//              这是因为在执行代码（2）和获取到putLock锁期间可能其线程通过put或者offer操作向队列里面添加了新元素。
//              重新判断队列确实不满则新元素入队，并递增计数器。
//        iv.	代码(5)判断如果新元素入队后队列还有空闲空间，
//              则唤醒notFull的条件队列里面因为调用了notFullawait操作（比如执行put方法而队列满了的时候）
//              而被阻塞的一个线程，因为队列现在有空闲所以这里可以提前唤醒一个入队线程。
//        v.	代码（6）则释放获取的putLock锁，这里要注意，锁的释放一定要在finally里面做，
//              因为即使try块抛出异常了，
//              finally也是会被执行到。
//              另外释放锁后其他因为调用put操作而被阻塞的线程将会有一个获取到该锁。
//        vi.	代码(7）中的c==O说明在执行代码（6）释放锁时队列里面至少有一个元素，
//              队列里面有元素则执行signalNotEmpty操作，signalNotEmpty的代码如下。

    }

    /**
     * 获取当前队列头部元素并从队列里面移除它。<br>
     * 如果队列为空则阻塞当前线程直到队列不为空然后返回元素，<br>
     * 如果在阻塞时被其他线程设置了中断标志，<br>
     * 则被阻塞线程会抛InterruptedException异常而返回。
     * A.	代码（1）中，当前线程获取到独占锁，其他调用take或者poll操作的线程将会被阻塞挂起。
     * B.   代码（2）判断如果队列为空则阻塞挂起当前线程，并把当前线程放入notEmpty的条件队列。
     * C.   代码（3）进行出队操作并递减计数。
     * D.	代码（4）判断如果c>l则说明当前队列不为空，那么唤醒notEmpty的条件队列里面的一个因为调用take操而被阻的线程。
     * E.	代码（5）释放锁
     * F.	代码（6）判断如果c==capacity则说明当前队列至少有一个空闲位置，
     *      那么激活条件变量notFull的条件队列里面的一个因为调用put操作而被阻塞的线程。
     */
    public E take() throws InterruptedException {
        E x;
        int c = -1;
        final AtomicInteger count = this.count;
        //1.获取锁
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lockInterruptibly();
        try {
        	//2.当前队列为空则阻塞挂起
            while (count.get() == 0) {
                notEmpty.await();
            }
            //3.出队并递减计数
            x = dequeue();
            c = count.getAndDecrement();
            //4
            if (c > 1)
                notEmpty.signal();
        } finally {
        	//5
            takeLock.unlock();
        }
        //6
        if (c == capacity)
            signalNotFull();
        //7
        return x;
    }

    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E x = null;
        int c = -1;
        long nanos = unit.toNanos(timeout);
        final AtomicInteger count = this.count;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lockInterruptibly();
        try {
            while (count.get() == 0) {
                if (nanos <= 0)
                    return null;
                nanos = notEmpty.awaitNanos(nanos);
            }
            x = dequeue();
            c = count.getAndDecrement();
            if (c > 1)
                notEmpty.signal();
        } finally {
            takeLock.unlock();
        }
        if (c == capacity)
            signalNotFull();
        return x;
    }
    
    /**
     * 从队列头部获取并移除一个元素，如果队列为空则返回null，该方法是不阻塞。
     */
    public E poll() {
    	//1.队列为空则返回null
        final AtomicInteger count = this.count;
        if (count.get() == 0)
            return null;
        //2.获取独占锁
        E x = null;
        int c = -1;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();
        try {
        	//3.队列不为空则出队并递减计数
            if (count.get() > 0) {//3.1
                x = dequeue();//3.2
                c = count.getAndDecrement();//3.3
                //4
                if (c > 1)
                    notEmpty.signal();
            }
        } finally {
        	//5.
            takeLock.unlock();
        }
        //6.
        if (c == capacity)
            signalNotFull();
        //7返回
        return x;
        /*代码(1）判断如果当前队列为空，则直接返回null。
         * 
		*代码（2）获取独占锁takeLock，当前线程获取该锁后，其他线程在调用poll或者take方法时会被阻塞挂起。
		*
		*代码（3）判断如果当前队列不为空则进行出队操作，然后递减计数器。这里需要思考，
		*如何保证执行代码3.1时队列不空，而执行代码3.2时也一定不会空呢？毕竟这不是原子性操作，
		*会不会出现代码3.1判断队列不为空，但是执行代码3.2时队列为空了呢？
		*那么我们看在执行到代码3.2前在哪些地方会修改count的计数。
		*由于当前线程己经拿到了takeLock锁，所以其他调用poll或者take方法的线程不可能会走到修改count计数的地方。
		*其实这时候如果能走到修改count计数的地方是因为其他线程调用了put和offer操作，
		*由于这两个操作不需要获取takeLock锁而获取的是putLock锁，
		*但是在put和offer操作内部是增加count计数值的，
		*所以不会出现上面所说的情况。其实只需要看在哪些地方递减了count计数值即可，
		*只有递减了count计数值才会出现上面说的，执行代码3.1时队列不空，而执行代码3.2时队列为空的情况。
		*我们查看代码，只有在poll、take或者remove操作的地方会递减count计数值，
		*但是这三个方法都需要获取到takeLock锁才能进行操作，而当前线程己经获取了takeLock锁，
		*所以其他线程没有机会在当前’情况下递减count数值，所以看起来代码3.1、3.2不是原子性的，但是它们是线程安全的。<p>
		*
		*代码（4）判断如果c>1则说明当前线程移除掉队列里面的一个元素后队列不为空(c是删元素前队列元素个数)，
		*那么这时候就可以激活因为调用take方法而被阻塞到notEmpty的条件队列里的一个线程。
		*
		*代码（6）说明当前线程移除队头元素前当前队列是满的，移除队头元素后当前队列至少有一个空闲位置，
		*那么这时候就可以调用signalNotFull激活因为调用put方法而被阻塞到notFull的条件队列里的一个线程，
		*signalNotFull的代码如下。
		* poll代码逻辑比较简单，值得注意的是，获取元素时只操作了队列的头节点。
        */
    }
    /**
     * 获取队列头部元素但是不从队列里面移除它，如果队列为空则返回null。该方法是不阻塞的。
     * peek操作的代码也比较简单，这里需要注意的是，<p>
     * 代码(3）这还是需要判断first是否为null，<p>
     * 不能直接执行代码(4)。正常情况下执行到代码(2)说明队列不为空，但是代码（1）和（2）不是原子操作，<p>
     * 也就是在执行点（1）判断队列不空后，在代码（2)获取到锁前有可能其他线执行了poll或者take操作导致队列变为空。<p>
     * 然后当前线程获取锁后，直接执行代码(4)(first.item）会抛出空针异常<p>
     */
    public E peek() {
    	//1
        if (count.get() == 0)
            return null;
        //2
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();
        try {
            Node<E> first = head.next;
            //3
            if (first == null)
                return null;
            else
            	//4.
                return first.item;
        } finally {
        	//5.
            takeLock.unlock();
        }
    }

    /**
     * Unlinks interior Node p with predecessor trail.
     */
    void unlink(Node<E> p, Node<E> trail) {
        // assert isFullyLocked();
        // p.next is not changed, to allow iterators that are
        // traversing p to maintain their weak-consistency guarantee.
        p.item = null;
        trail.next = p.next;
        if (last == p)
            last = trail;
        //如果当前队列满，则删除后，也不忘记唤醒等待的线程。
        if (count.getAndDecrement() == capacity)
            notFull.signal();
    }

    /**
     * Removes a single instance of the specified element from this queue,
     * if it is present.  More formally, removes an element {@code e} such
     * that {@code o.equals(e)}, if this queue contains one or more such
     * elements.
     * Returns {@code true} if this queue contained the specified element
     * (or equivalently, if this queue changed as a result of the call).
     * 删除队列里面指定的元素，有则删并返回true，没有则返回false;
     * @param o element to be removed from this queue, if present
     * @return {@code true} if this queue changed as a result of the call
     * 由于remove方法在删除指定元素前加了两把锁，所以在遍历队列查找指定元素的过程中是线程安全的，
     * 并且此时其他调用入队、出队操作的线程全部会被阻塞。
     * 另外，获取多个资锁的顺序与释放的顺序是相反的。
     */
    public boolean remove(Object o) {
    	
        if (o == null) return false;
        //1.双重加锁 通过fullyLock获取双重锁，获取后，其他线程进行入队或者出队操作时就会被阻塞挂起
        fullyLock();
        try {
        	//2.遍历队列找到则删除，并返回true
            for (Node<E> trail = head, p = trail.next;
                 p != null;
                 trail = p, p = p.next) {
                if (o.equals(p.item)) {
                	//遍历队列寻找要删除的元素，找不到则直接返回false，找到则执行unlink操作。
                	//删除元素后，如果发现当前队列有空闲空间，则唤醒notFull的条件队列中的一个因为用put方法而被阻塞的线程。
                    unlink(p, trail);
                    return true;
                }
            }
            //3.找不到则返回false
            return false;
        } finally {
        	//4.解锁 调用fullyUnlock方法使用与加锁顺序相反的顺序释放双重锁。
            fullyUnlock();
        }
    }

    /**
     * Returns {@code true} if this queue contains the specified element.
     * More formally, returns {@code true} if and only if this queue contains
     * at least one element {@code e} such that {@code o.equals(e)}.
     *
     * @param o object to be checked for containment in this queue
     * @return {@code true} if this queue contains the specified element
     */
    public boolean contains(Object o) {
        if (o == null) return false;
        fullyLock();
        try {
            for (Node<E> p = head.next; p != null; p = p.next)
                if (o.equals(p.item))
                    return true;
            return false;
        } finally {
            fullyUnlock();
        }
    }

    /**
     * Returns an array containing all of the elements in this queue, in
     * proper sequence.
     *
     * <p>The returned array will be "safe" in that no references to it are
     * maintained by this queue.  (In other words, this method must allocate
     * a new array).  The caller is thus free to modify the returned array.
     *
     * <p>This method acts as bridge between array-based and collection-based
     * APIs.
     *
     * @return an array containing all of the elements in this queue
     */
    public Object[] toArray() {
        fullyLock();
        try {
            int size = count.get();
            Object[] a = new Object[size];
            int k = 0;
            for (Node<E> p = head.next; p != null; p = p.next)
                a[k++] = p.item;
            return a;
        } finally {
            fullyUnlock();
        }
    }

    /**
     * Returns an array containing all of the elements in this queue, in
     * proper sequence; the runtime type of the returned array is that of
     * the specified array.  If the queue fits in the specified array, it
     * is returned therein.  Otherwise, a new array is allocated with the
     * runtime type of the specified array and the size of this queue.
     *
     * <p>If this queue fits in the specified array with room to spare
     * (i.e., the array has more elements than this queue), the element in
     * the array immediately following the end of the queue is set to
     * {@code null}.
     *
     * <p>Like the {@link #toArray()} method, this method acts as bridge between
     * array-based and collection-based APIs.  Further, this method allows
     * precise control over the runtime type of the output array, and may,
     * under certain circumstances, be used to save allocation costs.
     *
     * <p>Suppose {@code x} is a queue known to contain only strings.
     * The following code can be used to dump the queue into a newly
     * allocated array of {@code String}:
     *
     *  <pre> {@code String[] y = x.toArray(new String[0]);}</pre>
     *
     * Note that {@code toArray(new Object[0])} is identical in function to
     * {@code toArray()}.
     *
     * @param a the array into which the elements of the queue are to
     *          be stored, if it is big enough; otherwise, a new array of the
     *          same runtime type is allocated for this purpose
     * @return an array containing all of the elements in this queue
     * @throws ArrayStoreException if the runtime type of the specified array
     *         is not a supertype of the runtime type of every element in
     *         this queue
     * @throws NullPointerException if the specified array is null
     */
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        fullyLock();
        try {
            int size = count.get();
            if (a.length < size)
                a = (T[])java.lang.reflect.Array.newInstance
                    (a.getClass().getComponentType(), size);

            int k = 0;
            for (Node<E> p = head.next; p != null; p = p.next)
                a[k++] = (T)p.item;
            if (a.length > k)
                a[k] = null;
            return a;
        } finally {
            fullyUnlock();
        }
    }

    public String toString() {
        fullyLock();
        try {
            Node<E> p = head.next;
            if (p == null)
                return "[]";

            StringBuilder sb = new StringBuilder();
            sb.append('[');
            for (;;) {
                E e = p.item;
                sb.append(e == this ? "(this Collection)" : e);
                p = p.next;
                if (p == null)
                    return sb.append(']').toString();
                sb.append(',').append(' ');
            }
        } finally {
            fullyUnlock();
        }
    }

    /**
     * Atomically removes all of the elements from this queue.
     * The queue will be empty after this call returns.
     */
    public void clear() {
        fullyLock();
        try {
            for (Node<E> p, h = head; (p = h.next) != null; h = p) {
                h.next = h;
                p.item = null;
            }
            head = last;
            // assert head.item == null && head.next == null;
            if (count.getAndSet(0) == capacity)
                notFull.signal();
        } finally {
            fullyUnlock();
        }
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c) {
        return drainTo(c, Integer.MAX_VALUE);
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c, int maxElements) {
        if (c == null)
            throw new NullPointerException();
        if (c == this)
            throw new IllegalArgumentException();
        if (maxElements <= 0)
            return 0;
        boolean signalNotFull = false;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();
        try {
            int n = Math.min(maxElements, count.get());
            // count.get provides visibility to first n Nodes
            Node<E> h = head;
            int i = 0;
            try {
                while (i < n) {
                    Node<E> p = h.next;
                    c.add(p.item);
                    p.item = null;
                    h.next = h;
                    h = p;
                    ++i;
                }
                return n;
            } finally {
                // Restore invariants even if c.add() threw
                if (i > 0) {
                    // assert h.item == null;
                    head = h;
                    signalNotFull = (count.getAndAdd(-i) == capacity);
                }
            }
        } finally {
            takeLock.unlock();
            if (signalNotFull)
                signalNotFull();
        }
    }

    /**
     * Returns an iterator over the elements in this queue in proper sequence.
     * The elements will be returned in order from first (head) to last (tail).
     *
     * <p>The returned iterator is
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * @return an iterator over the elements in this queue in proper sequence
     */
    public Iterator<E> iterator() {
        return new Itr();
    }

    private class Itr implements Iterator<E> {
        /*
         * Basic weakly-consistent iterator.  At all times hold the next
         * item to hand out so that if hasNext() reports true, we will
         * still have it to return even if lost race with a take etc.
         */

        private Node<E> current;
        private Node<E> lastRet;
        private E currentElement;

        Itr() {
            fullyLock();
            try {
                current = head.next;
                if (current != null)
                    currentElement = current.item;
            } finally {
                fullyUnlock();
            }
        }

        public boolean hasNext() {
            return current != null;
        }

        /**
         * Returns the next live successor of p, or null if no such.
         *
         * Unlike other traversal methods, iterators need to handle both:
         * - dequeued nodes (p.next == p)
         * - (possibly multiple) interior removed nodes (p.item == null)
         */
        private Node<E> nextNode(Node<E> p) {
            for (;;) {
                Node<E> s = p.next;
                if (s == p)
                    return head.next;
                if (s == null || s.item != null)
                    return s;
                p = s;
            }
        }

        public E next() {
            fullyLock();
            try {
                if (current == null)
                    throw new NoSuchElementException();
                E x = currentElement;
                lastRet = current;
                current = nextNode(current);
                currentElement = (current == null) ? null : current.item;
                return x;
            } finally {
                fullyUnlock();
            }
        }

        public void remove() {
            if (lastRet == null)
                throw new IllegalStateException();
            fullyLock();
            try {
                Node<E> node = lastRet;
                lastRet = null;
                for (Node<E> trail = head, p = trail.next;
                     p != null;
                     trail = p, p = p.next) {
                    if (p == node) {
                        unlink(p, trail);
                        break;
                    }
                }
            } finally {
                fullyUnlock();
            }
        }
    }

    /** A customized variant of Spliterators.IteratorSpliterator */
    static final class LBQSpliterator<E> implements Spliterator<E> {
        static final int MAX_BATCH = 1 << 25;  // max batch array size;
        final LinkedBlockingQueue<E> queue;
        Node<E> current;    // current node; null until initialized
        int batch;          // batch size for splits
        boolean exhausted;  // true when no more nodes
        long est;           // size estimate
        LBQSpliterator(LinkedBlockingQueue<E> queue) {
            this.queue = queue;
            this.est = queue.size();
        }

        public long estimateSize() { return est; }

        public Spliterator<E> trySplit() {
            Node<E> h;
            final LinkedBlockingQueue<E> q = this.queue;
            int b = batch;
            int n = (b <= 0) ? 1 : (b >= MAX_BATCH) ? MAX_BATCH : b + 1;
            if (!exhausted &&
                ((h = current) != null || (h = q.head.next) != null) &&
                h.next != null) {
                Object[] a = new Object[n];
                int i = 0;
                Node<E> p = current;
                q.fullyLock();
                try {
                    if (p != null || (p = q.head.next) != null) {
                        do {
                            if ((a[i] = p.item) != null)
                                ++i;
                        } while ((p = p.next) != null && i < n);
                    }
                } finally {
                    q.fullyUnlock();
                }
                if ((current = p) == null) {
                    est = 0L;
                    exhausted = true;
                }
                else if ((est -= i) < 0L)
                    est = 0L;
                if (i > 0) {
                    batch = i;
                    return Spliterators.spliterator
                        (a, 0, i, Spliterator.ORDERED | Spliterator.NONNULL |
                         Spliterator.CONCURRENT);
                }
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super E> action) {
            if (action == null) throw new NullPointerException();
            final LinkedBlockingQueue<E> q = this.queue;
            if (!exhausted) {
                exhausted = true;
                Node<E> p = current;
                do {
                    E e = null;
                    q.fullyLock();
                    try {
                        if (p == null)
                            p = q.head.next;
                        while (p != null) {
                            e = p.item;
                            p = p.next;
                            if (e != null)
                                break;
                        }
                    } finally {
                        q.fullyUnlock();
                    }
                    if (e != null)
                        action.accept(e);
                } while (p != null);
            }
        }

        public boolean tryAdvance(Consumer<? super E> action) {
            if (action == null) throw new NullPointerException();
            final LinkedBlockingQueue<E> q = this.queue;
            if (!exhausted) {
                E e = null;
                q.fullyLock();
                try {
                    if (current == null)
                        current = q.head.next;
                    while (current != null) {
                        e = current.item;
                        current = current.next;
                        if (e != null)
                            break;
                    }
                } finally {
                    q.fullyUnlock();
                }
                if (current == null)
                    exhausted = true;
                if (e != null) {
                    action.accept(e);
                    return true;
                }
            }
            return false;
        }

        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.NONNULL |
                Spliterator.CONCURRENT;
        }
    }

    /**
     * Returns a {@link Spliterator} over the elements in this queue.
     *
     * <p>The returned spliterator is
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * <p>The {@code Spliterator} reports {@link Spliterator#CONCURRENT},
     * {@link Spliterator#ORDERED}, and {@link Spliterator#NONNULL}.
     *
     * @implNote
     * The {@code Spliterator} implements {@code trySplit} to permit limited
     * parallelism.
     *
     * @return a {@code Spliterator} over the elements in this queue
     * @since 1.8
     */
    public Spliterator<E> spliterator() {
        return new LBQSpliterator<E>(this);
    }

    /**
     * Saves this queue to a stream (that is, serializes it).
     *
     * @param s the stream
     * @throws java.io.IOException if an I/O error occurs
     * @serialData The capacity is emitted (int), followed by all of
     * its elements (each an {@code Object}) in the proper order,
     * followed by a null
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {

        fullyLock();
        try {
            // Write out any hidden stuff, plus capacity
            s.defaultWriteObject();

            // Write out all elements in the proper order.
            for (Node<E> p = head.next; p != null; p = p.next)
                s.writeObject(p.item);

            // Use trailing null as sentinel
            s.writeObject(null);
        } finally {
            fullyUnlock();
        }
    }

    /**
     * Reconstitutes this queue from a stream (that is, deserializes it).
     * @param s the stream
     * @throws ClassNotFoundException if the class of a serialized object
     *         could not be found
     * @throws java.io.IOException if an I/O error occurs
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        // Read in capacity, and any hidden stuff
        s.defaultReadObject();

        count.set(0);
        last = head = new Node<E>(null);

        // Read in all elements and place in queue
        for (;;) {
            @SuppressWarnings("unchecked")
            E item = (E)s.readObject();
            if (item == null)
                break;
            add(item);
        }
    }
}
