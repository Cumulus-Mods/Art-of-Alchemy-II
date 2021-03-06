package com.cumulusmc.artofalchemy.item;

import com.cumulusmc.artofalchemy.AoAConfig;
import com.cumulusmc.artofalchemy.essentia.Essentia;
import com.cumulusmc.artofalchemy.essentia.EssentiaContainer;
import com.cumulusmc.artofalchemy.transport.HasEssentia;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.util.NbtType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.List;

public class ItemEssentiaVessel extends Item {

	public final int capacity;
	public final Essentia type;
	private String translationKey;

	public ItemEssentiaVessel(Settings settings, Essentia type) {
		super(settings.maxCount(1));
		capacity = AoAConfig.get().vesselCapacity;
		this.type = type;
	}

	public ItemEssentiaVessel(Settings settings) {
		this(settings, null);
	}

	public static EssentiaContainer getContainer(ItemStack stack) {
		EssentiaContainer container = EssentiaContainer.of(stack);
		Essentia type = null;
		int capacity = 0;
		if (stack.getItem() instanceof ItemEssentiaVessel) {
			type = ((ItemEssentiaVessel) stack.getItem()).type;
			capacity = ((ItemEssentiaVessel) stack.getItem()).capacity;
		}
		if (container == null) {
			container = new EssentiaContainer().setCapacity(capacity);
			if (type != null) {
				container.whitelist(type).setWhitelistEnabled(true);
			}
		}
		return container;
	}

	public void setContainer(ItemStack stack, EssentiaContainer container) {
		if (type != null) {
			container.setWhitelist(new HashSet<>()).whitelist(type).setWhitelistEnabled(true);
		}
		container.in(stack);
	}

	@Override
	public void onCraft(ItemStack stack, World world, PlayerEntity player) {
		EssentiaContainer container = getContainer(stack);
		setContainer(stack, container);
		super.onCraft(stack, world, player);
	}

	public static int useStackOnBE(ItemStack stack, BlockEntity be) {
		EssentiaContainer container = getContainer(stack);
		int transferred = 0;

		if (be instanceof HasEssentia) {
			HasEssentia target = (HasEssentia) be;
			for (int i = 0; i < target.getNumContainers() && transferred == 0; i++) {
				EssentiaContainer other = target.getContainer(i);
				int pushed = container.pushContents(other).getCount();
				transferred -= pushed;
			}
			for (int i = 0; i < target.getNumContainers() && transferred == 0; i++) {
				EssentiaContainer other = target.getContainer(i);
				int pulled = container.pullContents(other, container.isInput()).getCount();
				transferred += pulled;
			}
			BlockPos pos = be.getPos();
			if (transferred > 0) {
				be.getWorld().playSound(null, pos, SoundEvents.ITEM_BUCKET_FILL, SoundCategory.BLOCKS, 1.0F, 1.0F);
			} else if (transferred < 0) {
				be.getWorld().playSound(null, pos, SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.BLOCKS, 1.0F, 1.0F);
			}
		}

		container.in(stack);
		return transferred;
	}

	public static int getColor(ItemStack stack) {
		NbtCompound tag = stack.getOrCreateNbt();
		if (tag.contains("color", NbtType.INT)) {
			return tag.getInt("color");
		} else {
			return 0xAA0077;
		}
	}

	public static void setColor(ItemStack stack, int color) {
		NbtCompound tag = stack.getOrCreateNbt();
		tag.putInt("color", color);
	}

	public static void setColor(ItemStack stack) {
		NbtCompound tag = stack.getOrCreateNbt();
		tag.putInt("color", getContainer(stack).getColor());
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext ctx) {
		PlayerEntity player = ctx.getPlayer();
		BlockEntity be = ctx.getWorld().getBlockEntity(ctx.getBlockPos());
		int transferred = useStackOnBE(ctx.getStack(), be);

		if (player != null) {
			if (transferred > 0) {
				player.sendMessage(new TranslatableText(tooltipPrefix() + "pulled", +transferred), true);
			} else if (transferred < 0) {
				player.sendMessage(new TranslatableText(tooltipPrefix() + "pushed", -transferred), true);
			}
		}

		if (transferred != 0) {
			be.markDirty();
			setColor(ctx.getStack());
			return ActionResult.SUCCESS;
		} else {
			return ActionResult.PASS;
		}
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		if (user.isSneaking()) {
			ItemStack stack = user.getStackInHand(hand);
			EssentiaContainer container = getContainer(stack);
			float pitch;
			if (container.isInput() && container.isOutput()) {
				user.sendMessage(new TranslatableText(tooltipPrefix() + "input"), true);
				container.setInput(true);
				container.setOutput(false);
				pitch = 0.80f;
			} else if (container.isInput() && !container.isOutput()) {
				user.sendMessage(new TranslatableText(tooltipPrefix() + "output"), true);
				container.setInput(false);
				container.setOutput(true);
				pitch = 0.95f;
			} else if (!container.isInput() && container.isOutput()){
				user.sendMessage(new TranslatableText(tooltipPrefix() + "locked"), true);
				container.setInput(false);
				container.setOutput(false);
				pitch = 1.05f;
			} else {
				user.sendMessage(new TranslatableText(tooltipPrefix() + "unlocked"), true);
				container.setInput(true);
				container.setOutput(true);
				pitch = 0.65f;
			}
			container.in(stack);
			if(world.isClient) {
				user.playSound(SoundEvents.UI_BUTTON_CLICK, SoundCategory.PLAYERS, 0.5f, pitch);
			}
			return TypedActionResult.consume(stack);
		}
		return super.use(world, user, hand);
	}

