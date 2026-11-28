package net.litetex.authback.common.access;

import java.net.Proxy;

import com.mojang.authlib.services.MinecraftServicesDiscoveryService;
import com.mojang.authlib.services.MinecraftServicesSessionService;
import com.mojang.authlib.services.ServicesKeySet;


// Required because underlying constructor is protected and can't be accessed by AW
public class MinecraftServicesSessionServiceExt extends MinecraftServicesSessionService
{
	@SuppressWarnings("checkstyle:IllegalIdentifierName")
	public MinecraftServicesSessionServiceExt(
		final ServicesKeySet servicesKeySet,
		final Proxy proxy,
		final MinecraftServicesDiscoveryService discoveryService)
	{
		super(servicesKeySet, proxy, discoveryService);
	}
}
