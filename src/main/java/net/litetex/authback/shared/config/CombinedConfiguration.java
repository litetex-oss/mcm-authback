package net.litetex.authback.shared.config;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import com.google.common.collect.ImmutableList;


class CombinedConfiguration implements Configuration
{
	private final List<Configuration> configurations;
	
	CombinedConfiguration(final Configuration... configurations)
	{
		this.configurations = ImmutableList.copyOf(configurations);
	}
	
	@Override
	public void load()
	{
		for(final Configuration configuration : this.configurations)
		{
			configuration.load();
		}
	}
	
	@Override
	public void save()
	{
		for(final Configuration configuration : this.configurations)
		{
			configuration.save();
		}
	}
	
	protected <T> Optional<T> get(final String path, final BiFunction<Configuration, String, Optional<T>> extractor)
	{
		return this.configurations.stream()
			.map(c -> extractor.apply(c, path))
			.filter(Optional::isPresent)
			.map(Optional::orElseThrow)
			.findFirst();
	}
	
	@Override
	public Optional<String> getString(final String path)
	{
		return this.get(path, Configuration::getString);
	}
	
	@Override
	public Optional<Boolean> getBoolean(final String path)
	{
		return this.get(path, Configuration::getBoolean);
	}
	
	@Override
	public Optional<Integer> getInteger(final String path)
	{
		return this.get(path, Configuration::getInteger);
	}
	
	@Override
	public Optional<List<String>> getStringList(final String path)
	{
		return this.get(path, Configuration::getStringList);
	}
	
	@Override
	public void setString(final String path, final String value)
	{
		for(final Configuration configuration : this.configurations)
		{
			configuration.setString(path, value);
		}
	}
	
	@Override
	public void setBoolean(final String path, final boolean value)
	{
		for(final Configuration configuration : this.configurations)
		{
			configuration.setBoolean(path, value);
		}
	}
	
	@Override
	public void setInteger(final String path, final int value)
	{
		for(final Configuration configuration : this.configurations)
		{
			configuration.setInteger(path, value);
		}
	}
	
	@Override
	public void setStringList(final String path, final List<String> value)
	{
		for(final Configuration configuration : this.configurations)
		{
			configuration.setStringList(path, value);
		}
	}
	
	@Override
	public boolean contains(final String path)
	{
		for(final Configuration configuration : this.configurations)
		{
			if(configuration.contains(path))
			{
				return true;
			}
		}
		return false;
	}
	
	@Override
	public void remove(final String path)
	{
		for(final Configuration configuration : this.configurations)
		{
			configuration.remove(path);
		}
	}
}
