package com.concuurent.learn.copy.impl;

import java.util.List;

import com.alibaba.fastjson.JSON;
import com.concuurent.learn.copy.Msg;
import com.concuurent.learn.copy.StrategyService;

public class StrategyServiceTwo implements StrategyService {

	@Override
	public void sendMsg(List<Msg> msgList, List<String> deviceIdList) {
		
		for (Msg msg : msgList) {
			msg.setDataId("twoService_"+msg.getDataId());
			System.out.println(msg.getDataId()+" "+JSON.toJSONString(deviceIdList));
		}
		
	}

}
