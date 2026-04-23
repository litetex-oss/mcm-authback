package net.litetex.authback.mixin.client;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.platform.NativeImage;

import net.litetex.authback.client.AuthBackClient;
import net.litetex.authback.shared.mixin.log.MixinLogger;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;


@Mixin(SkinTextureDownloader.class)
public abstract class SkinTextureDownloaderMixin
{
	@Unique
	private static final Logger LOG = MixinLogger.client("SkinTextureDownloaderMixin");
	
	@WrapMethod(method = "downloadSkin")
	NativeImage downloadSkin(final Path localCopy, final String url, final Operation<NativeImage> original)
	{
		return original.call(
			localCopy,
			AuthBackClient.instance().config().forceSecureSkinDownload().value()
				? this.secureUrlIfRequired(url)
				: url);
	}
	
	@Unique
	@SuppressWarnings("checkstyle:MagicNumber")
	private String secureUrlIfRequired(final String url)
	{
		if(url.length() < 8)
		{
			return url;
		}
		
		// Check if url starts with http://
		if(!"http://".equalsIgnoreCase(url.substring(0, 7)))
		{
			return url;
		}
		
		final String secureUrl = "https://" + url.substring(7);
		LOG.debug("Secured skin download url: {}", secureUrl);
		return secureUrl;
	}
}
