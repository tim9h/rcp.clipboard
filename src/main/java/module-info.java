module rcp.Clipboard {
	exports dev.tim9h.rcp.clipboard;

	requires transitive rcp.api;
	requires com.google.guice;
	requires org.apache.logging.log4j;
	requires transitive javafx.controls;
	requires java.desktop;
	requires transitive java.datatransfer;
}