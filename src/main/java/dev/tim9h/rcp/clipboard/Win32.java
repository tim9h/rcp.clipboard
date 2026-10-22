package dev.tim9h.rcp.clipboard;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/**
 * Minimal Win32 bindings (x64) built on the Foreign Function and Memory API.
 */
final class Win32 {

	static final int WM_DESTROY = 0x0002;

	static final int WM_CLOSE = 0x0010;

	static final int WM_CLIPBOARDUPDATE = 0x031D;

	/** HWND_MESSAGE: parent for message-only windows */
	static final long HWND_MESSAGE = -3L;

	private static final boolean OS_SUPPORTED = System.getProperty("os.name", "").toLowerCase().contains("win")
			&& "amd64".equals(System.getProperty("os.arch"));

	private static final Linker LINKER = Linker.nativeLinker();

	private static final SymbolLookup USER32 = OS_SUPPORTED ? library("user32") : null;

	private static final SymbolLookup KERNEL32 = OS_SUPPORTED ? library("kernel32") : null;

	static final MethodHandle GET_MODULE_HANDLE = fn(KERNEL32, "GetModuleHandleW",
			FunctionDescriptor.of(ADDRESS, ADDRESS));

	static final MethodHandle REGISTER_CLASS = fn(USER32, "RegisterClassExW",
			FunctionDescriptor.of(JAVA_SHORT, ADDRESS));

	static final MethodHandle UNREGISTER_CLASS = fn(USER32, "UnregisterClassW",
			FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

	static final MethodHandle CREATE_WINDOW = fn(USER32, "CreateWindowExW", FunctionDescriptor.of(ADDRESS, JAVA_INT,
			ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

	static final MethodHandle DESTROY_WINDOW = fn(USER32, "DestroyWindow", FunctionDescriptor.of(JAVA_INT, ADDRESS));

	static final MethodHandle DEF_WINDOW_PROC = fn(USER32, "DefWindowProcW",
			FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT, JAVA_LONG, JAVA_LONG));

	static final MethodHandle ADD_CLIPBOARD_LISTENER = fn(USER32, "AddClipboardFormatListener",
			FunctionDescriptor.of(JAVA_INT, ADDRESS));

	static final MethodHandle REMOVE_CLIPBOARD_LISTENER = fn(USER32, "RemoveClipboardFormatListener",
			FunctionDescriptor.of(JAVA_INT, ADDRESS));

	static final MethodHandle GET_MESSAGE = fn(USER32, "GetMessageW",
			FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));

	static final MethodHandle DISPATCH_MESSAGE = fn(USER32, "DispatchMessageW",
			FunctionDescriptor.of(JAVA_LONG, ADDRESS));

	static final MethodHandle POST_MESSAGE = fn(USER32, "PostMessageW",
			FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_LONG, JAVA_LONG));

	static final MethodHandle POST_QUIT_MESSAGE = fn(USER32, "PostQuitMessage", FunctionDescriptor.ofVoid(JAVA_INT));

	private static final boolean SUPPORTED = OS_SUPPORTED && GET_MODULE_HANDLE != null && REGISTER_CLASS != null
			&& UNREGISTER_CLASS != null && CREATE_WINDOW != null && DESTROY_WINDOW != null && DEF_WINDOW_PROC != null
			&& ADD_CLIPBOARD_LISTENER != null && REMOVE_CLIPBOARD_LISTENER != null && GET_MESSAGE != null
			&& DISPATCH_MESSAGE != null && POST_MESSAGE != null && POST_QUIT_MESSAGE != null;

	private Win32() {
		// static access only
	}

	static boolean isSupported() {
		return SUPPORTED;
	}

	static Linker linker() {
		return LINKER;
	}

	private static MethodHandle fn(SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
		if (lookup == null) {
			return null;
		}
		try {
			return LINKER.downcallHandle(lookup.findOrThrow(name), descriptor);
		} catch (RuntimeException | LinkageError e) {
			return null;
		}
	}

	private static SymbolLookup library(String name) {
		try {
			return SymbolLookup.libraryLookup(name, Arena.global());
		} catch (RuntimeException | LinkageError e) {
			return null;
		}
	}

}
