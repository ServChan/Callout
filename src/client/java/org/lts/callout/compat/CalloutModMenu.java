package org.lts.callout.compat;

import org.lts.callout.gui.CalloutConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class CalloutModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return CalloutConfigScreen::new;
    }
}
