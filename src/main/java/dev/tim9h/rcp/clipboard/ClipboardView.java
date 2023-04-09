package dev.tim9h.rcp.clipboard;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;

import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.spi.CCard;
import dev.tim9h.rcp.spi.Mode;

public class ClipboardView implements CCard {

	@InjectLogger
	private Logger logger;

	@Inject
	private EventManager eventManager;

	@Override
	public String getName() {
		return "Clipboard Interceptor";
	}

	@Override
	public Optional<List<Mode>> getModes() {
		return Optional.of(Arrays.asList(new Mode() {

			private ClipboardInterceptor listener;

			@Override
			public void onEnable() {
				if (listener == null) {
					listener = new ClipboardInterceptor(sysClip -> {
						try {
							var data = (String) sysClip.getData(DataFlavor.stringFlavor);
							sysClip.setContents(new StringSelection(data), null);
						} catch (UnsupportedFlavorException | IOException e) {
							logger.error(() -> "Clipboard changed but unable to get string data", e);
						}
					});
				}
				listener.start();
				eventManager.echo("Stripping clipboard formatting");
			}

			@Override
			public void onDisable() {
				listener.stopListener();
				listener = null;
				eventManager.echo("Stopped intercepting clipboard");
			}

			@Override
			public String getName() {
				return "clipboard";
			}
		}));
	}

}