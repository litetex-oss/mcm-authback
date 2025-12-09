package net.litetex.authback.server.command;

import java.security.PublicKey;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.litetex.authback.common.gameprofile.GameProfileCacheManager;
import net.litetex.authback.server.keys.ServerProfilePublicKeysManager;
import net.litetex.authback.shared.crypto.Ed25519KeyDecoder;
import net.litetex.authback.shared.external.org.apache.commons.codec.binary.Hex;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.permissions.PermissionProviderCheck;
import net.minecraft.server.players.NameAndId;


public class FallbackCommand
{
	private static final Logger LOG = LoggerFactory.getLogger(FallbackCommand.class);
	
	private static final DateTimeFormatter INSTANT_BASIC_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
		.withZone(ZoneId.systemDefault());
	
	private final Supplier<ServerProfilePublicKeysManager> serverProfilePublicKeysManagerSupplier;
	private final Supplier<GameProfileCacheManager> gameProfileCacheManagerSupplier;
	
	public FallbackCommand(
		final Supplier<ServerProfilePublicKeysManager> serverProfilePublicKeysManagerSupplier,
		final Supplier<GameProfileCacheManager> gameProfileCacheManagerSupplier)
	{
		this.serverProfilePublicKeysManagerSupplier = serverProfilePublicKeysManagerSupplier;
		this.gameProfileCacheManagerSupplier = gameProfileCacheManagerSupplier;
	}
	
	private ServerProfilePublicKeysManager serverProfilePublicKeysManager()
	{
		return this.serverProfilePublicKeysManagerSupplier.get();
	}
	
	private GameProfileCacheManager gameProfileCacheManager()
	{
		return this.gameProfileCacheManagerSupplier.get();
	}
	
	public void register(final CommandDispatcher<CommandSourceStack> dispatcher)
	{
		dispatcher.register(Commands.literal("authback")
			.then(Commands.literal("public_key")
				.then(this.registerAdd())
				.then(this.registerRemove())
				.then(this.registerList())
			)
		);
	}
	
