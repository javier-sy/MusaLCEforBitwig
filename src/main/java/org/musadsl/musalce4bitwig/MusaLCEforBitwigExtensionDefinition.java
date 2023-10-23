package org.musadsl.musalce4bitwig;

import java.util.UUID;

import com.bitwig.extension.api.PlatformType;
import com.bitwig.extension.controller.AutoDetectionMidiPortNamesList;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ControllerHost;

public class MusaLCEforBitwigExtensionDefinition extends ControllerExtensionDefinition
{
   private static final UUID DRIVER_ID = UUID.fromString("2ec5ae8f-9196-4119-ac9d-42eb7c1d5329");
   
   public MusaLCEforBitwigExtensionDefinition()
   {
   }

   @Override
   public String getName()
   {
      return "MusaLCE";
   }
   
   @Override
   public String getAuthor()
   {
      return "yeste.studio";
   }

   @Override
   public String getVersion()
   {
      return "0.2";
   }

   @Override
   public UUID getId()
   {
      return DRIVER_ID;
   }
   
   @Override
   public String getHardwareVendor()
   {
      return "MusaDSL";
   }
   
   @Override
   public String getHardwareModel()
   {
      return "MusaLCE";
   }

   @Override
   public int getRequiredAPIVersion()
   {
      return 18;
   }

   @Override
   public int getNumMidiInPorts()
   {
      return 1;
   }

   @Override
   public int getNumMidiOutPorts()
   {
      return 0;
   }

   @Override
   public void listAutoDetectionMidiPortNames(final AutoDetectionMidiPortNamesList list, final PlatformType platformType)
   {
   }

   @Override
   public Controller createInstance(final ControllerHost host)
   {
      return new Controller(this, host);
   }
}
