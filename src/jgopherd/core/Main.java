package jgopherd.core;

import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

/**
 * Main server class.
 * 
 * @author Richard
 */
public class Main {
	public static final String VERSION = "0.2";
	public static final String CONFIG_FILE = "jgopherd.cfg";
	public static final String LOG_FILE = "jgopherd.log";
	
	public static Logger log = Logger.getLogger("jgopherd");
	public static Configuration config;
	public static ListenerThread listener;
	
	public static boolean serverStopped = false;
	public static boolean executeSupported = true;
	
	/**
	 * Server entry point
	 * 
	 * @param args Arguments passed to the server
	 */
	public static void main(String[] args) throws Throwable {
		Handler handler;
		log.setUseParentHandlers(false);
		log.addHandler(handler = new ConsoleHandler());
		LogFormatter formatter = new LogFormatter();
		handler.setFormatter(formatter);
		StreamHandler fileHandler;
		log.addHandler(fileHandler = new StreamHandler(new FileOutputStream(LOG_FILE), formatter));
		
		log.log(Level.INFO, "jgopherd version " + VERSION + " starting");
		
		executeSupported = !System.getProperty("os.name").contains("Windows");
		
		log.log(Level.INFO, "Reading configuration...");
		config = new Configuration();
		try {
			config.load(new FileReader(CONFIG_FILE));
		} catch (Throwable e) {
			log.log(Level.INFO, "Configuration file failed to load - generating a new one: " + e);
		}
		config.loadFromProperties();
		
		config.saveToProperties();
		try {
			config.store(new FileWriter(CONFIG_FILE), "jgopherd configuration file");
		} catch (Throwable e) {
			log.log(Level.SEVERE, "Configuration file failed to save!");
			e.printStackTrace();
		}
		
		if (config.printRequests) log.setLevel(Level.FINE);
		
		Thread thread = new Thread(listener = new ListenerThread(), "jgopherd listener");
		thread.start();
		
		while (true) {
			fileHandler.flush();
			Thread.sleep(1000L);
		}
	}
}
