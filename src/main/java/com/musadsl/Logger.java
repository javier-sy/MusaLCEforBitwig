package com.musadsl;

import com.bitwig.extension.controller.api.ControllerHost;

public class Logger {
	private ControllerHost host = null;
	
	public Logger(ControllerHost host) {
		this.host = host;
	}
	
	public void info(String module, String s) {
		host.println("[" + module + "] " + s);
	}
	
	public void error(String module, String s ) {
		host.errorln("[" + module + "] " + s);
	}
}
