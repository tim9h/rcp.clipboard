package dev.tim9h.rcp.clipboard;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ClipboardInterceptor extends Thread {

	private static final Logger logger = LogManager.getLogger(ClipboardInterceptor.class);

	private AtomicBoolean running = new AtomicBoolean(false);

	private Clipboard sysClip = Toolkit.getDefaultToolkit().getSystemClipboard();

	private Consumer<Clipboard> action;

	private String recentContent;

	public ClipboardInterceptor(Consumer<Clipboard> action) {
		super("clipboardListener");
		this.action = action;
		recentContent = "";
	}

	@Override
	public void run() {
		running.set(true);
		while (running.get()) {
			try {
				Thread.sleep(250);
			} catch (Exception e) {
				logger.error(() -> "Error while waiting for clipboard contents to change", e);
				Thread.currentThread().interrupt();
			}
			try {
				var flavors = Arrays.asList(sysClip.getAvailableDataFlavors());
				if (flavors.contains(DataFlavor.stringFlavor)) {
					var data = (String) sysClip.getData(DataFlavor.stringFlavor);
					if (!data.equals(recentContent)) {
						logger.debug(() -> "String flavored clipboard changed");
						recentContent = data;
						action.accept(sysClip);
					}
				}
			} catch (IllegalStateException | UnsupportedFlavorException | IOException e) {
				logger.error(() -> "Error while observing clipboard");
			}
		}
		if (!running.get()) {
			interrupt();
		}
	}

	public void stopListener() {
		running.set(false);
	}

}