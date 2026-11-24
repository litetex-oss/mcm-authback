package net.litetex.authback.shared.config;

import java.util.List;
import java.util.Optional;

import net.fabricmc.loader.api.FabricLoader;


public interface Configuration
{
	static Configuration standard(final String variant)
	{
		return Configuration.combining(
			RuntimeConfiguration.environmentVariables("AUTHBACK"),
			RuntimeConfiguration.environmentVariables("AUTHBACK_" + variant.toUpperCase()),
			RuntimeConfiguration.systemProperties("authback"),
			RuntimeConfiguration.systemProperties("authback." + variant),
			new FileConfiguration(FabricLoader.getInstance().getConfigDir().resolve("authback-" + variant + ".json")));
	}
	
	static Configuration combining(final Configuration... configurations)
	{
		return new CombinedConfiguration(configurations);
	}
	
	void load();
	
	void save();
	
	Optional<String> getString(String path);
	
	Optional<Boolean> getBoolean(String path);
	
	Optional<Integer> getInteger(String path);
	
	Optional<List<String>> getStringList(String path);
	
	void setString(String path, String value);
	
	void setBoolean(String path, boolean value);
	
	void setInteger(String path, int value);
	
	void setStringList(String path, List<String> value);
	
	boolean contains(String path);
	
	void remove(String path);
}
