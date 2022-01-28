package com.musadsl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.MidiIn;
import com.bitwig.extension.controller.api.NoteInput;
import com.bitwig.extension.controller.api.SettableBooleanValue;
import com.bitwig.extension.controller.api.SettableStringValue;
import com.bitwig.extension.controller.api.Signal;

/* RECORDAR PARA PONER LA VARIABLE DE DEBUG:
 * 
 * launchctl setenv BITWIG_DEBUG_PORT 5005
 * 
 */

public class Controller extends ControllerExtension {

	String controllerName = null;

	ControllerData controllerData = null;
	OscHandler oscHandler = null;

	static Logger log = null;
	
	private static List<Controller> controllers = new ArrayList<>();
	private static Map<String, ControllerData> controllersData = new HashMap<>();
	
	private static ServerRunner serverRunner = null;
	
	private final ControllerHost host;

	private NoteInput noteInputs[] = new NoteInput[16];
	
	protected Controller(
			final MusaLCEforBitwigExtensionDefinition definition,
			final ControllerHost host) {
	
		super(definition, host);
		
		this.host = host;
		log = new Logger(host);
		
		if(serverRunner == null) {
			serverRunner = new ServerRunner();
		}
	}
	
	@Override
	public void init() {
		log.info("controller", "\ninit() started");

		/* 
		 * Controller Attributes 
		 * 
		 * */
		
		SettableStringValue controllerNameParameter = 
				host.getPreferences().getStringSetting("Controller Name", "Configuration", 32, newControllerName());
		
		controllerNameParameter.addValueObserver(newValue -> {
			Controller alreadyExistingController = findControllerByName(newValue);
			
			log.info("controller", "controllerNameParameter observer: new value " + newValue + " (old value " + controllerName + ")");
			
			if(!(newValue.isBlank() || newValue.isEmpty())) {
				
				boolean shouldRestart = !newValue.equals(controllerName);

				if(alreadyExistingController == null || alreadyExistingController == this) {

					ControllerData controllerData = controllersData.remove(controllerName);
					controllerName = newValue;
					controllerData.setControllerName(newValue);
					controllersData.put(controllerName, controllerData);

				} else {
					host.showPopupNotification("Controller name '" + newValue + "' already in use.");
					controllerNameParameter.set(controllerName);
				}
				
				if(shouldRestart) { delayedRestart(); }
			} else {
				host.showPopupNotification("Controller name can't be empty.");
				controllerNameParameter.set(controllerName);
			}
			
		});
		
		SettableBooleanValue oscHostParameter = host.getPreferences().getBooleanSetting("Osc Host", "Configuration", false);
		
		oscHostParameter.addValueObserver(newValue -> {
			if(newValue != controllerData.isOscHost) {
				controllerData.isOscHost = newValue;
				if(!newValue) {
					oscHandler.unlink();
					oscHandler = null;
				}
				delayedRestart();
			}
		});
		
		SettableBooleanValue clockSenderkParameter = host.getPreferences().getBooleanSetting("Clock Sender", "Configuration", false);
		clockSenderkParameter.addValueObserver(newValue -> {
			if(newValue != controllerData.isClockSender) {
				controllerData.isClockSender = newValue;
				delayedRestart();
			}
		});
		
		/* 
		 * Controller Registry 
		 * 
		 * */

		controllerName = controllerNameParameter.get();
		
		log.info("controller", "controllerName = " + controllerName);

		controllerData = getControllerData();
		
		if(controllerData == null) {
			log.info("controller", "Beginning init: controllerData == null... Creating a ControllerData for " + controllerName);
			controllerData = new ControllerData(controllerName);
			controllersData.put(controllerName, controllerData);
		} else {
			controllerData.willRestart = false;
			controllerData.dump("Beginning init");
		}

		host.showPopupNotification("MusaLCE Initialized! (" + controllerName + ")");

		controllers.add(this);
		
		/*
		 * Server Startup/Shutdown
		 * 
		 * 
		 * */
		
		if(controllerData.isOscHost) {
			Signal startServerSignal = host.getPreferences().getSignalSetting(" ", "Server", "Start");
			startServerSignal.addSignalObserver(() -> {
				serverRunner.run();
			});
			
			Signal killServerSignal = host.getPreferences().getSignalSetting("  ", "Server", "Shutdown");
			killServerSignal.addSignalObserver(() -> {
				serverRunner.kill();
			});
		}

		/* 
		 * Port Name Configuration 
		 * 
		 * */
		
		String portName = controllerData.portName != null ? controllerData.portName : controllerName; 

		SettableStringValue portNameParameter = 
				host.getDocumentState().getStringSetting("Name","Port", 32, portName);
		
		
		log.info("controller", "portNameParameter.get() = " + portNameParameter.get());
		
		final ControllerData finalControllerData = controllerData;
		
		portNameParameter.addValueObserver(newValue -> {
			log.info("controller", "portNameParameter.observer: new value " + newValue + " (old value " + finalControllerData.portName + ")");

			boolean shouldRestart = !newValue.equals(finalControllerData.portName);
			
			finalControllerData.portName = newValue;
			
			if(shouldRestart) { delayedRestart(); }
		});
		
		/* 
		 * Channels Configuration 
		 * 
		 * */
		
		SettableStringValue channelNameParameters[] = new SettableStringValue[16];

		for(int i = 0; i < 16; i++) {
			final int ii = i;
			
			String channelName = controllerData.channelNames[ii] != null ? controllerData.channelNames[ii] : "<unused>";
			
			channelNameParameters[ii] = host.getDocumentState().getStringSetting("Channel " + ii, "Channels", 32, channelName);
			
			channelNameParameters[ii].addValueObserver(newValue -> { 
				log.info("controller", "channelNameParameters[" + ii + "].observer: new value " + newValue + " (old value " + controllerData.channelNames[ii] + ")");
				
				boolean shouldRestart = !newValue.equals(controllerData.channelNames[ii]);
				
				controllerData.channelNames[ii] = newValue;
				
				if(shouldRestart) { delayedRestart(); }
			});
		}
		
		/* 
		 * Midi Port Redirection 
		 * 
		 * */
		
		controllerData.dump("Before midi port creation");

		if(controllerData.portName != null) {

			final MidiIn port = host.getMidiInPort(0);

			port.createNoteInput(controllerData.portName + ": All ports", "??????");
			
			for(int i = 0; i < 16; i++) {
				noteInputs[i] = port.createNoteInput(controllerData.portName + ": " + 
						controllerData.channelNames[i], "?" + Integer.toHexString(i) + "????");
			}
			
			log.info("controller", "Created midi ports for " + controllerName);
		} else {
			log.info("controller", "Skipped creation of midi ports for " + controllerName);
		}
		
		
		/* 
		 * Osc Host Configuration 
		 * 
		 * */
		
		if(controllerData.isOscHost) {
			if(controllerData.portName != null) {
				oscHandler = new OscHandler(host.getOscModule(), controllersData, host);
				log.info("controller", "Created OSC link for " + controllerName);
			} else {
				log.info("controller", "Skipped creation of OSC link because controller data is still uninitialized for " + controllerName);
			}
		} else {
			log.info("controller", "Skipped creation of OSC link because OSC is disabled for " + controllerName);
		}
		
		/* 
		 * Osc Update MusaLCE Server status
		 * 
		 *  */
		
		if(controllerData.shouldNotifyOschHost) {
			Controller controller = findOscHost();
			if(controller != null && controller.oscHandler != null) {
				if(this != controller) {
					controller.oscHandler.updateControllerData(controllerData);
				}
			} else {
				log.info("controller", "Couldn't update status for " + controllerName + " because there is no OscHost (yet)");
			}
			controllerData.shouldNotifyOschHost = false;
		}

		/* 
		 * Finishing 
		 * 
		 * */
		
		log.info("controller", "init() finished\n");
	}
	
