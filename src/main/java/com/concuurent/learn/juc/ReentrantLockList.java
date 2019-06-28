package com.concuurent.learn.juc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ReentrantLockList {
	
	//线程不安全的list
	private List<String> list=new ArrayList<String>();
	
	//独占锁
	private  final ReentrantReadWriteLock lock=new ReentrantReadWriteLock();
      private final Lock readLock= lock.readLock();
      private final Lock writeLock= lock.writeLock();
      
      public void add(String e) {
    	  try {
			writeLock.lock();
			  list.add(e);
		} finally {
			writeLock.unlock();
		}
	}
      
      public void remove(String index) {
       	  try {
  			writeLock.lock();
  			  list.remove(index);
  		} finally {
  			writeLock.unlock();
  		}
	}
     //调用get方法时使用的是读锁，这样运行多个读线程来同时访问list的元素，这在读多写少的情况下性能会更好。
      public void get(int index) {
    	  try {
			readLock.lock();
			list.get(index);
		} finally {
			readLock.unlock();
		}

	}
}
