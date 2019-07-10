package com.concuurent.learn.test;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import com.alibaba.fastjson.JSON;

public class TestMap2 {
	//（1）创建map，key为topic，value为设备列表
	static ConcurrentHashMap<String, List<String>> map=new ConcurrentHashMap<String, List<String>>();
	
	public static void main(String[] args) {
		
	
	//（2）进入直播topic1,线程one
	Thread threadOne=new Thread(new Runnable() {
		
		@Override
		public void run() {
          List<String> list1=new ArrayList<String>();		
          list1.add("device1");
          list1.add("device2");
          //2.1 
          List<String> oldList = map.putIfAbsent("topic1", list1);
          if(null != oldList) {
        	  oldList.addAll(list1);
          }
          System.out.println(JSON.toJSONString(map));
		}
	});
	//（3）进入直播topic1,线程two
	Thread threadTwo=new Thread(new Runnable() {
		@Override
		public void run() {
			List<String> list2=new ArrayList<String>();		
			list2.add("device11");
			list2.add("device22");
			List<String> oldList = map.putIfAbsent("topic1", list2);
	          if(null != oldList) {
	        	  oldList.addAll(list2);
	          }
			System.out.println(JSON.toJSONString(map));
		}
	});
	//（4）进入直播topic2,线程three
	Thread threadThree=new Thread(new Runnable() {
		@Override
		public void run() {
			List<String> list2=new ArrayList<String>();		
			list2.add("device111");
			list2.add("device222");
			List<String> oldList = map.putIfAbsent("topic2", list2);
	          if(null != oldList) {
	        	  oldList.addAll(list2);
	          }
			System.out.println(JSON.toJSONString(map));
		}
	});
	  threadOne.start();
	  threadTwo.start();
	  threadThree.start();
	
	}
	
	/**
	 * 在如上代码（2.1）中使用map.putlfAbsent方法添加新设备列表，
	 * 如果topic1在map不存在，则将topic1和对应设备列表放入map。
	 * 要注意的是，这个判断和放入是原子性操作，放入后会返回null。
	 * 如果topic1已经在map里面存在，则调用putlfAbsent返回topic1对应的设备列表，
	 * 若发现返回的设备列表不为null则把新的设备列表添加到返回的设备列表里面，
	 * 从而问题得到解决。
	 * 
	 * put(K key,V value）方法判断如果key己经存在，则使用value覆盖来的值并返回原来的值，
	 * 如果不存在则把value放入并返回null。而putlfAbsent(K key,V value)方法则是如果key
	 * 己经存在则直接返回原来对应的值并不使用value覆盖，如果key不存在则放入value并返回null，
	 * 另外要注意，判断key是否存在和放入是原子性操作。
	 */
}
