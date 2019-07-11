package com.concuurent.learn.thread;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
//import java.lang.ThreadLocal.SuppliedThreadLocal;
//import java.lang.ThreadLocal.ThreadLocalMap;
//import java.lang.ThreadLocal.ThreadLocalMap.Entry;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.concuurent.learn.thread.ThreadPoolTest.LocalVariable;

/**
 * 使用ThreadLocal不当可能会导致内存泄露
  *基础篇已经讲解了ThreadLocal的原理，
  *本节着重来讲解下使用ThreadLocal会导致内存泄露的原因，
  *并讲解使用ThreadLocal导致内存泄露的案例。
 * @author Wesley
 *
 * 2019年7月11日下午4:59:26
 * @Version 1.0
 */
public class ThreadLocal {

//	ThreadLocalMap内部是一个Entry数组,Entry继承自WeakReference，
//	Entry内部的value用来存放通过ThreadLocal的set方法传递的值，
//	那么ThreadLocal对象本身存放到哪里了吗?下面看看Entry的构造函数：
	
//	  Entry(ThreadLocal<?> k, Object v) {
//          super(k);
//          value = v;
//      }
	
//	static class WeakReference<T> extends Reference<T> {
//
//	    public WeakReference(T referent) {
//	        super(referent);
//	    }
//
//	    public WeakReference(T referent, ReferenceQueue<? super T> q) {
//	        super(referent, q);
//	    }
//
//	}
	
//	Reference(T referent, ReferenceQueue<? super T> queue) {
//        this.referent = referent;
//        this.queue = (queue == null) ? ReferenceQueue.NULL : queue;
//    }
	
	
//	可知k被传递到了WeakReference的构造函数里面，
//	也就是说ThreadLocalMap里面的key为ThreadLocal对象的弱引用，
//	具体是referent变量引用了ThreadLocal对象，
//	value为具体调用ThreadLocal的set方法传递的值。
	
//	当一个线程调用ThreadLocal的set方法设置变量时候，
//	当前线程的ThreadLocalMap里面就会存放一个记录，
//	这个记录的key为ThreadLocal的引用，value则为设置的值。
//	如果当前线程一直存在而没有调用ThreadLocal的remove方法，
//	并且这时候其它地方还是有对ThreadLocal的引用，
//	则当前线程的ThreadLocalMap变量里面会存在ThreadLocal变量的引用和value对象的引用是不会被释放的，
//	这就会造成内存泄露的。但是考虑如果这个ThreadLocal变量没有了其他强依赖，
//	而当前线程还存在的情况下，由于线程的ThreadLocalMap里面的key是弱依赖，
//	则当前线程的ThreadLocalMap里面的ThreadLocal变量的弱引用会被在gc的时候回收，
//	但是对应value还是会造成内存泄露，这时候ThreadLocalMap里面就会存在key为null但是value不为null的entry项。
//	其实在ThreadLocal的set和get和remove方法里面有一些时机是会对这些key为null的entry进行清理的，
//	但是这些清理不是必须发生的，下面简单说下ThreadLocalMap的remove方法的清理过程：
	
//	private void remove(ThreadLocal<?> key) {
//
//		  //(1)计算当前ThreadLocal变量所在table数组位置，尝试使用快速定位方法
//		  Entry[] tab = table;
//		  int len = tab.length;
//		  int i = key.threadLocalHashCode & (len-1);
//		  //(2)这里使用循环是防止快速定位失效后，遍历table数组
//		  for (Entry e = tab[i];
//		       e != null;
//		       e = tab[i = nextIndex(i, len)]) {
//		      //(3)找到
//		      if (e.get() == key) {
//		          //(4)找到则调用WeakReference的clear方法清除对ThreadLocal的弱引用
//		          e.clear();
//		          //(5)清理key为null的元素
//		          expungeStaleEntry(i);
//		          return;
//		      }
//		  }
//		}
//	private int expungeStaleEntry(int staleSlot) {
//        Entry[] tab = table;
//        int len = tab.length;
//
//        //（6）去掉去value的引用
//        tab[staleSlot].value = null;
//        tab[staleSlot] = null;
//        size--;
//
//        Entry e;
//        int i;
//        for (i = nextIndex(staleSlot, len);
//             (e = tab[i]) != null;
//             i = nextIndex(i, len)) {
//            ThreadLocal<?> k = e.get();
//            
//            //(7)如果key为null,则去掉对value的引用。
//            if (k == null) {
//                e.value = null;
//                tab[i] = null;
//                size--;
//            } else {
//                int h = k.threadLocalHashCode & (len - 1);
//                if (h != i) {
//                    tab[i] = null;
//                    while (tab[h] != null)
//                        h = nextIndex(h, len);
//                    tab[h] = e;
//                }
//            }
//        }
//        return i;
//    }
//	步骤（4）调用了Entry的clear方法，实际调用的是父类WeakReference的clear方法，作用是去掉对ThreadLocal的弱引用。
//	步骤（6）是去掉对value的引用，到这里当前线程里面的当前ThreadLocal对象的信息被清理完毕了。
//	代码（7）从当前元素的下标开始看table数组里面的其他元素是否有key为null的，有则清理。
//	循环退出的条件是遇到table里面有null的元素。所以这里知道null元素后面的Entry里面key 为null的元素不会被清理。
	
//	总结：ThreadLocalMap内部Entry中key使用的是对ThreadLocal对象的弱引用，
//	这为避免内存泄露是一个进步，因为如果是强引用，那么即使其他地方没有对ThreadLocal对象的引用，
//	ThreadLocalMap中的ThreadLocal对象还是不会被回收，而如果是弱引用则这时候ThreadLocal引用是会被回收掉的，
//	虽然对于的value还是不能被回收，这时候ThreadLocalMap里面就会存在key为null但是value不为null的entry项，
//	虽然ThreadLocalMap提供了set,get,remove方法在一些时机下会对这些Entry项进行清理，但是这是不及时的，
//	也不是每次都会执行的，所以一些情况下还是会发生内存泄露，所以在使用完毕后即使调用remove方法才是解决内存泄露的王道。



}
