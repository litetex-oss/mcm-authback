package net.litetex.authback.client.state;

import java.util.HashMap;
import java.util.Map;

import net.litetex.authback.client.keys.KeyState;


public class KeyStates
{
	private Map<String, KeyState> v1 = new HashMap<>();
	
	public Map<String, KeyState> getV1()
	{
		return this.v1;
	}
	
	public void setV1(final Map<String, KeyState> v1)
	{
		this.v1 = v1;
	}
}
