package com.concuurent.learn.copy;

import java.util.List;

public interface StrategyService {
	
	public void sendMsg(List<Msg> msgList,List<String> deviceIdList);

}
