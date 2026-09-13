package com.s4fmer.boozecraft;

import com.mojang.logging.LogUtils;
import com.s4fmer.boozecraft.booze.BoozeEvents;
import com.s4fmer.boozecraft.reg.BoozeBlockEntities;
import com.s4fmer.boozecraft.reg.BoozeBlocks;
import com.s4fmer.boozecraft.reg.BoozeEffects;
import com.s4fmer.boozecraft.reg.BoozeItems;
import com.s4fmer.boozecraft.reg.BoozeTabs;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * BoozeCraft - alcohol, soft drinks, bar counter and drunkenness for NeoForge 1.21.1.
 *
 * Design rules that keep this mod hybrid-server (Youer / Mohist / Arclight) friendly:
 *  - no mixins, no coremods, no access transformers
 *  - no custom network packets (the client derives visuals from vanilla-synced mob effects)
 *  - no custom containers / GUIs (Bukkit inventory layer never gets confused)
 *  - all gameplay logic runs server side only
 */
@Mod(BoozeCraft.MODID)
public class BoozeCraft {

	public static final String MODID = "boozecraft";
	public static final Logger LOGGER = LogUtils.getLogger();

	public BoozeCraft(IEventBus modBus, ModContainer container) {
		BoozeItems.ITEMS.register(modBus);
		BoozeBlocks.BLOCKS.register(modBus);
		BoozeBlockEntities.TYPES.register(modBus);
		BoozeEffects.EFFECTS.register(modBus);
		BoozeTabs.TABS.register(modBus);

		NeoForge.EVENT_BUS.register(new BoozeEvents());

		container.registerConfig(ModConfig.Type.SERVER, BoozeConfig.SERVER_SPEC);
		container.registerConfig(ModConfig.Type.CLIENT, BoozeConfig.CLIENT_SPEC);

		LOGGER.info("[BoozeCraft] initialised");
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MODID, path);
	}
}
