package com.github.shansun.sparrow.actor.internal;

import com.github.shansun.sparrow.actor.spi.AbstractActor;
import com.github.shansun.sparrow.actor.api.Message;


/**
 * 发送消息的来源没有指定时，会自动设置为UnknownActor.
 * 
 * @author: lanbo <br>
 * @version: 1.0 <br>
 * @date: 2012-7-23
 */
public class UnknownActor extends AbstractActor {

	@Override
	public String getName() {
		return "unknown-actor";
	}

	@Override
	public String getCategory() {
		return "default";
	}

	@Override
	public boolean process(Message message) {
		return true;
	}

}
