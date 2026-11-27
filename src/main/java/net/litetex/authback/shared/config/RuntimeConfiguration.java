package net.litetex.authback.shared.config;

import java.util.List;
import java.util.Optional;


public abstract class RuntimeConfiguration implements Configuration
{
	public static RuntimeConfiguration environmentVariables(final String prefix)
	{
		return new RuntimeConfiguration()
		{
			@Override
			public Optional<String> getString(final String path)
			{
				return Optional.ofNullable(System.getenv(
					prefix + "_" + path.replace(".", "_").replace("-", "_").toUpperCase()
				));
			}
		};
	}
	
	public static RuntimeConfiguration systemProperties(final String prefix)
	{
		return new RuntimeConfiguration()
		{
			@Override
			public Optional<String> getString(final String path)
			{
				return Optional.ofNullable(System.getProperty(prefix + "." + path, null));
			}
		};
	}
	
	@Override
	public Optional<Boolean> getBoolean(final String path)
	{
		return this.getString(path)
			.filter(val -> !val.isEmpty())
			.map(val -> "1".equals(val) || Boolean.parseBoolean(val));
	}
	
	@Override
	public Optional<Integer> getInteger(final String path)
	{
		return this.getString(path)
			.map(val -> {
				try
				{
					return Integer.parseInt(val);
				}
				catch(NumberFormatException _)
				{
					return null;
				}
			});
	}
	
	@Override
	public Optional<List<String>> getStringList(final String path)
	{
		return this.getString(path)
			.filter(val -> !val.isEmpty())
			.map(val -> List.of(val.split(",")));
	}
	
	@Override
	public boolean contains(final String path)
	{
		return this.getString(path).isPresent();
	}
	
	@Override
	public void load()
	{
	}
	
	@Override
	public void save()
	{
	}
	
	@Override
	public void setString(final String path, final String value)
	{
	}
	
	@Override
	public void setBoolean(final String path, final boolean value)
	{
	}
	
	@Override
	public void setInteger(final String path, final int value)
	{
	}
	
	@Override
	public void setStringList(final String path, final List<String> value)
	{
	}
	
	@Override
	public void remove(final String path)
	{
	}
}
