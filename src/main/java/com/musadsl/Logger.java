package com.musadsl;

import com.bitwig.extension.controller.api.ControllerHost;

public class Logger {
	private ControllerHost host = null;
	
	public Logger(ControllerHost host) {
		this.host = host;
	}
	
	public void info(String s) {
		host.println(s);
	}
	
	public void error(String s ) {
		host.errorln(s);
	}
}
