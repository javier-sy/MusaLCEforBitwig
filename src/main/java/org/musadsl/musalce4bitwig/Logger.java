package org.musadsl.musalce4bitwig;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

import com.bitwig.extension.controller.api.ControllerHost;

public class Logger {
	private ControllerHost host = null;
	private FileWriter logFileWriter = null;
	
	public Logger(ControllerHost host) {
		this.host = host;
		
		String defaultBasePath = System.getProperty("java.io.tmpdir");
		
		try {
			logFileWriter = createOrGetFile(defaultBasePath, "musalceserver.log");
		} catch(IOException e) {
			error("logger", e.getMessage());
		}
	}
	
	public void info(String module, String s) {
		String ss = "[" + module + "] " + s;
		host.println(ss);
		try {
			logFileWriter.write(ss + '\n');
			logFileWriter.flush();
		} catch(IOException e) { }
	}
	
	public void error(String module, String s ) {
		String ss = "[" + module + "] " + s;
		host.errorln(ss);
		try {
			logFileWriter.write(ss + '\n');
			logFileWriter.flush();
		} catch(IOException e) { }
	}
	
	private FileWriter createOrGetFile(String folder, String name) throws IOException {
		  File file = new File(folder, name);
		  
		  if (!file.exists()) {
		    Files.createFile(file.toPath());
		  }
		  
		  host.println("Created log file on " + file.getAbsolutePath());
		  
		  FileWriter fr = new FileWriter(file);
		  return fr;
		}
}
