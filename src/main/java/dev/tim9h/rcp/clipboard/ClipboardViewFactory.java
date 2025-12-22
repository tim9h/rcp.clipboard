package dev.tim9h.rcp.clipboard;

import java.util.Collections;
import java.util.Map;

import com.google.inject.Inject;

import dev.tim9h.rcp.spi.Plugin;
import dev.tim9h.rcp.spi.PluginFactory;

public class ClipboardViewFactory implements PluginFactory {

	@Inject
	private ClipboardView view;

	@Override
	public String getId() {
		return "clipboard";
	}

	@Override
	public Plugin create() {
		return view;
	}

	@Override
	public Map<String, String> getSettingsContributions() {
		return Collections.emptyMap();
	}

}
