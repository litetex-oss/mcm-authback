package net.litetex.authback.mixin.client.lan_server_detect;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.litetex.authback.shared.mixin.log.MixinLogger;
import net.minecraft.client.server.LanServerDetection;


@Mixin(LanServerDetection.LanServerDetector.class)
public abstract class LANServerDetectorFixIPv6PreferredMixin
{
	@Unique
	private static final Logger LOG = MixinLogger.client("LANServerDetectorFixIPv6PreferredMixin");
	
	@Unique
	private InetAddress ipv4AnyLocalAddressForInit;
	
	@Unique
	private InetSocketAddress joinedSocketAddr;
	@Unique
	private NetworkInterface joinedNetworkInterface;
	
	@WrapOperation(
		method = "<init>",
		at = @At(
			value = "NEW",
			target = "(I)Ljava/net/MulticastSocket;"))
	MulticastSocket newMultiCastSocket(final int port, final Operation<MulticastSocket> original)
		throws IOException
	{
		if(isIPv4Default() || !reflectiveNetworkInterfaceCreationAvailable())
		{
			return original.call(port);
		}
		
		LOG.debug("IPv4 is not the default IP stack, applying workaround to prevent LANServerDetector crash");
		try
		{
			this.ipv4AnyLocalAddressForInit = ipv4AnyLocalAddress();
			return new MulticastSocket(new InetSocketAddress(this.ipv4AnyLocalAddressForInit, port));
		}
		catch(final UnknownHostException e)
		{
			LOG.warn("Workaround for non-default IPv4 stack failed, calling original");
			return original.call(port);
		}
	}
	
	@WrapOperation(
		method = "<init>",
		at = @At(
			value = "INVOKE",
			target = "Ljava/net/MulticastSocket;joinGroup(Ljava/net/InetAddress;)V"))
	void joinGroup(final MulticastSocket instance, final InetAddress mcastaddr, final Operation<Void> original)
		throws IOException
	{
		if(this.ipv4AnyLocalAddressForInit == null)
		{
			original.call(instance, mcastaddr);
			return;
		}
		
		this.joinedSocketAddr = new InetSocketAddress(mcastaddr, 0);
		
		// DatagramSocketAdaptor#anyNetworkInterface
		final InetAddress[] addrs = new InetAddress[1];
		addrs[0] = this.ipv4AnyLocalAddressForInit;
		this.joinedNetworkInterface = this.networkInterfaces.newNetworkInterface(addrs[0].getHostName(), 0, addrs);
		
		instance.joinGroup(this.joinedSocketAddr, this.joinedNetworkInterface);
		this.ipv4AnyLocalAddressForInit = null;
	}
	
	@WrapOperation(
		method = "run",
		at = @At(
			value = "INVOKE",
			target = "Ljava/net/MulticastSocket;leaveGroup(Ljava/net/InetAddress;)V"))
	void leaveGroup(final MulticastSocket instance, final InetAddress mcastaddr, final Operation<Void> original)
		throws IOException
	{
		if(this.joinedNetworkInterface == null)
		{
			original.call(instance, mcastaddr);
		}
		
		instance.leaveGroup(this.joinedSocketAddr, this.joinedNetworkInterface);
		
		this.joinedSocketAddr = null;
		this.joinedNetworkInterface = null;
	}
	
	@Unique
	private static boolean triedCreateNetworkInterfaces;
	@Unique
	private static NetworkInterfaces networkInterfaces;
	
	@Unique
	private static boolean reflectiveNetworkInterfaceCreationAvailable()
	{
		if(!triedCreateNetworkInterfaces)
		{
			triedCreateNetworkInterfaces = true;
			try
			{
				networkInterfaces = new NetworkInterfaces();
			}
			catch(final Exception ex)
			{
				LOG.warn("Failed to access/create NetworkInterface. "
					+ "Ensure that you the following program launch arguments: "
					+ "--add-opens java.base/java.net=ALL-UNNAMED\n"
					+ "LAN Server will crash otherwise");
			}
		}
		return networkInterfaces != null;
	}
	
	@Unique
	private static Boolean isIPv4Default;
	
	@Unique
	private static boolean isIPv4Default()
	{
		if(isIPv4Default == null)
		{
			// InetAddress.anyLocalAddress is not directly accessible -> extract via InetSocketAddress
			isIPv4Default = new InetSocketAddress(0).getAddress() instanceof Inet4Address;
		}
		return isIPv4Default;
	}
	
	@Unique
	private static InetAddress ipv4AnyLocalAddress;
	
	@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
	@Unique
	private static InetAddress ipv4AnyLocalAddress() throws UnknownHostException
	{
		if(ipv4AnyLocalAddress == null)
		{
			// Would be a lot easier if we could simply write Inet4Address.anyLocalAddress()
			ipv4AnyLocalAddress =
				Inet4Address.getByAddress("0.0.0.0", new byte[]{0x00, 0x00, 0x00, 0x00});
		}
		return ipv4AnyLocalAddress;
	}
	
	// From DatagramSocketAdaptor
	// NOTE: I'm not sure why the JDK requires reflection internally.
	// Why not make this public in the first place?!?
	private static class NetworkInterfaces
	{
		private final Constructor<NetworkInterface> ctr;
		
		NetworkInterfaces() throws Exception
		{
			this.ctr = NetworkInterface.class.getDeclaredConstructor(String.class, int.class, InetAddress[].class);
			this.ctr.setAccessible(true);
		}
		
		/**
		 * Creates a NetworkInterface with the given name index and addresses.
		 */
		NetworkInterface newNetworkInterface(final String name, final int index, final InetAddress[] addrs)
		{
			try
			{
				return this.ctr.newInstance(name, index, addrs);
			}
			catch(final InstantiationException | IllegalAccessException | InvocationTargetException e)
			{
				throw new RuntimeException(e);
			}
		}
	}
}
