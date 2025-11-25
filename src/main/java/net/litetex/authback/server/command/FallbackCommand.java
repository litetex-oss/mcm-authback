package net.litetex.authback.server.command;

import java.security.PublicKey;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.litetex.authback.common.gameprofile.GameProfileCacheManager;
import net.litetex.authback.server.keys.ServerProfilePublicKeysManager;
import net.litetex.authback.shared.crypto.Ed25519KeyDecoder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.players.NameAndId;


public class FallbackCommand
{
	private static final Logger LOG = LoggerFactory.getLogger(FallbackCommand.class);
	
	private static final DateTimeFormatter INSTANT_BASIC_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
		.withZone(ZoneId.systemDefault());
	
	private static final int REQUIRED_PERMISSION = 3; // 3 -> Admin
	
	private final ServerProfilePublicKeysManager serverProfilePublicKeysManager;
	private final GameProfileCacheManager gameProfileCacheManager;
	
	public FallbackCommand(
		final ServerProfilePublicKeysManager serverProfilePublicKeysManager,
		final GameProfileCacheManager gameProfileCacheManager)
	{
		this.serverProfilePublicKeysManager = serverProfilePublicKeysManager;
		this.gameProfileCacheManager = gameProfileCacheManager;
	}
	
	public void register(final CommandDispatcher<CommandSourceStack> dispatcher)
	{
		dispatcher.register(Commands.literal("authback")
			.requires(src -> src.hasPermission(REQUIRED_PERMISSION))
			.then(Commands.literal("public_key")
				.then(this.registerAdd())
				.then(this.registerRemove())
				.then(this.registerGet())
				.then(this.registerList())
			)
		);
	}
	
