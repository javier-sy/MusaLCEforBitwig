package org.musadsl.musalce4bitwig;

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
import com.bitwig.extension.controller.api.SettableStringValue;

/* RECORDAR PARA PONER LA VARIABLE DE DEBUG:
 * 
 * launchctl setenv BITWIG_DEBUG_PORT 5005
 * 
 * LOGS:
 * 
 * tail -f $TMPDIR/musalceserver.log
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
		log.info("controller", "init() started");

		/* 
		 * Controller Name Attribute 
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
					popup("Controller name '" + newValue + "' already in use", false);
					controllerNameParameter.set(controllerName);
				}
				
				if(shouldRestart) { delayedRestart(); }
			} else {
				popup("Controller name can't be empty", false);
				controllerNameParameter.set(controllerName);
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

		popup("MusaLCE Initialized! (" + controllerName + ")", false);

		controllers.add(this);
		

		
		/* 
		 * Controller Attributes: Osc Host, Clock Sender and Start Server toggles 
		 * 
		 * */

		host.getPreferences().getBooleanSetting("Osc Host", "Configuration", false).addValueObserver(newValue -> {
			if(newValue != controllerData.isOscHost) {
				controllerData.isOscHost = newValue;
				if(!newValue) {
					oscHandler.unlink();
					oscHandler = null;
				}
				delayedRestart();
			}
		});
		
		host.getPreferences().getBooleanSetting("Clock Sender", "Configuration", false).addValueObserver(newValue -> {
			if(newValue != controllerData.isClockSender) {
				controllerData.isClockSender = newValue;
				delayedRestart();
			}
		});
		
		host.getPreferences().getBooleanSetting("Start Server", "Server", false).addValueObserver(newValue -> {
			if(controllerData.isOscHost) {
				if(newValue != controllerData.startServer) {
					controllerData.startServer = newValue;
					delayedRestart();
				}
			} else {
				if(newValue != controllerData.startServer) {
					controllerData.startServer = newValue;
					popup("Ignored parameter 'Start Server': 'Osc Host' is not enabled for this controller", false);
				}
			}
		});
		
		/*
		 * Server Reload Button
		 * 
		 * */
		
		host.getPreferences().getSignalSetting(" ", "Server", "Reload").addSignalObserver(() -> {
			if(controllerData.isOscHost && controllerData.startServer) {
				if(serverRunner.isRunning()) {
					popup("Stopping MusaLCEServer...", false);
					serverRunner.kill();
					popup("MusaLCEServer stopped", false);
				}
				
				try {
					TimeUnit.SECONDS.sleep(1);
				} catch (InterruptedException e) { }
				
				serverRunner.run();
				popup("MusaLCEServer reloaded", false);
			} else {
				popup("Ignored action 'Restart': server is not enabled", false);
			}
			
		});

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
			finalControllerData.portName = newValue;
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
				controllerData.channelNames[ii] = newValue;
			});
		}
		
		host.getDocumentState().getSignalSetting(" ", "Save", "Save Changes").addSignalObserver(() -> {
			delayedRestart();
		});
		
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
				oscHandler = new OscHandler(host.getOscModule(), controllersData, host, host.createTransport());
				log.info("controller", "Created OSC link for " + controllerName);
			} else {
				log.info("controller", "Skipped creation of OSC link because controller data is still uninitialized for " + controllerName);
			}
		} else {
			log.info("controller", "Skipped creation of OSC link because OSC is disabled for " + controllerName);
		}
		
		/* 
		 * Start/Stop MusaLCE Server
		 * 
		 *  */
		
		if(controllerData.startServer) {
			if(serverRunner.isRunning()) {
				popup("MusaLCEServer already started", false);
			} else {
				serverRunner.run();
				popup("MusaLCEServer started", false);
			}
		} else {
			if(serverRunner.isRunning()) {
				serverRunner.kill();
				popup("MusaLCEServer stopped", false);
			}
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
						controllerData.shouldKillServer = false;
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
		if(controllerData.shouldKillServer && serverRunner.isRunning()) { 
			serverRunner.kill(); 
			popup("MusaLCE Server stopped", true);
		}
		
		controllers.remove(this);

		controllerData.shouldKillServer = true;

		popup("MusaLCE Exited (" + controllerName + ")", true);
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

	private void popup(String message, boolean wait) {
		getHost().showPopupNotification(message);

		if(wait) {
			try {
				TimeUnit.SECONDS.sleep(1);
			} catch (InterruptedException e) { }
		}
	}
}
