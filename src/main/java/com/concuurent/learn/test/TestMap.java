package com.concuurent.learn.test;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import com.alibaba.fastjson.JSON;

public class TestMap {
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
          map.put("topic1", list1);
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
			map.put("topic1", list2);
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
			map.put("topic2", list2);
			System.out.println(JSON.toJSONString(map));
		}
	});
	  threadOne.start();
	  threadTwo.start();
	  threadThree.start();
	
	}
}
