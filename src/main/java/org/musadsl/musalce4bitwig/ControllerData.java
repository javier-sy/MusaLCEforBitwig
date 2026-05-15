package org.musadsl.musalce4bitwig;

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

	// Pulso Bridge endpoints — see "OSC" category in the controller
	// preferences. Loaded from preferences at init() in Controller.java;
	// changes trigger delayedRestart() the same way as isClockSender etc.
	//
	// The MusaLCEServer channel (this extension ↔ Ruby server) is NOT
	// configurable: it lives at the fixed pair 10001/11011 because the
	// Ruby server hardcodes its sockets and there is no way to override
	// them from outside. Exposing controller-side prefs for that channel
	// would only let the user introduce a mismatch nothing could fix.
	String pulsoSendHost = "127.0.0.1";
	int pulsoSendPort = 21012;
	int pulsoListenPort = 20002;
	
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
		Controller.log.info("controller", "| pulso send " + pulsoSendHost + ":" + pulsoSendPort
				+ " listen " + pulsoListenPort);
		for(int i = 0; i < 16; i++) {
			Controller.log.info("controller", "| channel " + i + " " + channelNames[i]);
		}
	}
}
