package net.litetex.authback.shared.config;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

import org.apache.commons.lang3.function.TriConsumer;


public class ConfigValueContainer<T>
{
	private final Configuration config;
	
	private final String path;
	private final T defaultValue;
	private final TriConsumer<Configuration, String, T> setIntoConfig;
	
	private T value;
	
	public ConfigValueContainer(
		final Configuration config,
		final String path,
		final T defaultValue,
		final BiFunction<Configuration, String, Optional<T>> getFromConfig,
		final TriConsumer<Configuration, String, T> setIntoConfig)
	{
		this.config = config;
		this.path = path;
		this.defaultValue = defaultValue;
		this.setIntoConfig = setIntoConfig;
		
		this.value = getFromConfig.apply(config, path).orElse(defaultValue);
	}
	
	public static ConfigValueContainer<Boolean> bool(
		final Configuration config,
		final String path,
		final boolean defaultValue)
	{
		return new ConfigValueContainer<>(
			config,
			path,
			defaultValue,
			Configuration::getBoolean,
			Configuration::setBoolean);
	}
	
	public T value()
	{
		return this.value;
	}
	
	public void setWithoutSave(final T value)
	{
		this.value = value;
		
		if(Objects.equals(this.defaultValue, value))
		{
			this.config.remove(this.path);
		}
		else
		{
			this.setIntoConfig.accept(this.config, this.path, value);
		}
	}
	
	public void set(final T value)
	{
		this.setWithoutSave(value);
		this.config.save();
	}
}
