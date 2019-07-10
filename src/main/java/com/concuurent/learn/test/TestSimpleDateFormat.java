package com.concuurent.learn.test;

import java.text.ParseException;
import java.text.SimpleDateFormat;

/**
 * SimpleDateFom1at是Java提供的一个格式化和解析日期的工具类，
 * 在日常开发中经常会用到，但是由于它是线程不安全的，
 * 所以多线程共用一个SimpleDateFormat实例对日期进行解析或者格式化会导致程序出错。
 * 本节来揭示它为何是线程不安全的，以及如何避免该问题。
 * @author Wesley
 * 为了复现问题，编写如下代码
 * 2019年7月10日下午2:55:59
 * @Version 1.0
 */
public class TestSimpleDateFormat {
	
	//1.创建单例实例
	static SimpleDateFormat simpleDateFormat=new SimpleDateFormat("yyy-MM-dd HH:mm:ss");
	
	
	public static void main(String[] args) {
		//2.创建多个线程启动
		for (int i = 0; i < 10; i++) {
			Thread thread=new Thread(new Runnable() {
				
				@Override
				public void run() {
					try {
						//3.使用单例日期实例解析文本
						System.out.println(simpleDateFormat.parse("2019-05-14 15:00:10"));
					} catch (ParseException e) {
						e.printStackTrace();
					}
				}
			});
	       thread.start();
		}
	}
	/**
	 * 代码（1）创建了SimpleDateFormat的一个实例，
	 * 代码（2）创建10个程，每个线程都共用同一个sdf对象对文本日期进行解析。
	 * 多运行几代码就会抛出java.lang.NumberFormatException异常，
	 * 增加线程的个数有利于复现该问题。
	 */
}
