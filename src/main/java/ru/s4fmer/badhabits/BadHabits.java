package ru.s4fmer.badhabits;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import ru.s4fmer.badhabits.event.BhEvents;
import ru.s4fmer.badhabits.registry.ModCreativeTabs;
import ru.s4fmer.badhabits.registry.ModItems;

/**
 * Bad Habits - NeoForge 1.21.1
 *
 * <p>Vsya igrovaya logika (zavisimost', lomka, kashel') vypolnyaetsya TOL'KO na servere.
 * Klient ne hranit sostoyanie, poetomu mod bezopasen dlya multiplayera i gibridnyh yader
 * (Youer / Mohist / Arclight / Magma): net mixin-ov, net access transformer-ov,
 * net obrashcheniy k klientskim klassam iz obshchego koda.</p>
 */
@Mod(BadHabits.MODID)
public class BadHabits {
    public static final String MODID = "badhabits";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BadHabits(IEventBus modBus, ModContainer container) {
        ModItems.register(modBus);
        ModCreativeTabs.register(modBus);

        // SERVER-konfig: hranitsya v <world>/serverconfig/badhabits-server.toml i sinhroniziruetsya klientu.
        container.registerConfig(ModConfig.Type.SERVER, BhConfig.SPEC);

        // Igrovye sobytiya (tick, chat, komandy, save/load).
        NeoForge.EVENT_BUS.register(new BhEvents());

        LOGGER.info("[BadHabits] initialized: server-authoritative, hybrid-server friendly");
    }
}
