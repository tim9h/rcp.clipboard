package dev.tim9h.rcp.clipboard;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Receives WM_CLIPBOARDUPDATE notifications through a hidden message-only
 * window (no polling) and only touches the clipboard when it contains rich text
 * (HTML/RTF) alongside plain text. Images, files and other content are left
 * alone.
 */
public class ClipboardInterceptor extends Thread {

	private static final Logger logger = LogManager.getLogger(ClipboardInterceptor.class);

	private static final String WINDOW_CLASS_PREFIX = "dev.tim9h.rcp.clipboard.listener.";

	private static final AtomicInteger INSTANCES = new AtomicInteger();

	private static final int MAX_RETRIES = 5;

	private static final long RETRY_DELAY_MS = 30;

	private final Clipboard sysClip = Toolkit.getDefaultToolkit().getSystemClipboard();

	private final Consumer<Clipboard> action;

	private final String windowClass = WINDOW_CLASS_PREFIX + INSTANCES.incrementAndGet();

	private final CountDownLatch started = new CountDownLatch(1);

	private volatile boolean startedOk;

	private volatile long hwnd;

	private volatile boolean stopRequested;

	public ClipboardInterceptor(Consumer<Clipboard> action) {
		super("clipboardListener");
		setDaemon(true);
		this.action = action;
	}

	/**
	 * Waits until the listener is registered or failed to start.
	 *
	 * @return true if the listener is active
	 */
	public boolean awaitStarted(long timeoutMs) {
		try {
			return started.await(timeoutMs, TimeUnit.MILLISECONDS) && startedOk;
		} catch (InterruptedException _) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	@Override
	public void run() {
		try (var arena = Arena.ofConfined()) {
			var windowProc = Win32.linker().upcallStub(
					MethodHandles.lookup()
							.findVirtual(ClipboardInterceptor.class, "windowProc",
									MethodType.methodType(long.class, MemorySegment.class, int.class, long.class,
											long.class))
							.bindTo(this),
					FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT, JAVA_LONG, JAVA_LONG), arena);

			var hInstance = (MemorySegment) Win32.GET_MODULE_HANDLE.invoke(MemorySegment.NULL);
			var className = arena.allocateFrom(windowClass, StandardCharsets.UTF_16LE);

			// WNDCLASSEXW (x64, 80 bytes)
			var wndClass = arena.allocate(80, 8);
			wndClass.set(JAVA_INT, 0, 80); // cbSize
			wndClass.set(ADDRESS, 8, windowProc); // lpfnWndProc
			wndClass.set(ADDRESS, 24, hInstance); // hInstance
			wndClass.set(ADDRESS, 64, className); // lpszClassName
			var atom = (short) Win32.REGISTER_CLASS.invoke(wndClass);
			if (atom == 0) {
				logger.error(() -> "Unable to register clipboard listener window class");
				return;
			}

			try {
				var window = (MemorySegment) Win32.CREATE_WINDOW.invoke(0, className, MemorySegment.NULL, 0, 0, 0, 0, 0,
						MemorySegment.ofAddress(Win32.HWND_MESSAGE), MemorySegment.NULL, hInstance, MemorySegment.NULL);
				if (window.address() == 0) {
					logger.error(() -> "Unable to create clipboard listener window");
					return;
				}
				hwnd = window.address();

				if ((int) Win32.ADD_CLIPBOARD_LISTENER.invoke(window) == 0) {
					logger.error(() -> "Unable to register clipboard format listener");
					int ignored = (int) Win32.DESTROY_WINDOW.invoke(window);
					return;
				}
				if (stopRequested) {
					int ignored = (int) Win32.POST_MESSAGE.invoke(window, Win32.WM_CLOSE, 0L, 0L);
				}
				startedOk = true;
				started.countDown();

				var msg = arena.allocate(48, 8);
				while ((int) Win32.GET_MESSAGE.invoke(msg, MemorySegment.NULL, 0, 0) > 0) {
					long ignored = (long) Win32.DISPATCH_MESSAGE.invoke(msg);
				}
			} finally {
				hwnd = 0;
				int ignored = (int) Win32.UNREGISTER_CLASS.invoke(className, hInstance);
			}
		} catch (Throwable e) {
			logger.error(() -> "Clipboard listener failed", e);
		} finally {
			started.countDown();
		}
	}

	// invoked from native code on the listener thread, must never throw
	@SuppressWarnings("unused")
	private long windowProc(MemorySegment window, int message, long wParam, long lParam) {
		try {
			switch (message) {
			case Win32.WM_CLIPBOARDUPDATE:
				stripFormatting();
				return 0;
			case Win32.WM_CLOSE:
				int closed = (int) Win32.DESTROY_WINDOW.invoke(window);
				return 0;
			case Win32.WM_DESTROY:
				int removed = (int) Win32.REMOVE_CLIPBOARD_LISTENER.invoke(window);
				Win32.POST_QUIT_MESSAGE.invoke(0);
				return 0;
			default:
				return (long) Win32.DEF_WINDOW_PROC.invoke(window, message, wParam, lParam);
			}
		} catch (Throwable e) {
			logger.error(() -> "Error while handling window message " + message, e);
			return 0;
		}
	}

	private void stripFormatting() {
		for (var attempt = 0; attempt < MAX_RETRIES; attempt++) {
			try {
				if (hasFormattedText()) {
					logger.debug(() -> "Formatted text on clipboard, stripping");
					action.accept(sysClip);
				}
				return;
			} catch (IllegalStateException _) {
				// clipboard currently held by another process
				try {
					Thread.sleep(RETRY_DELAY_MS);
				} catch (InterruptedException _) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}
		logger.warn(() -> "Clipboard unavailable, skipping this change");
	}

	private boolean hasFormattedText() {
		if (!sysClip.isDataFlavorAvailable(DataFlavor.stringFlavor)
				|| sysClip.isDataFlavorAvailable(DataFlavor.imageFlavor)
				|| sysClip.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) {
			return false;
		}
		for (var flavor : sysClip.getAvailableDataFlavors()) {
			var subType = flavor.getSubType();
			if ("rtf".equals(subType) || "text".equals(flavor.getPrimaryType()) && "html".equals(subType)) {
				return true;
			}
		}
		return false;
	}

	public void stopListener() {
		stopRequested = true;
		var window = hwnd;
		if (window != 0) {
			try {
				int ignored = (int) Win32.POST_MESSAGE.invoke(MemorySegment.ofAddress(window), Win32.WM_CLOSE, 0L, 0L);
			} catch (Throwable e) {
				logger.error(() -> "Unable to stop clipboard listener", e);
			}
		}
	}

}
