package com.musadsl;

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
	
	void dump(String label) {
		Controller.log.info("| " + label);
		Controller.log.info("|");
		Controller.log.info("| controllerName " + controllerName);
		Controller.log.info("| portName " + portName);
		for(int i = 0; i < 16; i++) {
			Controller.log.info("| channel " + i + " " + channelNames[i]);
		}
	}
}