	private LiteralArgumentBuilder<CommandSourceStack> registerAdd()
	{
		return Commands.literal("add")
			.then(Commands.literal("id")
				.then(Commands.argument("id", UuidArgument.uuid())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
						this.gameProfileCacheManager.uuids()
							.stream()
							.map(UUID::toString),
						builder
					))
					.then(Commands.argument("publicKeyHex", StringArgumentType.word())
						.executes(ctx -> this.execAdd(
							ctx,
							UuidArgument.getUuid(ctx, "id"),
							StringArgumentType.getString(ctx, "publicKeyHex"))))))
			.then(Commands.literal("name")
				.then(Commands.argument("name", StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
						this.gameProfileCacheManager.names(),
						builder
					))
					.then(Commands.argument("publicKeyHex", StringArgumentType.word())
						.executes(ctx -> this.execAdd(
							ctx,
							StringArgumentType.getString(ctx, "name"),
							StringArgumentType.getString(ctx, "publicKeyHex"))))));
	}
	
	private int execAdd(
		final CommandContext<CommandSourceStack> ctx,
		final String name,
		final String publicKeyHex)
	{
		return this.execForName(ctx, name, uuid -> this.execAdd(ctx, uuid, publicKeyHex));
	}
	
	private int execAdd(
		final CommandContext<CommandSourceStack> ctx,
		final UUID uuid,
		final String publicKeyHex)
	{
		final byte[] encodedKeyData;
		final PublicKey publicKey;
		try
		{
			encodedKeyData = Hex.decodeHex(publicKeyHex);
			publicKey = new Ed25519KeyDecoder().decodePublic(encodedKeyData);
		}
		catch(final Exception ex)
		{
			ctx.getSource().sendFailure(Component.literal("Failed to decode public key: " + ex.getMessage()));
			LOG.debug("Failed to decode public key", ex);
			return 0;
		}
		
		this.serverProfilePublicKeysManager.add(uuid, encodedKeyData, publicKey);
		final MutableComponent root = Component.empty()
			.append("Add public key for ")
			.append(this.renderPlayer(ctx, uuid));
		ctx.getSource().sendSuccess(() -> root, false);
		return 1;
	}
	
	private LiteralArgumentBuilder<CommandSourceStack> registerRemove()
	{
		return Commands.literal("remove")
			.then(Commands.literal("id")
				.then(Commands.argument("id", UuidArgument.uuid())
					.suggests(this.suggestExistingPublicKeyUserUUIDs())
					.then(Commands.literal("*")
						.executes(ctx -> this.execRemoveAll(
							ctx,
							UuidArgument.getUuid(ctx, "id"))))
					.then(Commands.argument("publicKeyHex", StringArgumentType.word())
						.executes(ctx -> this.execRemove(
							ctx,
							UuidArgument.getUuid(ctx, "id"),
							StringArgumentType.getString(ctx, "publicKeyHex"))))))
			.then(Commands.literal("name")
				.then(Commands.argument("name", StringArgumentType.word())
					.suggests(this.suggestExistingPublicKeyUserNames())
					.then(Commands.literal("*")
						.executes(ctx -> this.execRemoveAll(
							ctx,
							StringArgumentType.getString(ctx, "name"))))
					.then(Commands.argument("publicKeyHex", StringArgumentType.word())
						.executes(ctx -> this.execRemove(
							ctx,
							StringArgumentType.getString(ctx, "name"),
							StringArgumentType.getString(ctx, "publicKeyHex"))))));
	}
	
	private int execRemove(
		final CommandContext<CommandSourceStack> ctx,
		final String name,
		final String publicKeyHex)
	{
		return this.execForName(ctx, name, uuid -> this.execRemove(ctx, uuid, publicKeyHex));
	}
	
	private int execRemove(
		final CommandContext<CommandSourceStack> ctx,
		final UUID uuid,
		final String publicKeyHex)
	{
		if(this.serverProfilePublicKeysManager.remove(uuid, publicKeyHex))
		{
			final MutableComponent root = Component.empty()
				.append("Removed public key from ")
				.append(this.renderPlayer(ctx, uuid));
			ctx.getSource().sendSuccess(() -> root, false);
			return 1;
		}
		
		ctx.getSource().sendFailure(Component.empty()
			.append("Failed to find public key for ")
			.append(this.renderPlayer(ctx, uuid)));
		return 0;
	}
	
	private int execRemoveAll(
		final CommandContext<CommandSourceStack> ctx,
		final String name)
	{
		return this.execForName(ctx, name, uuid -> this.execRemoveAll(ctx, uuid));
	}
	
	private int execRemoveAll(
		final CommandContext<CommandSourceStack> ctx,
		final UUID uuid)
	{
		final int keyCount = this.serverProfilePublicKeysManager.removeAll(uuid);
		final MutableComponent root = Component.empty()
			.append("Removed " + keyCount + " public key(s) from ")
			.append(this.renderPlayer(ctx, uuid));
		ctx.getSource().sendSuccess(() -> root, false);
		return 1;
	}
	
	// region Read
	
	private LiteralArgumentBuilder<CommandSourceStack> registerGet()
	{
		return Commands.literal("get")
			.then(Commands.literal("id")
				.then(Commands.argument("id", UuidArgument.uuid())
					.suggests(this.suggestExistingPublicKeyUserUUIDs())
					.executes(ctx -> this.execGet(
						ctx,
						UuidArgument.getUuid(ctx, "id")))))
			.then(Commands.literal("name")
				.then(Commands.argument("name", StringArgumentType.word())
					.suggests(this.suggestExistingPublicKeyUserNames())
					.executes(ctx -> this.execGet(
						ctx,
						StringArgumentType.getString(ctx, "name")))));
	}
	
	private int execGet(final CommandContext<CommandSourceStack> ctx, final String name)
	{
		return this.execForName(ctx, name, uuid -> this.execGet(ctx, uuid));
	}
	
	private int execGet(final CommandContext<CommandSourceStack> ctx, final UUID uuid)
	{
		return this.execListing(
			ctx,
			Optional.ofNullable(this.serverProfilePublicKeysManager.uuidPublicKeyHex().get(uuid))
				.map(val -> Map.of(uuid, val))
				.orElseGet(Map::of));
	}
	
	private LiteralArgumentBuilder<CommandSourceStack> registerList()
	{
		return Commands.literal("list")
			.executes(this::execList);
	}
	
	private int execList(final CommandContext<CommandSourceStack> ctx)
	{
		return this.execListing(ctx, this.serverProfilePublicKeysManager.uuidPublicKeyHex());
	}
	
	private int execListing(
		final CommandContext<CommandSourceStack> ctx,
		final Map<UUID, List<ServerProfilePublicKeysManager.PublicKeyInfo>> uuidPublicKeysHex)
	{
		final MutableComponent root = Component.empty()
			.append(Component.literal("Listing "
					+ uuidPublicKeysHex.size()
					+ " player(s) with "
					+ uuidPublicKeysHex.values().stream().mapToInt(List::size).sum()
					+ " public key(s)")
				.withStyle(style -> style.withItalic(true)
					.withColor(ChatFormatting.GRAY)));
		
		uuidPublicKeysHex
			.entrySet()
			.stream()
			.flatMap(e -> Stream.concat(
				Stream.of(this.renderPlayer(ctx, e.getKey())),
				e.getValue().stream()
					.map(pki -> Stream.of(
							Component.literal("- "),
							Component.literal(pki.hex().substring(0, 16) + "...")
								.withStyle(style -> style.withItalic(true)
									.withClickEvent(new ClickEvent.CopyToClipboard(pki.hex()))
									.withHoverEvent(new HoverEvent.ShowText(Component.literal(pki.hex())))
								),
							Component.literal(" "),
							Component.literal(INSTANT_BASIC_DATE_TIME.format(pki.lastUse()))
								.withStyle(style -> style
									.withClickEvent(new ClickEvent.CopyToClipboard(pki.lastUse().toString()))
									.withHoverEvent(new HoverEvent.ShowText(Component.literal("Last used: "
										+ pki.lastUse())))
								))
						.reduce(Component.empty(), MutableComponent::append)
					)))
			.forEach(c -> root.append("\n").append(c));
		
		ctx.getSource().sendSuccess(() -> root, false);
		return 1;
	}
	
	// endregion
	// region Helper
	
	private Component renderPlayer(final CommandContext<CommandSourceStack> ctx, final UUID uuid)
	{
		return Component.literal(this.associatedNameOrUUID(ctx, uuid))
			.withStyle(style -> style
				.withClickEvent(new ClickEvent.CopyToClipboard(uuid.toString()))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(uuid.toString()))));
	}
	
	private String associatedNameOrUUID(final CommandContext<CommandSourceStack> ctx, final UUID uuid)
	{
		return this.findNameForUUID(ctx, uuid)
			.orElseGet(uuid::toString);
	}
	
	private int execForName(
		final CommandContext<CommandSourceStack> ctx,
		final String name,
		final Function<UUID, Integer> func)
	{
		return this.findUUIDByName(ctx, name)
			.map(func)
			.orElseGet(() -> {
				ctx.getSource().sendFailure(Component.literal("Failed to resolve player for name"));
				return 0;
			});
	}
	
	private SuggestionProvider<CommandSourceStack> suggestExistingPublicKeyUserUUIDs()
	{
		return (ctx, builder) -> SharedSuggestionProvider.suggest(
			this.serverProfilePublicKeysManager.profileUUIDs()
				.stream()
				.map(UUID::toString)
				.sorted(),
			builder
		);
	}
	
	private SuggestionProvider<CommandSourceStack> suggestExistingPublicKeyUserNames()
	{
		return (ctx, builder) -> SharedSuggestionProvider.suggest(
			
			this.serverProfilePublicKeysManager.profileUUIDs()
				.stream()
				.map(uuid -> this.findNameForUUID(ctx, uuid))
				.filter(Optional::isPresent)
				.map(Optional::get)
				.sorted(),
			builder
		);
	}
	
	private Optional<String> findNameForUUID(final CommandContext<CommandSourceStack> ctx, final UUID uuid)
	{
		return ctx.getSource().getServer().services().nameToIdCache().get(uuid)
			.map(NameAndId::name);
	}
	
	private Optional<UUID> findUUIDByName(final CommandContext<CommandSourceStack> ctx, final String name)
	{
		return ctx.getSource().getServer().services().nameToIdCache().get(name)
			.map(NameAndId::id);
	}
	
	// endregion
}
