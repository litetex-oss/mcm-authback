package net.litetex.authback.client.config;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


public enum SignalingState
{
	DEFAULT,
	AUTO_DISABLE,
	DISABLE;
	
	public static final Map<String, SignalingState> VALUES = Arrays.stream(SignalingState.values())
		.collect(Collectors.toMap(SignalingState::name, Function.identity()));
}
