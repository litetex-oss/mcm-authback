package net.litetex.authback.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.litetex.authback.client.AuthBackClient;
import net.litetex.authback.shared.mixin.log.MixinLogger;
import net.minecraft.client.multiplayer.resolver.AddressCheck;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;


@Mixin(AddressCheck.class)
public interface AddressCheckMixin
{
	// There are major problems with the address check - Why is this even in use?
	// What problems?
	// 1. 99% of the entries are either
	// a) not been in use for a decade
	// (e.g. dc2c735b3e6aba51ece294d7de21b947379aac4d - is present since 2016!)
	// b) DynDNS entries where someone keeps increasing the number
	// See also https://raw.githubusercontent.com/sudofox/mojang-blocklist/refs/heads/master/data/merged.txt
	//
	// 2. The same trash code is also responsible for lagging out resolution of literal IP based servers!
	// It calls InetAddress#getHostName which ALWAYS causes a reverse DNS lookup (PTR) when a literal IP is used
	// Mods like "Fast IP Ping" work around this problem by correctly creating InetAddress
	
	@Inject(
		method = "createFromService",
		at = @At("HEAD"),
		cancellable = true)
	private static void supplyDummy(final CallbackInfoReturnable<AddressCheck> cir)
	{
		if(AuthBackClient.instance().config().blockAddressCheck().value())
		{
			MixinLogger.client("AddressCheckMixin").debug("Blocking address check");
			cir.setReturnValue(new AddressCheck()
			{
				@Override
				public boolean isAllowed(final ResolvedServerAddress resolvedServerAddress)
				{
					return true;
				}
				
				@Override
				public boolean isAllowed(final ServerAddress serverAddress)
				{
					return true;
				}
			});
		}
	}
}
