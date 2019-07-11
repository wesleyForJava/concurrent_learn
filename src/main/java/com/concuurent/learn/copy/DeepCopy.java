package com.concuurent.learn.copy;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.beanutils.BeanUtils;

import com.concuurent.learn.copy.impl.StrategyServiceOne;
import com.concuurent.learn.copy.impl.StrategyServiceTwo;

/**
 * 本节通过一个简单的消息发送demo来讲解。
 * 首先介绍消息发送的场景，比如每个安装有手淘App的移动设备有一个设备ID，
 * 每个App（比如于淘App）有个appkey用来标识这个应用。
 * 可以根据不同的appkey选择不同的发送策略，
 * 对注册到自己的设备进行消息发送，
 * 每个消息有一个消息ID和消息体字段。
 * 下面首先贴出实例代码，如下所示。
 * @author Wesley
 *
 * 2019年7月10日下午4:46:00
 * @Version 1.0
 */
public class DeepCopy {
	
	static Map<Integer, StrategyService> serviceMap=new HashMap<Integer, StrategyService>();

	static {
		serviceMap.put(111, new StrategyServiceOne());
		serviceMap.put(222, new StrategyServiceTwo());
	}
	
	public static void main(String[] args) throws IllegalAccessException, InstantiationException, InvocationTargetException, NoSuchMethodException {
		//2.key为Appkey，value为设备id列表
		Map<Integer, List<String>> appKeyMap=new HashMap<>();
		
		//3.创建Appkey为111的设备列表
		List<String> oneList=new ArrayList<String>();
		oneList.add("device_id1");
		appKeyMap.put(111, oneList);
		//创建Appkey为222的设备列表
		List<String> twoList=new ArrayList<String>();
		twoList.add("device_id2");
		appKeyMap.put(222, twoList);
		//4.创建消息
		List<Msg> msgList=new ArrayList<Msg>();
		Msg msg=new Msg();
		msg.setDataId("abc");
		msg.setBody("hello");
		msgList.add(msg);
		
		//5.根据不同的appkey使用不同的策略进行处理
//		Iterator<Integer> appkeyItr = appKeyMap.keySet().iterator();
//        while (appkeyItr.hasNext()) {
//			int appkey = (Integer) appkeyItr.next();
//			//这里根据appkey获取自己的消息列表
//			StrategyService strategyService = serviceMap.get(appkey);
//			if(null != strategyService) {
//	 version1			strategyService.sendMsg(msgList, appKeyMap.get(appkey));
//	 version2	 
//				strategyService.sendMsg(new ArrayList<Msg>(msgList), appKeyMap.get(appkey));
//			}else {
//				System.out.println(String.format("appkey:%s is not registered service", appkey));
//			}
//		}
//        
		//	问题产生了。这个例子运行的结果是固定的，
		//	但是如果在每个发送消息的sendMsg法里面异步修改消息的Datald，
		//	那么运行的结果就不是固定的了。
	
		//	分析输出结果可以知道，代码（5）先执行了appkey=222的发送消息服务，
		//	然后再执行appkey=lll的服务，之所以后者打印出来的Datald是oneService_TwoService而不是oneService，
		//	是因为在appkey=222的消息服务里面修改了消息体msg的Datald为TwoService_abc，
		//	而方法sendMsg里面的消息是引用传递的，所以导致appkey=l11的服务
		//	在调用sendMsg方法时msg里面的Datald已经变成了TwoService_abc，
		//	然后在sendMs方法内部又会在它的前面添加oneService前缀，最后Datald就变成了
		//	oneServiceTwoServiceabc。那么该问题如何解决呢？首先应该想到的是不同的appkey
		//	应该有自己的一份List<Msg＞，这样不同的服务只会修改自己的消息的Datald而不会相互影响。
		//	那么下面修代码（5）中的部分代码如version2
		//	就是在具体发送消息前重新new一个消息表传递过去，这样应该可以了吧？
		//	其实这还是不行的，因为如果appkey的个数大于1，那么在第二个appkey服务
		//	发送时ArrayList构造函数里面的msgList己经是第一个appkey的服务修改后的了。
		//	那么自然会想到应该在代码（5）前面给每个appkey事先准备好自己的消息列表，
		//	那么新增和修改代码（5）如下。
		
		//这里给每个appkey准备自己的消息列表
//		Iterator<Integer> appkeyItr = appKeyMap.keySet().iterator();
//		Map<Integer, List<Msg>> appKeyMsgMap=new HashMap<Integer, List<Msg>>();
//		while (appkeyItr.hasNext()) {
//			appKeyMsgMap.put(appkeyItr.next(),new ArrayList(msgList));
//		}
//		appkeyItr = appKeyMap.keySet().iterator();
//		//5.根据不同的appkey使用不同的策略进行处理
//        while (appkeyItr.hasNext()) {
//			int appkey = (Integer) appkeyItr.next();
//			//这里根据appkey获取自己的消息列表
//			StrategyService strategyService = serviceMap.get(appkey);
//			if(null != strategyService) {
//				strategyService.sendMsg(new ArrayList<Msg>(msgList), appKeyMap.get(appkey));
//			}else {
//				System.out.println(String.format("appkey:%s is not registered service", appkey));
//			}
//		}
		//上代码首先给每个appkey创建消息列表，然后放入appKeyMsgMap。
		//之后在代码（5)具体发送消息时根据appkey去获取相应的消息列表，
		//这样应该没问题了吧？但是当你信心满满地执行并查看结果时就傻眼了，
		//结果竟然和之前的一样。
		//那么问题出在哪里呢？给每个appkey搞一份消息列表，
		//然后发送时使用自己的消息列表进行发送，这个策略是没问题的，
		//那么只有一个情况，就是给每个appkey建一份消息列表时出错了，
		//所有appkey用的还是同一份列表。难道new ArrayList<>(msgList）里面还是引用？
		//其实确实是，因为Msg身是引用类型，而newArrayList<>(msgList）
		//这种方式是浅复制，每appkey消息列表都是对同一个Msg的用，修改代码如下。
		Iterator<Integer> appkeyItr = appKeyMap.keySet().iterator();
		Map<Integer, List<Msg>> appKeyMsgMap=new HashMap<Integer, List<Msg>>();
        while (appkeyItr.hasNext()) {
			//复制每个消息到临时消息列表
			List<Msg> tempList=new ArrayList<Msg>();
			Iterator<Msg> itrMsg = msgList.iterator();
			Msg tempMsg=null;
			while (itrMsg.hasNext()) {
				 tempMsg =	(Msg) BeanUtils.cloneBean(itrMsg.next());
				 if(null !=tempMsg) {
					 tempList.add(tempMsg);
				 }
			}
			//存放当前appkey对应的经过深复制的消息列表
			appKeyMsgMap.put(appkeyItr.next(),tempList);
		}
        
        appkeyItr = appKeyMap.keySet().iterator();
        
        while (appkeyItr.hasNext()) {
			Integer appkey = (Integer) appkeyItr.next();
			StrategyService strategyService = serviceMap.get(appkey);
			if(null != strategyService) {
			strategyService.sendMsg(appKeyMsgMap.get(appkey), appKeyMap.get(appkey));
			}else {
				System.out.println(String.format("appkey:%s is not registered service", appkey));
			}
		}
	}
//	本节通过一个简单的消息发送例子说明了需要复用但是会被下游修改的参数要进行深复制，
//	否则会导致出现错误的结果另外引用类型作为集合元素时，如果使用这个集合作为另外一个
//	集合的构造函数参数，会导致两个集合里面的同一个位置的元素指向的是同一引用，
//	这会导致对引用的修改在两个集合中都可见，所这时候需要对引用元素进行深复制。
	

}
