package dev.htcl;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class HtCl implements ClientModInitializer {
	public static KeyMapping key;

	public void onInitializeClient() {
		Cfg.ld();
		KeyMapping k = key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hitcolor.open", InputConstants.KEY_H, KeyMapping.Category.register(Identifier.fromNamespaceAndPath("hitcolor", "main"))));
		ClientTickEvents.END_CLIENT_TICK.register(m -> {
			while (k.consumeClick()) if (m.gui.screen() == null) m.gui.setScreen(new dev.htcl.ui.Panel(null));
		});
	}
}
