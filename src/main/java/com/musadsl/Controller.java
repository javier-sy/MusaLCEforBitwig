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

/* RECORDAR PARA PONER LA VARIABLE DE DEBUG:
 * 
 * launchctl setenv BITWIG_DEBUG_PORT 5005
 * 
 */

public class Controller extends ControllerExtension {

	String controllerName = null;

	ControllerData controllerData = null;
	OscHandler oscHandler = null;

	private static List<Controller> controllers = new ArrayList<>();
	private static Map<String, ControllerData> controllersData = new HashMap<>();
	
	private final ControllerHost host;

	private NoteInput noteInputs[] = new NoteInput[16];
	
	protected Controller(
			final MusaLCEforBitwigExtensionDefinition definition,
			final ControllerHost host) {
	
		super(definition, host);
		
		this.host = host;
	}
	
	@Override
	public void init() {
		host.println("\ninit() started");

		/* 
		 * Controller Attributes 
		 * 
		 * */
		
		SettableStringValue controllerNameParameter = 
				host.getPreferences().getStringSetting("Controller Name", "Configuration", 32, newControllerName());
		
		controllerNameParameter.addValueObserver(newValue -> {
			Controller alreadyExistingController = findControllerByName(newValue);
			
			host.println("controllerNameParameter observer: new value " + newValue + " (old value " + controllerName + ")");
			
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
		
		host.println("controllerName = " + controllerName);

		controllerData = getControllerData();
		
		if(controllerData == null) {
			host.println("Beginning init: controllerData == null... Creating a ControllerData for " + controllerName);
			controllerData = new ControllerData(controllerName);
			controllersData.put(controllerName, controllerData);
		} else {
			controllerData.willRestart = false;
			controllerData.dump(host, "Beginning init");
		}

		host.showPopupNotification("MusaLCE Initialized! (" + controllerName + ")");

		controllers.add(this);
		
		/* 
		 * Port Name Configuration 
		 * 
		 * */
		
		String portName = controllerData.portName != null ? controllerData.portName : controllerName; 

		SettableStringValue portNameParameter = 
				host.getDocumentState().getStringSetting("Name","Port", 32, portName);
		
		
		host.println("portNameParameter.get() = " + portNameParameter.get());
		
		final ControllerData finalControllerData = controllerData;
		
		portNameParameter.addValueObserver(newValue -> {
			host.println("portNameParameter.observer: new value " + newValue + " (old value " + finalControllerData.portName + ")");

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
				host.println("channelNameParameters[" + ii + "].observer: new value " + newValue + " (old value " + controllerData.channelNames[ii] + ")");
				
				boolean shouldRestart = !newValue.equals(controllerData.channelNames[ii]);
				
				controllerData.channelNames[ii] = newValue;
				
				if(shouldRestart) { delayedRestart(); }
			});
		}
		
		/* 
		 * Midi Port Redirection 
		 * 
		 * */
		
		controllerData.dump(host, "Before midi port creation");

		if(controllerData.portName != null) {

			final MidiIn port = host.getMidiInPort(0);

			port.createNoteInput(controllerData.portName + ": All ports", "??????");
			
			for(int i = 0; i < 16; i++) {
				noteInputs[i] = port.createNoteInput(controllerData.portName + ": " + 
						controllerData.channelNames[i], "?" + Integer.toHexString(i) + "????");
			}
			
			host.println("Created midi ports");
		} else {
			host.println("Skipped creation of midi ports");
		}
		
		
		/* 
		 * Osc Host Configuration 
		 * 
		 * */
		
		if(controllerData.isOscHost) {
			if(controllerData.portName != null) {
				oscHandler = new OscHandler(host.getOscModule(), controllersData, host);
				host.println("Created OSC link for " + controllerName);
			} else {
				host.println("Skipped creation of OSC link because controller data is still uninitialized for " + controllerName);
			}
		} else {
			host.println("Skipped creation of OSC link because OSC is disabled for " + controllerName);
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
				host.println("Couldn't update status for " + controllerName + " because there is no OscHost (yet)");
			}
			controllerData.shouldNotifyOschHost = false;
		}

		/* 
		 * Finishing 
		 * 
		 * */
		
		host.println("init() finished\n");
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
						host.println("Restarting now");
						host.restart(); 
					}, 1, TimeUnit.SECONDS);
			
			controllerData.willRestart = true;
			controllerData.shouldNotifyOschHost = true;
		} else {
			host.println("Restart already scheduled...");
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