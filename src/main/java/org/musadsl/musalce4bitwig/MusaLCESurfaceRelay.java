package org.musadsl.musalce4bitwig;

import java.io.IOException;

import com.bitwig.extension.api.opensoundcontrol.OscAddressSpace;
import com.bitwig.extension.api.opensoundcontrol.OscConnection;
import com.bitwig.extension.api.opensoundcontrol.OscInvalidArgumentTypeException;
import com.bitwig.extension.api.opensoundcontrol.OscModule;
import com.bitwig.extension.api.opensoundcontrol.OscServer;
import com.bitwig.extension.controller.api.ControllerHost;

/**
 * Bidirectional OSC relay between MusaLCEServer and Pulso Bridge for
 * the MusaLCE Surface protocol (<code>/musalce/surface/*</code>).
 *
 * <p>The extension does <strong>not</strong> validate or aggregate;
 * it forwards each message verbatim. All payloads cross the wire as
 * strings (the Ruby server serializes typed values upstream and the
 * plugin parses them downstream based on the OSC address), so the
 * forwarder is generic and doesn't need to inspect OSC typetags.</p>
 *
 * <h3>Server &rarr; Pulso (handlers on the shared server address
 * space bound to UDP :10001)</h3>
 * <ul>
 *   <li><code>/musalce/surface/sync_request</code> (no args)</li>
 *   <li><code>/musalce/surface/state/message  event text</code></li>
 *   <li><code>/musalce/surface/state/enabled  event enabled</code></li>
 *   <li><code>/musalce/surface/state/value    event value</code></li>
 *   <li><code>/musalce/surface/state/range    event min max</code></li>
 * </ul>
 *
 * <h3>Pulso &rarr; Server (handlers on a dedicated Pulso-side
 * address space bound to UDP :20002 by default)</h3>
 * <ul>
 *   <li><code>/musalce/surface/inventory/begin</code> (no args)</li>
 *   <li><code>/musalce/surface/inventory/add     event type</code></li>
 *   <li><code>/musalce/surface/inventory/remove  event</code></li>
 *   <li><code>/musalce/surface/inventory/end</code> (no args)</li>
 *   <li><code>/musalce/surface/state_request</code> (no args)</li>
 *   <li><code>/musalce/surface/trigger          event payload</code></li>
 * </ul>
 */
public class MusaLCESurfaceRelay {

    private OscConnection toPulsoBridge;
    private OscServer fromPulsoBridgeServer;

    public MusaLCESurfaceRelay(
            OscModule osc,
            ControllerHost host,
            OscAddressSpace serverAddressSpace,
            OscConnection toMusalceServer,
            String pulsoSendHost,
            int pulsoSendPort,
            int pulsoListenPort) {

        toPulsoBridge = osc.connectToUdpServer(pulsoSendHost, pulsoSendPort, null);

        // --- Server → Pulso ---
        registerForwardNoArgs(serverAddressSpace, "/musalce/surface/sync_request", toPulsoBridge);

        registerForwardTwoStrings(serverAddressSpace, "/musalce/surface/state/message", toPulsoBridge);
        registerForwardTwoStrings(serverAddressSpace, "/musalce/surface/state/enabled", toPulsoBridge);
        registerForwardTwoStrings(serverAddressSpace, "/musalce/surface/state/value",   toPulsoBridge);
        registerForwardThreeStrings(serverAddressSpace, "/musalce/surface/state/range", toPulsoBridge);

        // --- Pulso → Server ---
        OscAddressSpace pulsoAddressSpace = osc.createAddressSpace();

        registerForwardNoArgs(pulsoAddressSpace, "/musalce/surface/inventory/begin",   toMusalceServer);
        registerForwardTwoStrings(pulsoAddressSpace, "/musalce/surface/inventory/add",  toMusalceServer);
        registerForwardOneString(pulsoAddressSpace, "/musalce/surface/inventory/remove", toMusalceServer);
        registerForwardNoArgs(pulsoAddressSpace, "/musalce/surface/inventory/end",     toMusalceServer);
        registerForwardNoArgs(pulsoAddressSpace, "/musalce/surface/state_request",     toMusalceServer);
        registerForwardTwoStrings(pulsoAddressSpace, "/musalce/surface/trigger",       toMusalceServer);

        fromPulsoBridgeServer = osc.createUdpServer(pulsoAddressSpace);
        try {
            fromPulsoBridgeServer.start(pulsoListenPort);
            Controller.log.info("controller", "MusaLCESurfaceRelay listening on UDP " + pulsoListenPort
                    + ", sending to " + pulsoSendHost + ":" + pulsoSendPort);
        } catch (IOException e) {
            Controller.log.error("controller",
                    "Couldn't start MusaLCESurfaceRelay server on UDP " + pulsoListenPort
                            + ": " + e.getLocalizedMessage());
        }
    }

    private void registerForwardNoArgs(OscAddressSpace space, String address, OscConnection target) {
        space.registerMethod(address, "", "Forward " + address,
                (source, message) -> {
                    try {
                        target.sendMessage(address);
                    } catch (OscInvalidArgumentTypeException | IOException e) {
                        Controller.log.error("controller",
                                "Error forwarding " + address + ": " + e.getLocalizedMessage());
                    }
                });
    }

    private void registerForwardOneString(OscAddressSpace space, String address, OscConnection target) {
        space.registerMethod(address, "*", "Forward " + address,
                (source, message) -> {
                    try {
                        target.sendMessage(address, message.getString(0));
                    } catch (OscInvalidArgumentTypeException | IOException e) {
                        Controller.log.error("controller",
                                "Error forwarding " + address + ": " + e.getLocalizedMessage());
                    }
                });
    }

    private void registerForwardTwoStrings(OscAddressSpace space, String address, OscConnection target) {
        space.registerMethod(address, "*", "Forward " + address,
                (source, message) -> {
                    try {
                        target.sendMessage(address, message.getString(0), message.getString(1));
                    } catch (OscInvalidArgumentTypeException | IOException e) {
                        Controller.log.error("controller",
                                "Error forwarding " + address + ": " + e.getLocalizedMessage());
                    }
                });
    }

    private void registerForwardThreeStrings(OscAddressSpace space, String address, OscConnection target) {
        space.registerMethod(address, "*", "Forward " + address,
                (source, message) -> {
                    try {
                        target.sendMessage(address,
                                message.getString(0),
                                message.getString(1),
                                message.getString(2));
                    } catch (OscInvalidArgumentTypeException | IOException e) {
                        Controller.log.error("controller",
                                "Error forwarding " + address + ": " + e.getLocalizedMessage());
                    }
                });
    }

    public void unlink() {
        toPulsoBridge = null;
        fromPulsoBridgeServer = null;
    }
}
