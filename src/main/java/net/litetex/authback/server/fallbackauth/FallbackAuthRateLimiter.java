package net.litetex.authback.server.fallbackauth;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.RateLimiter;

import net.litetex.authback.shared.collections.MaxSizedHashMap;
import net.litetex.authback.shared.config.Configuration;


public class FallbackAuthRateLimiter
{
	private static final Logger LOG = LoggerFactory.getLogger(FallbackAuthRateLimiter.class);
	
	private final Map<RateLimitKey, RateLimiter> rateLimiters;
	private final Supplier<RateLimiter> newRateLimiterSupplier;
	
	private final boolean ignoreLocalAddresses;
	private final int ipv6NetworkPrefixBytes;
	
	FallbackAuthRateLimiter(
		final int requestPerMinutePerIP,
		final int bucketSize,
		final boolean ignoreLocalAddresses,
		final int ipv6NetworkPrefixBytes)
	{
		this.rateLimiters = Collections.synchronizedMap(new MaxSizedHashMap<>(bucketSize));
		this.newRateLimiterSupplier = () ->
			// Note that the rate limiter is constant
			RateLimiter.create(requestPerMinutePerIP / 60.0);
		
		this.ignoreLocalAddresses = ignoreLocalAddresses;
		if(ipv6NetworkPrefixBytes < 0 || ipv6NetworkPrefixBytes > 16)
		{
			throw new IllegalArgumentException(
				"ipv6NetworkPrefixBytes=" + ipv6NetworkPrefixBytes + " out of bound[min=0,max=16]");
		}
		this.ipv6NetworkPrefixBytes = ipv6NetworkPrefixBytes;
	}
	
	@SuppressWarnings("checkstyle:MagicNumber")
	@Nullable
	public static FallbackAuthRateLimiter create(final Configuration config)
	{
		final String prefix = "fallback-auth.rate-limit.";
		
		final int requestsPerMinutePerIP =
			config.getInteger(prefix + "requests-per-ip-per-minute", 20); // every 3s
		if(requestsPerMinutePerIP <= 0)
		{
			return null;
		}
		
		return new FallbackAuthRateLimiter(
			requestsPerMinutePerIP,
			config.getInteger(prefix + "bucket-size", 1000),
			config.getBoolean(prefix + "ignore-private-addresses", true),
			config.getInteger(prefix + "ipv6-network-prefix-bytes", 8));
	}
	
	public boolean isAddressRateLimited(final InetAddress address)
	{
		if(this.ignoreLocalAddresses
			&& (address.isLoopbackAddress() // e.g. 127.0.0.1
			|| address.isLinkLocalAddress() // e.g. fe80:...
			|| address.isSiteLocalAddress())) // e.g. 192.168...
		{
			LOG.debug("Will not rate limit local address: {}", address);
			return false;
		}
		
		return !this.rateLimiters.computeIfAbsent(
				RateLimitKey.create(address, this.ipv6NetworkPrefixBytes),
				ignored -> this.newRateLimiterSupplier.get())
			.tryAcquire();
	}
	
	record RateLimitKey(
		IPVersion version,
		byte[] addressBytes
	)
	{
		@SuppressWarnings("checkstyle:MagicNumber")
		public static RateLimitKey create(final InetAddress address, final int ipv6NetworkPrefixBytes)
		{
			if(address instanceof final Inet6Address inet6Address)
			{
				return new RateLimitKey(
					IPVersion.V6,
					Arrays.copyOfRange(inet6Address.getAddress(), 0, ipv6NetworkPrefixBytes));
			}
			
			return new RateLimitKey(
				IPVersion.V4,
				address.getAddress());
		}
		
		@Override
		public boolean equals(final Object o)
		{
			if(!(o instanceof RateLimitKey(
				final IPVersion otherVersion,
				final byte[] otherAddressBytes
			)))
			{
				return false;
			}
			
			return this.version() == otherVersion
				&& Arrays.equals(this.addressBytes(), otherAddressBytes);
		}
		
		@Override
		public int hashCode()
		{
			return 31 * Objects.hashCode(this.version())
				+ Arrays.hashCode(this.addressBytes());
		}
	}
	
	
	enum IPVersion
	{
		V4,
		V6
	}
}
