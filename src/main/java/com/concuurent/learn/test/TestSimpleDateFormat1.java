package com.concuurent.learn.test;

import java.text.ParseException;
import java.text.SimpleDateFormat;

/**
 * 第一种方式：次使用时new一个SimpleDateFormat的实例，
 * 这样可以保证每个实例用自己的Calendar例，但是每次使用都需要new一个对象，
 * 并且使用后由于没有其他引用，又需要回收，开销会很大。
 * 第二种方式：出错的根本原因是因为多线程下代码（3）、代码（4）和代码（5）三个步骤不是一个原子性操作，
 * 那么容易到的是对它们进同步，让代码(3）、代码（4）和代码（5）成为原子性操作。
 * 可以使用synchronized进行同步，具体如下。
 * 进行同步意味着多个线程要竞争锁，在高并发场景下这会导致系统响应性能下降。
 * 第三种方式详见TestSimpleDateFormat2
 * @author Wesley
 *
 * 2019年7月10日下午3:52:41
 * @Version 1.0
 */
public class TestSimpleDateFormat1 {
	//1.创建单例实例
		static SimpleDateFormat sdf=new SimpleDateFormat("yyy-MM-dd HH:mm:ss");
		
		
		public static void main(String[] args) {
			//2.创建多个线程启动
			for (int i = 0; i < 10; i++) {
				Thread thread=new Thread(new Runnable() {
					
					@Override
					public void run() {
						try {
							synchronized (sdf) {
								//3.使用单例日期实例解析文本
								System.out.println(sdf.parse("2019-05-14 15:00:10"));
							}
						} catch (ParseException e) {
							e.printStackTrace();
						}
					}
				});
		       thread.start();
			}
		}
}
