package com.musadsl;

import com.bitwig.extension.controller.api.ControllerHost;

class ControllerData {
	private String oldControllerName = null;
	private String controllerName = null;

	String portName = null;
	String channelNames[] = new String[16];
	
	boolean willRestart = false;
	
	boolean isOscHost = false;
	boolean isClockSender = false;
	
	boolean shouldNotifyOschHost = false;
	
	ControllerData(String controllerName) {
		this.controllerName = controllerName;
	}
	
	public String getControllerName() { return controllerName; }
	public String getOldControllerName() { return oldControllerName; }
	
	public void setControllerName(String newName) {
		if(!newName.equals(controllerName)) {
			oldControllerName = controllerName;
		}
		controllerName = newName;
	}
	
	public void forgetOldControllerName() {
		oldControllerName = null;
	}
	
	void dump(ControllerHost host, String label) {
		host.println("| " + label);
		host.println("|");
		host.println("| controllerName " + controllerName);
		host.println("| portName " + portName);
		for(int i = 0; i < 16; i++) {
			host.println("| channel " + i + " " + channelNames[i]);
		}
	}
}
