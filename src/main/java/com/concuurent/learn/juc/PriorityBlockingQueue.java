package com.concuurent.learn.juc;


import java.util.AbstractQueue;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.SortedSet;
import java.util.Spliterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * PriorityBlockingQueue是带先级的无界阻塞队列，每次出队都返回优先级最高或者最低的元素。<br>
 * 其内部是使用平衡二叉树堆实现的，所以直接遍历队列元素不保证有序。<br>
 * 默认使用对象的compareTo方法提供比较规则，<br>
 * 如果你需要自定义比较规则则可以自定义comparators。<br>
 * @author Wesley
 *
 * @param <E>
 * 2019年7月3日下午2:51:43
 * @Version 1.0
 */
public class PriorityBlockingQueue<E> extends AbstractQueue<E>
    implements BlockingQueue<E>, java.io.Serializable {
    private static final long serialVersionUID = 5595510919245408276L;

    /*
     * PriorityBlockingQueue内部有一个数组queue，用来存放队列元素，size用来存放队列元素个数。
     * allocationSpinLock是个自旋锁，其使用CAS操作来保证同时只有一个线程可以扩容队列，状态为0或者1，
     * 其中0表示当前没有进行扩容，1表示当前正在扩容。
     * 由于这是一个优先级队列，所以有一个比较器comparator用来比较元素大小。
     * lock独占锁对象用来控制同时只能有一个线程可以进行入队、出队操作。
     * notEmpty条件变量用来实现take方法阻塞模式。这里没有notFull条件变量是因为这里的put操作是非阻塞的，
     * 为啥要设计为非阻塞的，是因为这是无界队列。
     * 在如下构造函数中，默认队列容量为11，默认比较器为null，
     * 也就是使用元素的compareTo方法进行比较来确定元素的优先级，
     * 这意味着队列元素必须实现了comparable接口。
     */

    /**
     * Default array capacity.
     */
    private static final int DEFAULT_INITIAL_CAPACITY = 11;

    /**
     * The maximum size of array to allocate.
     * Some VMs reserve some header words in an array.
     * Attempts to allocate larger arrays may result in
     * OutOfMemoryError: Requested array size exceeds VM limit
     */
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    /**
     * Priority queue represented as a balanced binary heap: the two
     * children of queue[n] are queue[2*n+1] and queue[2*(n+1)].  The
     * priority queue is ordered by comparator, or by the elements'
     * natural ordering, if comparator is null: For each node n in the
     * heap and each descendant d of n, n <= d.  The element with the
     * lowest value is in queue[0], assuming the queue is nonempty.
     */
    private transient Object[] queue;

    /**
     * The number of elements in the priority queue.
     */
    private transient int size;

    /**
     * The comparator, or null if priority queue uses elements'
     * natural ordering.
     */
    private transient Comparator<? super E> comparator;

    /**
     * Lock used for all public operations
     */
    private final ReentrantLock lock;

    /**
     * Condition for blocking when empty
     */
    private final Condition notEmpty;

    /**
     * Spinlock for allocation, acquired via CAS.
     */
    private transient volatile int allocationSpinLock;

    /**
     * A plain PriorityQueue used only for serialization,
     * to maintain compatibility with previous versions
     * of this class. Non-null only during serialization/deserialization.
     */
    private PriorityQueue<E> q;

    /**
     * Creates a {@code PriorityBlockingQueue} with the default
     * initial capacity (11) that orders its elements according to
     * their {@linkplain Comparable natural ordering}.
     */
    public PriorityBlockingQueue() {
        this(DEFAULT_INITIAL_CAPACITY, null);
    }

    /**
     * Creates a {@code PriorityBlockingQueue} with the specified
     * initial capacity that orders its elements according to their
     * {@linkplain Comparable natural ordering}.
     *
     * @param initialCapacity the initial capacity for this priority queue
     * @throws IllegalArgumentException if {@code initialCapacity} is less
     *         than 1
     */
    public PriorityBlockingQueue(int initialCapacity) {
        this(initialCapacity, null);
    }

    /**
     * Creates a {@code PriorityBlockingQueue} with the specified initial
     * capacity that orders its elements according to the specified
     * comparator.
     *
     * @param initialCapacity the initial capacity for this priority queue
     * @param  comparator the comparator that will be used to order this
     *         priority queue.  If {@code null}, the {@linkplain Comparable
     *         natural ordering} of the elements will be used.
     * @throws IllegalArgumentException if {@code initialCapacity} is less
     *         than 1
     */
    public PriorityBlockingQueue(int initialCapacity,
                                 Comparator<? super E> comparator) {
        if (initialCapacity < 1)
            throw new IllegalArgumentException();
        this.lock = new ReentrantLock();
        this.notEmpty = lock.newCondition();
        this.comparator = comparator;
        this.queue = new Object[initialCapacity];
    }

    /**
     * Creates a {@code PriorityBlockingQueue} containing the elements
     * in the specified collection.  If the specified collection is a
     * {@link SortedSet} or a {@link PriorityQueue}, this
     * priority queue will be ordered according to the same ordering.
     * Otherwise, this priority queue will be ordered according to the
     * {@linkplain Comparable natural ordering} of its elements.
     *
     * @param  c the collection whose elements are to be placed
     *         into this priority queue
     * @throws ClassCastException if elements of the specified collection
     *         cannot be compared to one another according to the priority
     *         queue's ordering
     * @throws NullPointerException if the specified collection or any
     *         of its elements are null
     */
    public PriorityBlockingQueue(Collection<? extends E> c) {
        this.lock = new ReentrantLock();
        this.notEmpty = lock.newCondition();
        boolean heapify = true; // true if not known to be in heap order
        boolean screen = true;  // true if must screen for nulls
        if (c instanceof SortedSet<?>) {
            SortedSet<? extends E> ss = (SortedSet<? extends E>) c;
            this.comparator = (Comparator<? super E>) ss.comparator();
            heapify = false;
        }
        else if (c instanceof PriorityBlockingQueue<?>) {
            PriorityBlockingQueue<? extends E> pq =
                (PriorityBlockingQueue<? extends E>) c;
            this.comparator = (Comparator<? super E>) pq.comparator();
            screen = false;
            if (pq.getClass() == PriorityBlockingQueue.class) // exact match
                heapify = false;
        }
        Object[] a = c.toArray();
        int n = a.length;
        // If c.toArray incorrectly doesn't return Object[], copy it.
        if (a.getClass() != Object[].class)
            a = Arrays.copyOf(a, n, Object[].class);
        if (screen && (n == 1 || this.comparator != null)) {
            for (int i = 0; i < n; ++i)
                if (a[i] == null)
                    throw new NullPointerException();
        }
        this.queue = a;
        this.size = n;
        if (heapify)
            heapify();
    }

    /**
     *tryGrow的作用是扩容。这里为啥在扩容前要先释放锁，然后使用CAS控制只有一个线程可以扩容成功？<br>
     *其实这里不先释放锁，也是可行的，也就是在整个扩容期间一直持有锁，<br>
     *但是扩容是需要花时间的，如果扩容时还占用锁那么其他线程在这个时候是不能进行出队和入队操作的，<br>
     *这大大降低了并发性。所以为了提高性能，使用CAS控制只有一个线程可以进行扩容，并且在扩容前释放锁，<br>
     *让其他线程可以进行入队和出队操作。<br>
     *<br>
	 *spinlock锁使用CAS制只有一个线程可以进行扩容，<br>
	 *CAS失败的线程会调用Thread.yield（）让出CPU，<br>
	 *目的是让扩容线程扩容后优先调用lock.lock重新获取锁，<br>
	 *但是这得不到保证。有可能yield的线程在扩容线程扩容完成前己经退出，<br>
	 *并执行代码（6）获取到了锁，这时候获取到锁线程发现newArray为null就会执行代码（l）。<br>
	 *如果当前数组扩容还没完毕，当前线程会再次调用tryGrow方法，<br>
	 *然后释放锁，这又给扩容线程获取锁提供机会，<br>
	 *如果这时候扩容线程还没扩容完毕，则当前线程释放锁后又调用yield方法让出CPU。<br>
	 *所以当扩容线程进行扩容时，其他线程原地自旋通过代码（1）检查当前扩容是否完毕，<br>
	 *扩容完毕后才退出代码（l）的循环。
	 *扩容线程扩容完毕后会重置自旋锁变量allocationSpinLock为0，<br>
	 *这里并没有使用UNSAE方法的CAS进行设置是因为同时只可能有一个线程获取到该锁，<br>
	 *并且allocationSpinLock被修饰为了volatile的。<br>
	 *当扩容线程扩容完毕后会执行代码（6）获取锁，<br>
	 *获取锁后复制当前queue里面的元素到新数组。<br>
     * @param array the heap array
     * @param oldCap the length of the array
     */
    private void tryGrow(Object[] array, int oldCap) {
    	//释放获取的锁
        lock.unlock(); // must release and then re-acquire main lock
        Object[] newArray = null;
        //CAS成功则扩容
        if (allocationSpinLock == 0 &&
            UNSAFE.compareAndSwapInt(this, allocationSpinLockOffset,
                                     0, 1)) {
            try {
            	//oldGap<64则扩容，执行oldcap+2,否则扩容50%，并且最大为MAX_ARRAY_SIZE
                int newCap = oldCap + ((oldCap < 64) ?
                                       (oldCap + 2) : // grow faster if small
                                       (oldCap >> 1));
                if (newCap - MAX_ARRAY_SIZE > 0) {    // possible overflow
                    int minCap = oldCap + 1;
                    if (minCap < 0 || minCap > MAX_ARRAY_SIZE)
                        throw new OutOfMemoryError();
                    newCap = MAX_ARRAY_SIZE;
                }
                if (newCap > oldCap && queue == array)
                    newArray = new Object[newCap];
            } finally {
                allocationSpinLock = 0;
            }
        }
//      5.第一个线程CAS成功后，第二个线程会进入这段代码，然后第二个线程让出CPU，尽量让第一个线程获取锁，但是这得不到保证。
        if (newArray == null) // back off if another thread is allocating
            Thread.yield();
//       6.
        lock.lock();
        if (newArray != null && queue == array) {
            queue = newArray;
            System.arraycopy(array, 0, newArray, 0, oldCap);
        }

    }

    /**
     * Mechanics for poll().  Call only while holding lock.<hr>
     * <ul>
     * <li>如果队列为空则直接返回null，否则执行代码(l)获取数组第一个元素作为返回值存放到变量Result中，</li>
     *  <li>这里需要注意，数组里面的第一个元素是优先级最小者最大的元素，出队操作就是返回这个元素。</li>
     *  <li>然后代码（2）获取队列尾部元素并存放到变量x中，且置空尾部节点，</li>
     *  <li>然后执行代码3将变量x插入到数组下标为0的位置，之后重新调整堆为最大或者最小堆，</li>
     *  <li>然后返回。这里重要的是，去掉堆的根节点后，如何使用剩下的节点重新调整一个大或者最小堆。</li>
     * </ul>
     * 
     */
    private E dequeue() {
    	//队列为空则返回null
        int n = size - 1;
        if (n < 0)
            return null;
        else {
        	//1获取队头元素
            Object[] array = queue;
            E result = (E) array[0];
            //2获取队尾元素并赋值为null
            E x = (E) array[n];
            array[n] = null;
            Comparator<? super E> cmp = comparator;
            if (cmp == null) //3 看看siftDownComparable的实现代码
                siftDownComparable(0, x, array, n);
            else
                siftDownUsingComparator(0, x, array, n, cmp);
            size = n;//4
            return result;
        }
    }

    /**
     * Inserts item x at position k, maintaining heap invariant by
     * promoting x up the tree until it is greater than or equal to
     * its parent, or is the root.
     *
     * To simplify and speed up coercions and comparisons. the
     * Comparable and Comparator versions are separated into different
     * methods that are otherwise identical. (Similarly for siftDown.)
     * These methods are static, with heap state as arguments, to
     * simplify use in light of possible comparator exceptions.<br>
     *假设队列初始化容量为2，创建的优先级队列的泛型数为Integer。<br>
     *I.首先调用队列的offer(2)方法，希望向队列插入元素2，插入前队列状态如下所示：<br>
     *先执行代码（1），从图中的变量值可知判断结果为false，所以紧接着执行代码2。<br>
     *由于k=n=size=O，所以代码（7)的判断结果为false，因此会执行代码8）直接把元素2入队。<br>
     *最后执行代码（9）将size的值加1，这时候队列的状态如下所示：<br>
     *
     *II.第二次调用队列的offer(4)时，首先执行代码(l)，从图中的变量值可知判断结果为false，<br>
     *所以执行代码（2）。由于k=1，所以进入while循环，由于parent=O;e=2;key=4;<br>
     *默认元素比较器使用元素的compareTo方法，可知key>e，<br>
     *所以执行break退出siftUpComparable中的循环，<br>
     *然后把元素存到数组下标为1的地方。<br>
     *最后执行代码（9）将size的值加1，这时候队列状态如下所示：<br>
     *
     *第三次调用队列的offer(6）时，首先执行代码(1)，从图中的变量值知道，这时候判断结果为true，<br>
     *所以调用tryGrow进行数组扩容由于2<64，所以执行newCap=2+(2+2)=6，然后创建新数组并复制，<br>
     *之后调用siftUpComparable法。由于k=2>0，故进入while循环，由于parent=O;e=2;key=6;key>e，<br>
     *所以执行break后退出while循环，并把元素6放入数组下标为2的方。<br>
     *最后将size的值加1，现在队列状态如下所示：<br>
     *
     *
     *IV.第四次调用队列的offer(l）时，首先执行代码（l），<br>
     *从图中的变量值知道，这次判断结果为false，<br>
     *所以执行代码（2）。<br>
     *由于k=3，所以进入while循，由于parent=1;e=4;key=1;key < e ，<br>
     *所以把元素4复制到数组下标为3的地方。<br>
     *然后执行k=1，再次循环，发现e=2,key=l，key<e，所以复制元素2到数组下标1处，<br>
     *然后k=O退出循环。最后把元素1存放到下标为0的地方，<br>
     *现在的状态如下所示：<br>
     *
     *由此可见，堆的根元素是1，也就是这是一个最小堆，<br>
     *那么当调用这个优先级队列的poll方法时，会依次返回堆里面值最小的元素。<br>
     *
     * @param k the position to fill
     * @param x the item to insert
     * @param array the heap array
     */
    private static <T> void siftUpComparable(int k, T x, Object[] array) {
        Comparable<? super T> key = (Comparable<? super T>) x;
       //队列元素个数>0则判断插入位置，否则直接入队（7）
        while (k > 0) {
            int parent = (k - 1) >>> 1;
            Object e = array[parent];
            if (key.compareTo((T) e) >= 0)
                break;
            array[k] = e;
            k = parent;
        }
        array[k] = key;//(8)
    }

    private static <T> void siftUpUsingComparator(int k, T x, Object[] array,
                                       Comparator<? super T> cmp) {
        while (k > 0) {
            int parent = (k - 1) >>> 1;
            Object e = array[parent];
            if (cmp.compare(x, (T) e) >= 0)
                break;
            array[k] = e;
            k = parent;
        }
        array[k] = x;
    }

    /**
     * Inserts item x at position k, maintaining heap invariant by
     * demoting x down the tree repeatedly until it is less than or
     * equal to its children or is a leaf.
     * 介绍上面调整堆的算法过程。接着上节队列的状态继续讲解，在上一节中队列元素序列为1、2、6、4
     * <ul>
     *  <li>第一次调用队列的poll（）方法时，首先执行代码（1）和代码（2），这时候变量，</li>
     *  <li>size=4;n=3;result=1;x=4·此时队列状态如下所示。</li>
	 *	<li>1，2，6</li>
	 *	<li>然后执行代码（3）调整堆后队列状态为</li>
	 *	<li>2,4,6</li>
	 *	<li>第二次调用队列的poll（）方法时，首先执行代码（1）和代码（2），这时候变量<li>size=3;n=2;result=2;x=6；此时队列状态为</li>
	 *	<li>2,4</li>
	 *	<li>然后执行代码（3）调整堆后队列状态为</li>
	 *	<li>4,6</li>
	 *	<li>第三次调用队列的poll（）方法时，首先执行代码（1)和代码（2），这时候变量<li>size=2;n=1;result=4;x=6：，此时队列状态为</li>
	 *	<li>4</li>
	 * 	<li>然后执行代码（3）调整堆后队列状态为</li>
	 *	<li>4</li>
	 *	<li>第四次直接返回元素6</li>
	 *  <li>下面重点说说siftDownComparable调整堆的算法。
	 *	<li>首先介绍下堆调整的思路。
	 *	<li>由于队列数组第0个元素为树根，因此出队时要移除它。
	 *  <li>这时数组就不再是最小的堆了，所以需要调整堆。
	 *  <li>具体是从被移除的树根的左右子树中找一个最小的值来当树根，
	 *  <li> 左右子树又会找自己左右子树里面那个最小，这是一个递归过程，
	 *  <li>直到树叶节点结束递归。如果不太明白，没关系，
	 *  <li>下面我们结合图来说明，假如当前队列内容如下：
	 *   2，4,6,8,10,11 二叉树图
	 *  这时候如果调用了poll()，那么result=2;x=11，并且队列末尾的元素被设置为null,
	 *  然后对于剩下的元素，调整堆的步骤如下图所示：图（l）中树根的leftChildVal=4;rightChildVal=6；
	 *  由于4<6，所以c=4。然后由于11，也就是key>c，所以使用元素4覆盖树根节点的值，现在堆对应的树如图（2）所示。
	 *  然后树根的左子树树根的左右孩子节点中的leftChildVal=8;rightChildVal=10；由于8<10，所以c=8。然后由于11>8，
	 *  也就是key>c，所以元素8作为树根左子树的根节点，现在树的形状如图（3）所示。这时候判断是否k<half，
	 *  结果为false，所以退出循环。然后把x=ll的元素设置到数组下标为3的地方，这时候堆树如图（4）所示，至此调整堆完毕。
	 *  sitDownComparable返回的result=2，所以poll方法也返回了。
	 *	</ul>
     * @param k the position to fill
     * @param x the item to insert
     * @param array the heap array
     * @param n heap size
     */
    private static <T> void siftDownComparable(int k, T x, Object[] array,
                                               int n) {
        if (n > 0) {
            Comparable<? super T> key = (Comparable<? super T>)x;
            int half = n >>> 1;           // loop while a non-leaf
            while (k < half) {
                int child = (k << 1) + 1; // assume left child is least
                Object c = array[child];//5
                int right = child + 1;//6
                if (right < n &&
                    ((Comparable<? super T>) c).compareTo((T) array[right]) > 0)//7
                    c = array[child = right];
                if (key.compareTo((T) c) <= 0)//8
                    break;
                array[k] = c;
                k = child;
            }
            array[k] = key;//9
        }
    }

    private static <T> void siftDownUsingComparator(int k, T x, Object[] array,
                                                    int n,
                                                    Comparator<? super T> cmp) {
        if (n > 0) {
            int half = n >>> 1;
            while (k < half) {
                int child = (k << 1) + 1;
                Object c = array[child];
                int right = child + 1;
                if (right < n && cmp.compare((T) c, (T) array[right]) > 0)
                    c = array[child = right];
                if (cmp.compare(x, (T) c) <= 0)
                    break;
                array[k] = c;
                k = child;
            }
            array[k] = x;
        }
    }

    /**
     * Establishes the heap invariant (described above) in the entire tree,
     * assuming nothing about the order of the elements prior to the call.
     */
    private void heapify() {
        Object[] array = queue;
        int n = size;
        int half = (n >>> 1) - 1;
        Comparator<? super E> cmp = comparator;
        if (cmp == null) {
            for (int i = half; i >= 0; i--)
                siftDownComparable(i, (E) array[i], array, n);
        }
        else {
            for (int i = half; i >= 0; i--)
                siftDownUsingComparator(i, (E) array[i], array, n, cmp);
        }
    }

    /**
     * Inserts the specified element into this priority queue.
     *
     * @param e the element to add
     * @return {@code true} (as specified by {@link Collection#add})
     * @throws ClassCastException if the specified element cannot be compared
     *         with elements currently in the priority queue according to the
     *         priority queue's ordering
     * @throws NullPointerException if the specified element is null
     */
    public boolean add(E e) {
        return offer(e);
    }

    /**
     * Inserts the specified element into this priority queue.
     * As the queue is unbounded, this method will never return {@code false}.
     * offer操作的作用是在队列中插入一个元素，由于是无界队列，所以一直返回true
     * @param e the element to add
     * @return {@code true} (as specified by {@link Queue#offer})
     * @throws ClassCastException if the specified element cannot be compared
     *         with elements currently in the priority queue according to the
     *         priority queue's ordering
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e) {
        if (e == null)
            throw new NullPointerException();
        //获取独占锁
        final ReentrantLock lock = this.lock;
        lock.lock();
        int n, cap;
        Object[] array;
        //代码1 如果当前元素个数大于等于队列容量，则扩容
        while ((n = size) >= (cap = (array = queue).length))
        	//扩容逻辑 详细见方法
            tryGrow(array, cap);
        try {
            Comparator<? super E> cmp = comparator;
            //默认比较器为null
            if (cmp == null)
            	//建堆算法
                siftUpComparable(n, e, array);
            else
            	//自定义比较器
                siftUpUsingComparator(n, e, array, cmp);
            //将队列元素数增加1并且激活notEmtpy的条件队列里面的一个阻塞线程
            size = n + 1;
            notEmpty.signal();//激活因调用take方法而被阻塞的线程
        } finally {
        	//释放独占锁
            lock.unlock();
        }
        return true;
    }

    /**
     * Inserts the specified element into this priority queue.
     * As the queue is unbounded, this method will never block.
     *
     * @param e the element to add
     * @throws ClassCastException if the specified element cannot be compared
     *         with elements currently in the priority queue according to the
     *         priority queue's ordering
     * @throws NullPointerException if the specified element is null
     */
    public void put(E e) {
        offer(e); // never need to block
    }

    /**
     * Inserts the specified element into this priority queue.
     * As the queue is unbounded, this method will never block or
     * return {@code false}.
     * put操作内部调用的是offer操作，由于是无界队列，所以不需要阻塞。
     * @param e the element to add
     * @param timeout This parameter is ignored as the method never blocks
     * @param unit This parameter is ignored as the method never blocks
     * @return {@code true} (as specified by
     *  {@link BlockingQueue#offer(Object,long,TimeUnit) BlockingQueue.offer})
     * @throws ClassCastException if the specified element cannot be compared
     *         with elements currently in the priority queue according to the
     *         priority queue's ordering
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e, long timeout, TimeUnit unit) {
        return offer(e); // never need to block
    }
    
    /**
     * poll操作的作用是获取队列内部堆树的根节点元素，
     * 如果队列为空，则返回null。<br>
     * 在进行出队操作时要先加锁，这意味着，当前线程在进行出队操作时，<br>
     * 其他线程不能再进行入队和出队操作，但是前面在介绍offer函数时介绍过，<br>
     * 这时候其他线程可以进行扩容。<br>
     */
    public E poll() {
        final ReentrantLock lock = this.lock;
        lock.lock();//获取独占锁
        try {
        	//执行出队操作的dequeue方法
            return dequeue();
        } finally {
            lock.unlock();//释放独占锁
        }
    }
    
    /**
     * take操作的作用是获取队列内部堆树的根节点元素，
     * 如果队列为空则阻塞，如以下代码所示。
     * <hr>
     * 首先通过lock.locklnterruptibly（）获取独占锁，以这个方式获取的锁会对中断进行响应。<br>
     * 然后调用dequeue方法返回堆树根节点元素，如果队列为空，则返回false。<br>
     * 然后当前线程调用notEmpty.await（）阻塞挂起自己，<br>
     * 直到有线程调用了offer（）方法（在offer方法内添加元素成功后会调用notEmpty.signal方法，<br>
     * 这会激活一个阻塞在notEmpty的条件队列里面的一个线程）。<br>
     * 另外，这里使用while循环而不是if语句是为了避免虚假唤醒。<br>
     */
    public E take() throws InterruptedException {
    	//获取锁可被中断
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        E result;
        try {
        	//如果队列为空，则阻塞，把当前线程放入notEmpty条件队列中
            while ( (result = dequeue()) == null)
                notEmpty.await();//阻塞当前线程
        } finally {
            lock.unlock();//释放锁
        }
        return result;
    }

    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        E result;
        try {
            while ( (result = dequeue()) == null && nanos > 0)
                nanos = notEmpty.awaitNanos(nanos);
        } finally {
            lock.unlock();
        }
        return result;
    }

    public E peek() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return (size == 0) ? null : (E) queue[0];
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the comparator used to order the elements in this queue,
     * or {@code null} if this queue uses the {@linkplain Comparable
     * natural ordering} of its elements.
     *
     * @return the comparator used to order the elements in this queue,
     *         or {@code null} if this queue uses the natural
     *         ordering of its elements
     */
    public Comparator<? super E> comparator() {
        return comparator;
    }
    /**
     * 计算队列元素个数。如下代码在返回size前加了锁，
     * 以保证在调用size（）方法时不会有其他线程进行入队和出队操作。
     * 另外，由于size变量没有被修饰为volatie的，
     * 所以这里加锁也保证了在多线程下size变量的内存可见性。
     */
    public int size() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return size;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Always returns {@code Integer.MAX_VALUE} because
     * a {@code PriorityBlockingQueue} is not capacity constrained.
     * @return {@code Integer.MAX_VALUE} always
     */
    public int remainingCapacity() {
        return Integer.MAX_VALUE;
    }

    private int indexOf(Object o) {
        if (o != null) {
            Object[] array = queue;
            int n = size;
            for (int i = 0; i < n; i++)
                if (o.equals(array[i]))
                    return i;
        }
        return -1;
    }

    /**
     * Removes the ith element from queue.
     */
    private void removeAt(int i) {
        Object[] array = queue;
        int n = size - 1;
        if (n == i) // removed last element
            array[i] = null;
        else {
            E moved = (E) array[n];
            array[n] = null;
            Comparator<? super E> cmp = comparator;
            if (cmp == null)
                siftDownComparable(i, moved, array, n);
            else
                siftDownUsingComparator(i, moved, array, n, cmp);
            if (array[i] == moved) {
                if (cmp == null)
                    siftUpComparable(i, moved, array);
                else
                    siftUpUsingComparator(i, moved, array, cmp);
            }
        }
        size = n;
    }

    /**
     * Removes a single instance of the specified element from this queue,
     * if it is present.  More formally, removes an element {@code e} such
     * that {@code o.equals(e)}, if this queue contains one or more such
     * elements.  Returns {@code true} if and only if this queue contained
     * the specified element (or equivalently, if this queue changed as a
     * result of the call).
     *
     * @param o element to be removed from this queue, if present
     * @return {@code true} if this queue changed as a result of the call
     */
    public boolean remove(Object o) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int i = indexOf(o);
            if (i == -1)
                return false;
            removeAt(i);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Identity-based version for use in Itr.remove
     */
    void removeEQ(Object o) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] array = queue;
            for (int i = 0, n = size; i < n; i++) {
                if (o == array[i]) {
                    removeAt(i);
                    break;
                }
            }
        } finally {
            lock.unlock();
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
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return indexOf(o) != -1;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns an array containing all of the elements in this queue.
     * The returned array elements are in no particular order.
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
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return Arrays.copyOf(queue, size);
        } finally {
            lock.unlock();
        }
    }

    public String toString() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int n = size;
            if (n == 0)
                return "[]";
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            for (int i = 0; i < n; ++i) {
                Object e = queue[i];
                sb.append(e == this ? "(this Collection)" : e);
                if (i != n - 1)
                    sb.append(',').append(' ');
            }
            return sb.append(']').toString();
        } finally {
            lock.unlock();
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
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int n = Math.min(size, maxElements);
            for (int i = 0; i < n; i++) {
                c.add((E) queue[0]); // In this order, in case add() throws.
                dequeue();
            }
            return n;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically removes all of the elements from this queue.
     * The queue will be empty after this call returns.
     */
    public void clear() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] array = queue;
            int n = size;
            size = 0;
            for (int i = 0; i < n; i++)
                array[i] = null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns an array containing all of the elements in this queue; the
     * runtime type of the returned array is that of the specified array.
     * The returned array elements are in no particular order.
     * If the queue fits in the specified array, it is returned therein.
     * Otherwise, a new array is allocated with the runtime type of the
     * specified array and the size of this queue.
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
    public <T> T[] toArray(T[] a) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int n = size;
            if (a.length < n)
                // Make a new array of a's runtime type, but my contents:
                return (T[]) Arrays.copyOf(queue, size, a.getClass());
            System.arraycopy(queue, 0, a, 0, n);
            if (a.length > n)
                a[n] = null;
            return a;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns an iterator over the elements in this queue. The
     * iterator does not return the elements in any particular order.
     *
     * <p>The returned iterator is
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * @return an iterator over the elements in this queue
     */
    public Iterator<E> iterator() {
        return new Itr(toArray());
    }

    /**
     * Snapshot iterator that works off copy of underlying q array.
     */
    final class Itr implements Iterator<E> {
        final Object[] array; // Array of all elements
        int cursor;           // index of next element to return
        int lastRet;          // index of last element, or -1 if no such

        Itr(Object[] array) {
            lastRet = -1;
            this.array = array;
        }

        public boolean hasNext() {
            return cursor < array.length;
        }

        public E next() {
            if (cursor >= array.length)
                throw new NoSuchElementException();
            lastRet = cursor;
            return (E)array[cursor++];
        }

        public void remove() {
            if (lastRet < 0)
                throw new IllegalStateException();
            removeEQ(array[lastRet]);
            lastRet = -1;
        }
    }

    /**
     * Saves this queue to a stream (that is, serializes it).
     *
     * For compatibility with previous version of this class, elements
     * are first copied to a java.util.PriorityQueue, which is then
     * serialized.
     *
     * @param s the stream
     * @throws java.io.IOException if an I/O error occurs
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {
        lock.lock();
        try {
            // avoid zero capacity argument
            q = new PriorityQueue<E>(Math.max(size, 1), comparator);
            q.addAll(this);
            s.defaultWriteObject();
        } finally {
            q = null;
            lock.unlock();
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
        try {
            s.defaultReadObject();
            this.queue = new Object[q.size()];
            comparator = q.comparator();
            addAll(q);
        } finally {
            q = null;
        }
    }

    // Similar to Collections.ArraySnapshotSpliterator but avoids
    // commitment to toArray until needed
    static final class PBQSpliterator<E> implements Spliterator<E> {
        final PriorityBlockingQueue<E> queue;
        Object[] array;
        int index;
        int fence;

        PBQSpliterator(PriorityBlockingQueue<E> queue, Object[] array,
                       int index, int fence) {
            this.queue = queue;
            this.array = array;
            this.index = index;
            this.fence = fence;
        }

        final int getFence() {
            int hi;
            if ((hi = fence) < 0)
                hi = fence = (array = queue.toArray()).length;
            return hi;
        }

        public Spliterator<E> trySplit() {
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            return (lo >= mid) ? null :
                new PBQSpliterator<E>(queue, array, lo, index = mid);
        }

        @SuppressWarnings("unchecked")
        public void forEachRemaining(Consumer<? super E> action) {
            Object[] a; int i, hi; // hoist accesses and checks from loop
            if (action == null)
                throw new NullPointerException();
            if ((a = array) == null)
                fence = (a = queue.toArray()).length;
            if ((hi = fence) <= a.length &&
                (i = index) >= 0 && i < (index = hi)) {
                do { action.accept((E)a[i]); } while (++i < hi);
            }
        }

        public boolean tryAdvance(Consumer<? super E> action) {
            if (action == null)
                throw new NullPointerException();
            if (getFence() > index && index >= 0) {
                @SuppressWarnings("unchecked") E e = (E) array[index++];
                action.accept(e);
                return true;
            }
            return false;
        }

        public long estimateSize() { return (long)(getFence() - index); }

        public int characteristics() {
            return Spliterator.NONNULL | Spliterator.SIZED | Spliterator.SUBSIZED;
        }
    }

    /**
     * Returns a {@link Spliterator} over the elements in this queue.
     *
     * <p>The returned spliterator is
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * <p>The {@code Spliterator} reports {@link Spliterator#SIZED} and
     * {@link Spliterator#NONNULL}.
     *
     * @implNote
     * The {@code Spliterator} additionally reports {@link Spliterator#SUBSIZED}.
     *
     * @return a {@code Spliterator} over the elements in this queue
     * @since 1.8
     */
    public Spliterator<E> spliterator() {
        return new PBQSpliterator<E>(this, null, 0, -1);
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long allocationSpinLockOffset;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> k = PriorityBlockingQueue.class;
            allocationSpinLockOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("allocationSpinLock"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}