	@Environment(EnvType.CLIENT)
	@Override
	public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext ctx) {

		if (world == null) {
			return;
		}

		EssentiaContainer container = getContainer(stack);
		String prefix = tooltipPrefix();

		if (((ItemEssentiaVessel) stack.getItem()).type != null) {
			tooltip.add(new TranslatableText(prefix + "deprecated").formatted(Formatting.DARK_RED));
		}

		if (container.isInfinite()) {
			tooltip.add(new TranslatableText(prefix + "infinite").formatted(Formatting.LIGHT_PURPLE));
			if (container.isWhitelistEnabled()) {
				if (container.getWhitelist().isEmpty()) {
					tooltip.add(new TranslatableText(prefix + "empty").formatted(Formatting.GRAY));
				} else {
					for (Essentia essentia : container.getWhitelist()) {
						tooltip.add(new TranslatableText(prefix + "component_inf",
							essentia.getName()).formatted(Formatting.GOLD));
					}
				}
			}

		} else if (container.hasUnlimitedCapacity()){
			if (container.isWhitelistEnabled() && container.getWhitelist().size() == 1) {
				for (Essentia essentia : container.getWhitelist()) {
					tooltip.add(new TranslatableText(prefix + "single_unlim",
						essentia.getName(), container.getCount(essentia)).formatted(Formatting.GREEN));
				}
			} else if (container.isWhitelistEnabled() && container.getWhitelist().isEmpty()) {
				tooltip.add(new TranslatableText(prefix + "empty").formatted(Formatting.GRAY));
			} else {
				tooltip.add(new TranslatableText(prefix + "mixed_unlim", container.getCount()).formatted(Formatting.AQUA));
				for (Essentia essentia : container.getContents().sortedList()) {
					if (container.getCount(essentia) != 0 && container.whitelisted(essentia)) {
						tooltip.add(new TranslatableText(prefix + "component",
								essentia.getName(), container.getCount(essentia)).formatted(Formatting.GOLD));
					}
				}
			}

		} else {
			if (container.isWhitelistEnabled() && container.getWhitelist().size() == 1) {
				for (Essentia essentia : container.getWhitelist()) {
					tooltip.add(new TranslatableText(prefix + "single", essentia.getName(),
						container.getCount(essentia), container.getCapacity()).formatted(Formatting.GREEN));
				}
			} else if (container.isWhitelistEnabled() && container.getWhitelist().isEmpty()) {
				tooltip.add(new TranslatableText(prefix + "empty").formatted(Formatting.GRAY));
			} else {
				tooltip.add(new TranslatableText(prefix + "mixed", container.getCount(),
					container.getCapacity()).formatted(Formatting.AQUA));
				for (Essentia essentia : container.getContents().sortedList()) {
					if (container.getCount(essentia) != 0 && container.whitelisted(essentia)) {
						tooltip.add(new TranslatableText(prefix + "component",
							essentia.getName(), container.getCount(essentia)).formatted(Formatting.GOLD));
					}
				}
			}
		}

		if (!container.isInput() && !container.isOutput()) {
			tooltip.add(new TranslatableText(prefix + "locked").formatted(Formatting.RED));
		} else if (!container.isInput()) {
			tooltip.add(new TranslatableText(prefix + "output").formatted(Formatting.RED));
		} else if (!container.isOutput()) {
			tooltip.add(new TranslatableText(prefix + "input").formatted(Formatting.RED));
		}

		super.appendTooltip(stack, world, tooltip, ctx);
	}

	@Override
	protected String getOrCreateTranslationKey() {
		if (translationKey == null) {
			translationKey = Util.createTranslationKey("item", Registry.ITEM.getId(AoAItems.ESSENTIA_VESSEL));
		}
		return this.translationKey;
	}

	@Override
	public String getTranslationKey() {
		return getOrCreateTranslationKey();
	}

	private String tooltipPrefix() {
		return getTranslationKey() + ".tooltip_";
	}

}
