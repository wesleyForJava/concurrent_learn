package com.concuurent.learn.test;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

/**
 * 第三种方式：使用ThreadLocal这样每个线程只需要使用一个SimpleDateFormat实例，
 * 这相比第一种方式大大节省了对象的创建销毁开销，
 * 并且不需要使多个线程同步。
 * 使用ThreadLocal方式的代码如下
 * @author Wesley
 *
 * 2019年7月10日下午3:55:24
 * @Version 1.0
 */
public class TestSimpleDateFormat2 {
	
	//(1)创建ThreadLocal实例
	static ThreadLocal<DateFormat> safeSdf=new ThreadLocal<DateFormat>() {

		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat("yyy-MM-dd HH:mm:ss");
		}
	};
	
	public static void main(String[] args) {
		//2.创建多个线程启动
		for (int i = 0; i < 10; i++) {
			Thread thread=new Thread(new Runnable() {
				
				@Override
				public void run() {
					try {
							//3.使用单例日期实例解析文本
							System.out.println(safeSdf.get().parse("2019-05-14 15:00:10"));
					} catch (ParseException e) {
						e.printStackTrace();
					}finally {
						//4.使用完毕记得清除，避免内存泄露
						safeSdf.remove();
					}
				}
			});
	       thread.start();//5.启动线程
		}
	}
//	代码(1）创建了一个线程安全的SimpleDateFormat实例，
//	代码(3）首先使用get()方法获取当前线程下SimpleDateFormat的实例。
//	在第一次调用ThreadLocal的get（）方法时，
//	会触发其initia!Value方法创建当前线程所需要的SimpleDateFormat对象。
//	另外需要注意的是，在代码（4）中，使用完线程变量后，要进行清理，以避免内存泄漏。

}
