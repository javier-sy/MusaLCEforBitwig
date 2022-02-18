package com.musadsl;

class ControllerData {
	private String oldControllerName = null;
	private String controllerName = null;

	String portName = null;
	String channelNames[] = new String[16];
	
	boolean willRestart = false;
	
	boolean isOscHost = false;
	boolean isClockSender = false;
	boolean startServer = false;
	
	boolean shouldNotifyOschHost = false;
	boolean shouldKillServer = true;
	
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
	
	void dump(String label) {
		Controller.log.info("controller", "| " + label);
		Controller.log.info("controller", "|");
		Controller.log.info("controller", "| controllerName " + controllerName);
		Controller.log.info("controller", "| portName " + portName);
		for(int i = 0; i < 16; i++) {
			Controller.log.info("controller", "| channel " + i + " " + channelNames[i]);
		}
	}
}
