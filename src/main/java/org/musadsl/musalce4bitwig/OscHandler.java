package org.musadsl.musalce4bitwig;

import java.io.IOException;
import java.util.Map;

import com.bitwig.extension.api.opensoundcontrol.OscAddressSpace;
import com.bitwig.extension.api.opensoundcontrol.OscConnection;
import com.bitwig.extension.api.opensoundcontrol.OscInvalidArgumentTypeException;
import com.bitwig.extension.api.opensoundcontrol.OscModule;
import com.bitwig.extension.api.opensoundcontrol.OscServer;
import com.bitwig.extension.controller.api.Application;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.Transport;

public class OscHandler {

	final private ControllerHost host;

	private OscServer localserver;
	private OscConnection musalceserver;
	private Application application;

	
	/*
	Algunos de los metodos de la API de Bitwig Studio que pueden ser utiles en el futuro:

	final Project project = host.getProject();
	final Transport transport = host.createTransport();
	
	project.getRootTrackGroup();
	project.getRootTrackGroup().createTrackBank(1, 0, 0, true).itemCount().addValueObserver(newValue -> {});
	
	application.hasActiveEngine();
	application.activateEngine();
	application.deactivateEngine();
	application.projectName();
	
	transport.play();
	transport.continuePlayback();
	transport.stop();
	transport.restart();
	transport.record();
	transport.rewind();
	transport.fastForward();
	transport.isPlaying();
	transport.tempo();
	transport.getPosition();
	transport.launchFromPlayStartPosition();
	transport.jumpToPlayStartPosition();
	transport.jumpToNextCueMarker();
	transport.jumpToPreviousCueMarker();
	transport.timeSignature();
	*/

	public OscHandler(OscModule osc, Map<String, ControllerData> controllers, ControllerHost host, Transport transport) {
		this.host = host;
		
		application = host.createApplication();
		
		transport.isPlaying().markInterested();
		transport.playStartPosition().markInterested();
		
		musalceserver = osc.connectToUdpServer("localhost", 11011, null);

		OscAddressSpace addressSpace = osc.createAddressSpace();
		
		addressSpace.registerMethod("/version", 
				"*", 
				"Receives the version of MusaLCEServer", 
				(source, message) -> {
					Controller.log.info("controller", "Received /version " + message.getString(0));
				});

		addressSpace.registerMethod("/reload", 
				"*", 
				"Reload the controller and restarts MusaLCEServer if it was already started (useful for testing changes in MusaDSL)", 
				(source, message) -> {
					Controller.log.info("controller", "Restarting controller...");
					
					host.restart();
				});

		addressSpace.registerMethod("/musalce4bitwig/sync", 
				"*", 
				"Returns Controllers and Ports. The client is expected to implement /musalce4bitwig/controller and /musalce4bitwig/port to receive the responses.", 
				(source, message) -> {
					Controller.log.info("controller", "Received /musalce4bitwig/sync"); 
		
					try {
						musalceserver.startBundle();
						
						sendIndexControllersData(controllers);

						for(ControllerData controllerData: controllers.values()) {
							sendControllerData(controllerData, controllers.size());
							sendChannelsData(controllerData);
						}

						musalceserver.endBundle();
						
					} catch (OscInvalidArgumentTypeException | IOException e) {
						Controller.log.error("controller", e.getLocalizedMessage());
					}
				});
		
		addressSpace.registerMethod("/musalce4bitwig/play", 
				"*", 
				"Start playing the song", 
				(source, message) -> { 
					if(!transport.isPlaying().get()) {
						transport.jumpToPlayStartPosition();
						transport.play(); 
					}
				});
		
		addressSpace.registerMethod("/musalce4bitwig/stop", 
				"*", 
				"Stop playing the song", 
				(source, message) -> { 
					if(transport.isPlaying().get()) {
						transport.stop();
					}
				});

		addressSpace.registerMethod("/musalce4bitwig/continue", 
				"*", 
				"Continue playing the song", 
				(source, message) -> { 
					if(!transport.isPlaying().get()) {
						transport.continuePlayback();
					}
				});

		addressSpace.registerMethod("/musalce4bitwig/goto", 
				"*", 
				"Sets the play position of the song", 
				(source, message) -> { 
					transport.playStartPosition().set((double)message.getDouble(0));
				});

		addressSpace.registerMethod("/musalce4bitwig/record", 
				"*", 
				"Enable recording", 
				(source, message) -> { 
					transport.record();
				});

		localserver = osc.createUdpServer(addressSpace);
		try {
			localserver.start(10001);
		} catch (IOException e) {
			Controller.log.error("controller", e.getLocalizedMessage());
		}
		
		application.projectName().addValueObserver(newValue -> {
			if(!newValue.isEmpty()) {
				try {
					musalceserver.sendMessage("/hello", newValue);
					Controller.log.info("controller", "Sent /hello");
				} catch (OscInvalidArgumentTypeException | IOException e) {
					Controller.log.error("controller", e.getLocalizedMessage());
				}
			}
		});
	}
	
	private void sendIndexControllersData(Map<String, ControllerData> controllers) throws OscInvalidArgumentTypeException, IOException {
		Object names[] = new Object[controllers.size()];
		
		int i = 0;
		for(ControllerData controllerData: controllers.values()) {
			names[i] = controllerData.getControllerName();
			i++;
		}
		
		musalceserver.sendMessage("/musalce4bitwig/controllers", names);
	}
		
	private void sendControllerData(ControllerData controllerData, int size) throws OscInvalidArgumentTypeException, IOException {
		musalceserver.sendMessage("/musalce4bitwig/controller", 
				controllerData.getControllerName(), 
				controllerData.portName, 
				controllerData.isClockSender ? 1 : 0);
	}
	
	private void sendControllerDataUpdate(ControllerData controllerData) throws OscInvalidArgumentTypeException, IOException {
		musalceserver.sendMessage("/musalce4bitwig/controller/update", 
				controllerData.getOldControllerName() != null ? controllerData.getOldControllerName() : controllerData.getControllerName(), 
				controllerData.getControllerName(), 
				controllerData.portName, 
				controllerData.isClockSender ? 1 : 0);
	}

	private void sendChannelsData(ControllerData controllerData) throws OscInvalidArgumentTypeException, IOException {
		musalceserver.sendMessage("/musalce4bitwig/channels", 
				controllerData.getControllerName(), 
				controllerData.channelNames[0],
				controllerData.channelNames[1],
				controllerData.channelNames[2],
				controllerData.channelNames[3],
				controllerData.channelNames[4],
				controllerData.channelNames[5],
				controllerData.channelNames[6],
				controllerData.channelNames[7],
				controllerData.channelNames[8],
				controllerData.channelNames[9],
				controllerData.channelNames[10],
				controllerData.channelNames[11],
				controllerData.channelNames[12],
				controllerData.channelNames[13],
				controllerData.channelNames[14],
				controllerData.channelNames[15]);
	}
	
	public void updateControllerData(ControllerData controllerData) {
		try {
			musalceserver.startBundle();
			
			sendControllerDataUpdate(controllerData);
			sendChannelsData(controllerData);

			musalceserver.endBundle();
			
			controllerData.forgetOldControllerName();
			
		} catch (OscInvalidArgumentTypeException | IOException e) {
			host.errorln(e.getLocalizedMessage());
		}
	}
	
	public void unlink() {
		application.projectName().unsubscribe();
		
		musalceserver = null;
		localserver = null;
		application = null;
	}
}