	private Controller findOscHost() {
		for(Controller controller: controllers) {
			if(controller.controllerData.isOscHost) {
				return controller;
			}
		}
		return null;
	}

	private void delayedRestart() {
		if(!controllerData.willRestart) {
			host.println("Will restart in a few moments...");
			
			Executors.newSingleThreadScheduledExecutor().schedule(
					() -> { 
						log.info("controller", "Restarting now");
						host.restart(); 
					}, 1, TimeUnit.SECONDS);
			
			controllerData.willRestart = true;
			controllerData.shouldNotifyOschHost = true;
		} else {
			log.info("controller", "Restart already scheduled...");
		}
	}

	@Override
	public void exit() {
		controllers.remove(this);
		getHost().showPopupNotification("MusaLCE Exited (" + controllerName + ")");
	}

	@Override
	public void flush() {
		// TODO Send any updates you need here.
	}
	
	private String newControllerName() {
		String newName = "no name";
		int i = 1;
		while(findControllerByName(newName) != null) {
			newName = "no name " + i;
			i++;
		}
		return newName;
	}
	
	private ControllerData getControllerData() {
		if(controllerName != null) {
			return controllersData.get(controllerName);
		} else {
			return null;
		}
	}

	private Controller findControllerByName(String name) {
		for(int i = 0; i < controllers.size(); i++) {
			String controllerName = controllers.get(i).controllerName;
			if(controllerName != null && controllerName.equals(name)) {
				return controllers.get(i);
			}
		}
		
		return null;
	}
}
