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
import dev.tim9h.rcp.spi.Mode;
import dev.tim9h.rcp.spi.Plugin;

public class ClipboardView implements Plugin {

	@InjectLogger
	private Logger logger;

	@Inject
	private EventManager eventManager;

	@Override
	public String getName() {
		return "Clipboard Interceptor";
	}

	@Override
	public String getId() {
		return "clipboard";
	}

	private ClipboardInterceptor listener;

	@Override
	public void init() {
		// warm up native bindings off the UI thread
		var warmup = new Thread(Win32::isSupported, "clipboard-warmup");
		warmup.setDaemon(true);
		warmup.start();
	}

	@Override
	public void onShutdown() {
		stopListener();
	}

	private synchronized void stopListener() {
		if (listener != null) {
			listener.stopListener();
			listener = null;
		}
	}

	@Override
	public Optional<List<Mode>> getModes() {
		return Optional.of(Arrays.asList(new Mode() {

			@Override
			public void onEnable() {
				if (!Win32.isSupported()) {
					eventManager.echo("Clipboard interceptor is only supported on Windows");
					return;
				}
				synchronized (ClipboardView.this) {
					if (listener == null) {
						listener = new ClipboardInterceptor(sysClip -> {
							try {
								var data = (String) sysClip.getData(DataFlavor.stringFlavor);
								if (data != null) {
									sysClip.setContents(new StringSelection(data), null);
								}
							} catch (UnsupportedFlavorException | IOException e) {
								logger.error(() -> "Clipboard changed but unable to get string data", e);
							}
						});
						listener.start();
					}
					if (!listener.awaitStarted(1000)) {
						listener.stopListener();
						listener = null;
						eventManager.echo("Unable to start clipboard interceptor");
						return;
					}
				}
				eventManager.echo("Stripping clipboard formatting");
			}

			@Override
			public void onDisable() {
				stopListener();
				eventManager.echo("Stopped intercepting clipboard");
			}

			@Override
			public String getName() {
				return "clipboard";
			}
		}));
	}

}