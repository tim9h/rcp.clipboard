package dev.tim9h.rcp.clipboard;

import java.util.Collections;
import java.util.Map;

import com.google.inject.Inject;

import dev.tim9h.rcp.spi.CCard;
import dev.tim9h.rcp.spi.CCardFactory;

public class ClipboardViewFactory implements CCardFactory {

	@Inject
	private ClipboardView view;

	@Override
	public String getId() {
		return "clipboard";
	}

	@Override
	public CCard createCCard() {
		return view;
	}

	@Override
	public Map<String, String> getSettingsContributions() {
		return Collections.emptyMap();
	}

}
