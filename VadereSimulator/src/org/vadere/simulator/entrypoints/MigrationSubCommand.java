package org.vadere.simulator.entrypoints;

import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

import org.apache.log4j.Logger;
import org.vadere.simulator.projects.migration.MigrationAssistant;
import org.vadere.simulator.projects.migration.MigrationException;
import org.vadere.simulator.projects.migration.MigrationOptions;

import java.nio.file.Path;
import java.nio.file.Paths;

public class MigrationSubCommand implements  SubCommandRunner{
	private final static Logger logger = Logger.getLogger(MigrationSubCommand.class);
	@Override
	public void run(Namespace ns, ArgumentParser parser) throws Exception {
		Path scenarioFile = Paths.get(ns.getString("scenario-file"));
		if (!scenarioFile.toFile().exists() || !scenarioFile.toFile().isFile()){
			logger.error("scenario-file does not exist, is not a regular file or you do not have read permissions:" +
					scenarioFile.toFile().toString());
			System.exit(-1);
		}

		String outputPathString = ns.getString("output-file");
		Version targetVersion = Version.fromString(ns.getString("target-version"));
		boolean revertMode = ns.getBoolean("revert-migration");

		if (revertMode){
			revert(scenarioFile);
		} else {
			migrate(scenarioFile, targetVersion, outputPathString);
		}

	}

	private void revert(Path scenarioFile) throws MigrationException {
		MigrationAssistant ma = MigrationAssistant.getNewInstance(MigrationOptions.defaultOptions());
		ma.revertFile(scenarioFile);
	}

	private void migrate(Path scenarioFile, Version targetVersion, String outputPathString){
		MigrationAssistant ma = MigrationAssistant.getNewInstance(MigrationOptions.defaultOptions());
		String out;
		try{
			logger.info("Scenario file" + scenarioFile.getFileName().toString());
			logger.info("Try to migrate to version " + targetVersion);
			Path outputFile = null;
			if (outputPathString != null){
				outputFile = Paths.get(outputPathString);
			}

			ma.migrateFile(scenarioFile, targetVersion, outputFile);


		} catch (Exception e) {
			logger.error("Migration failed", e);
		}
	}

	private Path addSuffix(Path p, String suffix){
		Path abs = p.toAbsolutePath();
		String filename = abs.getFileName().toString() + suffix;
		return  abs.getParent().resolve(filename);
	}
}
