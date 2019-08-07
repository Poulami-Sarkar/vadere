package org.vadere.manager;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.internal.HelpScreenException;

import org.vadere.util.logging.Logger;

import java.net.ServerSocket;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Manager {

	static Logger logger;

	public static void main(String[] args) {
		Logger.setMainArguments(args);
		logger = Logger.getLogger(Manager.class);
		ArgumentParser p = createArgumentParser();
		Namespace ns;

		try {
			ns = p.parseArgs(args);
			ExecutorService pool = Executors.newFixedThreadPool(ns.getInt("clientNum"));
			ServerSocket serverSocket = new ServerSocket(ns.getInt("port"));
			logger.infof("Start Server(%s) with Loglevel: %s", VadereServer.currentVersion.getVersionString(), logger.getLevel().toString());
			VadereServer server = new VadereServer(serverSocket, pool, Paths.get(ns.getString("output-dir")), ns.getBoolean("guiMode"));
			server.run();

		} catch (HelpScreenException ignored) {

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static ArgumentParser createArgumentParser() {
		ArgumentParser parser = ArgumentParsers.newArgumentParser("Vadere Server")
				.defaultHelp(true)
				.description("Runs the VADERE pedestrian simulator as a server.");

		addOptionsToParser(parser);

		return parser;
	}

	private static void addOptionsToParser(ArgumentParser parser) {
		// no action required call to  Logger.setMainArguments(args) already configured Logger.
		parser.addArgument("--loglevel")
				.required(false)
				.type(String.class)
				.dest("loglevel")
				.choices("OFF", "FATAL", "ERROR", "WARN", "INFO", "DEBUG", "TRACE", "ALL")
				.setDefault("INFO")
				.help("Set Log Level.");

		// no action required call to  Logger.setMainArguments(args) already configured Logger.
		parser.addArgument("--logname")
				.required(false)
				.type(String.class)
				.dest("logname")
				.help("Write log to given file.");


		// no action required call to  Logger.setMainArguments(args) already configured Logger.
		parser.addArgument("--port")
				.required(false)
				.type(Integer.class)
				.setDefault(9999)
				.dest("port")
				.help("Set port number.");

		// no action required call to  Logger.setMainArguments(args) already configured Logger.
		parser.addArgument("--clientNum")
				.required(false)
				.type(Integer.class)
				.setDefault(4)
				.dest("clientNum")
				.help("Set number of clients to manager. Important: Each client has a separate simulation. No communication between clients");

		// boolean switch to tell server to start in gui mode.
		parser.addArgument("--gui-mode")
				.required(false)
				.action(Arguments.storeTrue())
				.type(Boolean.class)
				.dest("guiMode")
				.help("Start server with GUI support. If a scenario is received show the current state of the scenario");

		parser.addArgument("--output-dir", "-o")
				.required(false)
				.setDefault("./vadere-server-output")
				.dest("output-dir") // set name in namespace
				.type(String.class)
				.help("Supply output directory as base directory for received scenarios.");
	}
}
