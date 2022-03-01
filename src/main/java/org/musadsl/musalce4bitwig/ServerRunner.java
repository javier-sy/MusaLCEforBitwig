package org.musadsl.musalce4bitwig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ServerRunner {

	private Process process = null;
	
	public boolean isRunning() {
		return process != null;
	}
	
	public void run() {
		if(process == null) {
			ProcessBuilder builder = new ProcessBuilder();
			
			// TODO Generalize to different shells (maybe bash don't use --login) and operating systems (windows, linux)
			
			builder.command(System.getenv("SHELL"), "--login", "-c", "musalce-server bitwig");
			
			try {
				process = builder.start();
				
				Controller.log.info("server", "Launched server");
				
				StreamGobbler streamGobblerStdout = 
					new StreamGobbler(process.getInputStream(),
						t -> {
							Controller.log.info("server", t);
						});
				
				StreamGobbler streamGobblerStderr = 
						new StreamGobbler(process.getErrorStream(),
							t -> {
								Controller.log.info("server", t);
							});

				Executors.newSingleThreadExecutor().submit(streamGobblerStdout);
				Executors.newSingleThreadExecutor().submit(streamGobblerStderr);

			} catch (IOException e) {
				Controller.log.error("server", "Error launching server: " + e.getLocalizedMessage());
			}
		}
	}
	
	public void kill() {
		if(process != null) {
			if(process.supportsNormalTermination()) {
				try {
					process.getInputStream().close();
				} catch (IOException e) {
					Controller.log.error("server", "Closing server process: " + e.getLocalizedMessage());
				}
				try {
					process.getErrorStream().close();
				} catch (IOException e) {
					Controller.log.error("server", "Closing server process: " + e.getLocalizedMessage());
				}

				process.destroy();
				
				try {
					process.waitFor(5, TimeUnit.SECONDS);
				} catch (InterruptedException e) { }
				
				process.destroyForcibly();
				
				Controller.log.info("server", "Server shutdown");
			} else {
				process.destroyForcibly();
				Controller.log.error("server", "Forced server shutdown");
			}
			
		}
		process = null;
	}
	
	private static class StreamGobbler implements Runnable {
	    private InputStream inputStream;
	    private Consumer<String> consumer;

	    public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
	        this.inputStream = inputStream;
	        this.consumer = consumer;
	    }

	    @Override
	    public void run() {
	        new BufferedReader(new InputStreamReader(inputStream)).lines()
	          .forEach(consumer);
	    }
	}
}