	private LiteralArgumentBuilder<CommandSourceStack> registerAdd()
	{
		return Commands.literal("add")
			// Do not add "self" cmd here as it might lead to players accidentally back-dooring themselves
			.requires(permissionAdmin())
			.then(cmdId()
				.then(cmdArgId()
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
						this.gameProfileCacheManager().uuids()
							.stream()
							.map(UUID::toString),
						builder
					))
					.then(cmdArgPKH()
						.executes(ctx -> this.execAdd(
							ctx,
							resolveArgId(ctx),
							resolveArgPKH(ctx))))))
			.then(cmdName()
				.then(cmdArgName()
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
						this.gameProfileCacheManager().names(),
						builder
					))
					.then(cmdArgPKH()
						.executes(ctx -> this.execAdd(
							ctx,
							resolveArgName(ctx),
							resolveArgPKH(ctx))))));
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
		
		this.serverProfilePublicKeysManager().add(uuid, encodedKeyData, publicKey);
		final MutableComponent root = Component.empty()
			.append("Add public key for ")
			.append(this.renderPlayer(ctx, uuid));
		ctx.getSource().sendSuccess(() -> root, false);
		return 1;
	}
	
	private LiteralArgumentBuilder<CommandSourceStack> registerRemove()
	{
		return Commands.literal("remove")
			.then(cmdSelf()
				.then(cmdAll()
					.executes(this::execRemoveAllSelf))
				.then(cmdArgPKH()
					.executes(ctx -> this.execRemoveSelf(ctx, resolveArgPKH(ctx)))))
			.then(cmdId()
				.requires(permissionAdmin())
				.then(cmdArgId()
					.suggests(this.suggestExistingPublicKeyUserUUIDs())
					.then(cmdAll()
						.executes(ctx -> this.execRemoveAll(
							ctx,
							resolveArgId(ctx))))
					.then(cmdArgPKH()
						.executes(ctx -> this.execRemove(
							ctx,
							resolveArgId(ctx),
							resolveArgPKH(ctx))))))
			.then(cmdName()
				.requires(permissionAdmin())
				.then(cmdArgName()
					.suggests(this.suggestExistingPublicKeyUserNames())
					.then(cmdAll()
						.executes(ctx -> this.execRemoveAll(
							ctx,
							resolveArgName(ctx))))
					.then(cmdArgPKH()
						.executes(ctx -> this.execRemove(
							ctx,
							resolveArgName(ctx),
							resolveArgPKH(ctx))))));
	}
	
	private int execRemoveSelf(
		final CommandContext<CommandSourceStack> ctx,
		final String publicKeyHex
	) throws CommandSyntaxException
	{
		return this.execRemove(ctx, uuidFromCtx(ctx), publicKeyHex);
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
		if(this.serverProfilePublicKeysManager().remove(uuid, publicKeyHex))
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
	
	private int execRemoveAllSelf(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
	{
		return this.execRemoveAll(ctx, uuidFromCtx(ctx));
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
		final int keyCount = this.serverProfilePublicKeysManager().removeAll(uuid);
		final MutableComponent root = Component.empty()
			.append("Removed " + keyCount + " public key(s) from ")
			.append(this.renderPlayer(ctx, uuid));
		ctx.getSource().sendSuccess(() -> root, false);
		return 1;
	}
	
	// region Read
	
	private LiteralArgumentBuilder<CommandSourceStack> registerList()
	{
		return Commands.literal("list")
			.then(cmdSelf()
				.executes(this::execListSelf))
			.then(cmdAll()
				.requires(permissionAdmin())
				.executes(this::execListAll))
			.then(cmdId()
				.requires(permissionAdmin())
				.then(cmdArgId()
					.suggests(this.suggestExistingPublicKeyUserUUIDs())
					.executes(ctx -> this.execList(
						ctx,
						resolveArgId(ctx)))))
			.then(cmdName()
				.requires(permissionAdmin())
				.then(cmdArgName()
					.suggests(this.suggestExistingPublicKeyUserNames())
					.executes(ctx -> this.execList(
						ctx,
						resolveArgName(ctx)))));
	}
	
	private int execListSelf(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
	{
		return this.execList(ctx, uuidFromCtx(ctx));
	}
	
	private int execListAll(final CommandContext<CommandSourceStack> ctx)
	{
		return this.execListing(ctx, this.serverProfilePublicKeysManager().uuidPublicKeyHex());
	}
	
	private int execList(final CommandContext<CommandSourceStack> ctx, final String name)
	{
		return this.execForName(ctx, name, uuid -> this.execList(ctx, uuid));
	}
	
	private int execList(final CommandContext<CommandSourceStack> ctx, final UUID uuid)
	{
		return this.execListing(
			ctx,
			Optional.ofNullable(this.serverProfilePublicKeysManager().uuidPublicKeyHex().get(uuid))
				.map(val -> Map.of(uuid, val))
				.orElseGet(Map::of));
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
			this.serverProfilePublicKeysManager().profileUUIDs()
				.stream()
				.map(UUID::toString)
				.sorted(),
			builder
		);
	}
	
	private SuggestionProvider<CommandSourceStack> suggestExistingPublicKeyUserNames()
	{
		return (ctx, builder) -> SharedSuggestionProvider.suggest(
			
			this.serverProfilePublicKeysManager().profileUUIDs()
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
	
	private static UUID uuidFromCtx(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
	{
		return ctx.getSource().getPlayerOrException().nameAndId().id();
	}
	
	// endregion
	// region Commands
	
	private static LiteralArgumentBuilder<CommandSourceStack> cmdAll()
	{
		return Commands.literal("*");
	}
	
	private static LiteralArgumentBuilder<CommandSourceStack> cmdName()
	{
		return Commands.literal("name");
	}
	
	private static LiteralArgumentBuilder<CommandSourceStack> cmdId()
	{
		return Commands.literal("id");
	}
	
	private static LiteralArgumentBuilder<CommandSourceStack> cmdSelf()
	{
		return Commands.literal("self");
	}
	
	private static String resolveArgName(final CommandContext<CommandSourceStack> ctx)
	{
		return StringArgumentType.getString(ctx, "name");
	}
	
	private static RequiredArgumentBuilder<CommandSourceStack, String> cmdArgName()
	{
		return Commands.argument("name", StringArgumentType.word());
	}
	
	private static UUID resolveArgId(final CommandContext<CommandSourceStack> ctx)
	{
		return UuidArgument.getUuid(ctx, "id");
	}
	
	private static RequiredArgumentBuilder<CommandSourceStack, UUID> cmdArgId()
	{
		return Commands.argument("id", UuidArgument.uuid());
	}
	
	private static String resolveArgPKH(final CommandContext<CommandSourceStack> ctx)
	{
		return StringArgumentType.getString(ctx, "publicKeyHex");
	}
	
	private static RequiredArgumentBuilder<CommandSourceStack, String> cmdArgPKH()
	{
		return Commands.argument("publicKeyHex", StringArgumentType.word());
	}
	
	private static PermissionProviderCheck<CommandSourceStack> permissionAdmin()
	{
		return Commands.hasPermission(Commands.LEVEL_ADMINS);
	}
	
	// endregion
}
