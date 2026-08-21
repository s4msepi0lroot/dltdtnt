package ru.s4fmer.badhabits.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import ru.s4fmer.badhabits.BadHabits;
import ru.s4fmer.badhabits.menu.LabMenu;

/** Screen of the Synthesis Lab. */
public class LabScreen extends AbstractContainerScreen<LabMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BadHabits.MODID, "textures/gui/synth_lab.png");

    public LabScreen(LabMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;
        graphics.blit(TEXTURE, left, top, 0, 0, this.imageWidth, this.imageHeight);

        int progress = this.menu.progressPixels();
        if (progress > 0) {
            graphics.blit(TEXTURE, left + 76, top + 35, 176, 0, progress, 16);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
