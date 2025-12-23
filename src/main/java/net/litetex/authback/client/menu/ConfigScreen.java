package net.litetex.authback.client.menu;

import java.util.List;
import java.util.stream.Stream;

import net.litetex.authback.client.AuthBackClient;
import net.litetex.authback.client.config.AuthBackClientConfig;
import net.litetex.authback.shared.config.ConfigValueContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;


public class ConfigScreen extends OptionsSubScreen
{
	private final AuthBackClient abClient;
	
	public ConfigScreen(
		final Screen screen,
		final Options options)
	{
		super(screen, options, Component.literal("AuthBack"));
		this.abClient = AuthBackClient.instance();
	}
	
	@Override
	protected void addOptions()
	{
		this.addKeyManagementOptions();
		this.addAPIInteractionOptions();
	}
	
	private void addKeyManagementOptions()
	{
		this.addCategory(Component.literal("Key Management"));
		this.list.addSmall(
			Button.builder(
					Component.literal("Copy public key"),
					ignored -> {
						this.minecraft.keyboardHandler.setClipboard(this.abClient.currentPublicKeyHex());
						this.showToast(Component.literal("Copied public key to clipboard"), 2000L);
					})
				.build(),
			Button.builder(
					Component.literal("Regenerate keys"),
					ignored ->
						this.minecraft.setScreen(
							new ConfirmScreen(
								yes -> {
									if(yes)
									{
										this.abClient.regenerateKeys();
										this.showToast(Component.literal("Regenerated keys"), 4000L);
									}
									this.minecraft.setScreen(this);
								},
								Component.literal("Confirm Regeneration"),
								Component.literal(
									"""
										You should only regenerate your keys when your PRIVATE key got compromised.
										In such a case you should also remove the key from existing servers using \
										/authback public_keys remove self
										
										Also note that servers will only be notified about your changed keys when you \
										login to them again.
										
										Continue with regeneration?"""))))
				.build()
		);
	}
	
	private void addAPIInteractionOptions()
	{
		final AuthBackClientConfig config = this.abClient.config();
		
		this.addCategory(Component.literal("API Interaction"));
		Stream.of(
				new BooleanConfigData(
					config.blockRealmsFetching(),
					"Block/Delay fetching initial Realms data",
					"Blocks initial fetching of Realms news, notifications, ..."
				),
				new BooleanConfigData(
					config.blockFetchingProfileKeys(),
					"Block fetching profile/chat-signing keys",
					"NOTE: You will NOT be able to join servers that have "
						+ "enforce-secure-profile set to true when this is enabled"
				),
				new BooleanConfigData(
					config.blockAddressCheck(),
					"Disable server address check",
					"""
						Disables the central server blocklist lookup.
						Massively improves the performance when looking up literal IPs \
						as the required Reverse DNS lookup will no longer be executed."""
				),
				new BooleanConfigData(
					config.suppressAllServerJoinErrors(),
					"Suppress any joinServer error",
					"""
						Blocks all errors encountered when calling joinServer.
						This can work-around problems when the API is misbehaving and returning incorrect responses.
						
						WARNING: Allows joining servers with possibly invalid session data"""
				))
			.map(BooleanConfigData::createButton)
			.map(btn -> new BigEntry(this.list, btn))
			.forEach(this.list::addEntry);
	}
	
	private void addCategory(final Component category)
	{
		this.list.addEntry(new CategoryEntry(this.list, category));
	}
	
	private void showToast(final Component component, final long displayTime)
	{
		this.minecraft.getToastManager().addToast(new SystemToast(
			new SystemToast.SystemToastId(displayTime),
			component,
			null
		));
	}
	
	static class BigEntry extends OptionsList.Entry
	{
		protected final OptionsList list;
		protected final AbstractWidget widget;
		
		public BigEntry(
			final OptionsList list,
			final AbstractWidget widget)
		{
			super(List.of(), null);
			this.list = list;
			this.widget = widget;
		}
		
		@SuppressWarnings("checkstyle:MagicNumber")
		@Override
		public void renderContent(
			final GuiGraphics guiGraphics,
			final int i,
			final int j,
			final boolean bl,
			final float f)
		{
			this.widget.setWidth(this.list.getRowWidth());
			this.widget.setPosition(
				this.list.screen.width / 2 - 155,
				this.getContentY());
			this.widget.render(guiGraphics, i, j, f);
		}
		
		@Override
		public List<? extends GuiEventListener> children()
		{
			return List.of(this.widget);
		}
		
		@Override
		public List<? extends NarratableEntry> narratables()
		{
			return List.of(this.widget);
		}
	}
	
	
	// See also KeyBindsList#CategoryEntry
	static class CategoryEntry extends BigEntry
	{
		public CategoryEntry(final OptionsList list, final Component category)
		{
			super(list, new StringWidget(category, Minecraft.getInstance().font));
		}
		
		@Override
		public void renderContent(
			final GuiGraphics guiGraphics,
			final int i,
			final int j,
			final boolean bl,
			final float f)
		{
			this.widget.setPosition(
				this.list.getWidth() / 2 - this.widget.getWidth() / 2,
				this.getContentBottom() - 12);
			this.widget.render(guiGraphics, i, j, f);
		}
	}
	
	
	record BooleanConfigData(
		ConfigValueContainer<Boolean> container,
		String name,
		String tooltip)
	{
		CycleButton<Boolean> createButton()
		{
			return CycleButton.onOffBuilder(this.container.value())
				.withTooltip(ignored -> Tooltip.create(Component.literal(this.tooltip)))
				.create(
					Component.literal(this.name),
					(btn, value) ->
						this.container.set(value)
				);
		}
	}
}
