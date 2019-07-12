package org.vadere.util.config;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.builder.fluent.PropertiesBuilderParameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.vadere.util.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A {@link VadereConfig} reads its options from a text file in "properties" style. I.e., simple key-value pairs
 * where keys can contain dots but no section dividers are allowed (for more details see
 * https://commons.apache.org/proper/commons-configuration/userguide/howto_properties.html#Properties_files).
 * {@link VadereConfig} uses the Apache Commons Configuration library to read such property files.
 *
 * A Vadere config file looks like this:
 *
 * <pre>
 * # PostVis
 * PostVis.SVGWidth=1024
 * PostVis.SVGHeight=768
 * </pre>
 *
 * This config object is used like this:
 *
 * <pre>int svgWidth = VadereConfig.getConfig().getInt("PostVis.SVGWidth");</pre>
 */
public class VadereConfig {

    // Static Variables
    private static final Logger LOGGER = Logger.getLogger(VadereConfig.class);

    private static final String DEFAULT_HOME_DIR = System.getProperty("user.home");
    private static final String DEFAULT_CONFIG_DIR = ".config";

    // Both variables must not be "final" so that we are able
    // to inject another config file from CLI argument "--config-file myconfig.conf".
    private static String CONFIG_FILENAME = "vadere.conf";
    private static Path CONFIG_PATH = Path.of(DEFAULT_HOME_DIR, DEFAULT_CONFIG_DIR, CONFIG_FILENAME);

    private static VadereConfig SINGLETON_INSTANCE;

    // Variables
    private FileBasedConfiguration vadereConfig;

    // Constructors
    private VadereConfig() {
        createDefaultConfigIfNonExisting();

        // If Vadere was started like "vadere-console.jar --config-file here.txt", search in current working directory.
        String basePath = (CONFIG_PATH.getParent() == null) ? System.getProperty("user.dir") : CONFIG_PATH.getParent().toString() ;

        PropertiesBuilderParameters propertiesParams = new Parameters()
                .properties()
                .setFileName(CONFIG_FILENAME)
                .setBasePath(basePath);

        FileBasedConfigurationBuilder<FileBasedConfiguration> builder =
                new FileBasedConfigurationBuilder<FileBasedConfiguration>(PropertiesConfiguration.class)
                .configure(propertiesParams);
        builder.setAutoSave(true);

        try {
            vadereConfig = builder.getConfiguration();
        } catch (ConfigurationException ex) {
            LOGGER.error(String.format("Error while reading config file \"%s\": %s", CONFIG_PATH.toString(), ex.getMessage()));
            LOGGER.info("Create and use default config");
        }
    }

    private void createDefaultConfigIfNonExisting() {
        try { // Ensure that config directory exists.
            Files.createDirectories(Path.of(DEFAULT_HOME_DIR, DEFAULT_CONFIG_DIR));
        } catch (IOException ex) {
            LOGGER.error(String.format("Cannot create directory: %s", Path.of(DEFAULT_HOME_DIR, DEFAULT_CONFIG_DIR)));
        }

        if (Files.exists(CONFIG_PATH) == false) {
            Map<String, String> defaultConfig = getDefaultConfig();

            try {
                LOGGER.info(String.format("Writing default config file: %s", CONFIG_PATH));

                Files.write(
                        CONFIG_PATH,
                        defaultConfig
                                .entrySet()
                                .stream()
                                .map(entry -> entry.getKey() + " = " + entry.getValue())
                                .sorted(String::compareTo)
                                .collect(Collectors.toList()));
            } catch (IOException e) {
                LOGGER.error(String.format("Error while writing default config file \"%s\": %s", CONFIG_PATH, e.getMessage()));
            }
        }
    }

    // Static Setters
    /**
     * With this setter one can inject a different config file instead of using "~/.config/vadere.conf".
     *
     * @param configPath Path to config file.
     */
    public static void setConfigPath(String configPath) {
        CONFIG_PATH = Path.of(configPath);
        CONFIG_FILENAME = CONFIG_PATH.getFileName().toString();
    }

    // Static Setters
    /**
     * Use Apache Common Configuration API on the returned object to retrieve Vadere's config options.
     *
     * See https://commons.apache.org/proper/commons-configuration/userguide/howto_properties.html#Properties_files
     *
     * @return A Configuration object from Apache Common Configuration library.
     */
    public static Configuration getConfig() {
        if (SINGLETON_INSTANCE == null) {
            SINGLETON_INSTANCE = new VadereConfig();
        }

        return SINGLETON_INSTANCE.vadereConfig;
    }

    // Methods

    private static Map<String, String> getDefaultConfig(){
        final Map<String, String> defaultConfig = new HashMap<>();

        String defaultSearchDirectory = System.getProperty("user.home");

        defaultConfig.put("Gui.dataProcessingViewMode", "gui");
        defaultConfig.put("Gui.toolbar.size", "40");
        defaultConfig.put("Gui.lastSavePoint", defaultSearchDirectory);
        defaultConfig.put("Density.measurementScale", "10.0");
        defaultConfig.put("Density.measurementRadius", "15");
        defaultConfig.put("Density.standardDeviation", "0.5");
        defaultConfig.put("Messages.language", Locale.ENGLISH.getLanguage());
        defaultConfig.put("Pedestrian.radius", "0.195");
        defaultConfig.put("PostVis.SVGWidth", "1024");
        defaultConfig.put("PostVis.SVGHeight", "768");
        defaultConfig.put("PostVis.maxNumberOfSaveDirectories", "5");
        defaultConfig.put("PostVis.maxFramePerSecond", "30");
        defaultConfig.put("PostVis.framesPerSecond", "5");
        defaultConfig.put("PostVis.cellWidth", "1.0");
        defaultConfig.put("PostVis.minCellWidth", "0.01");
        defaultConfig.put("PostVis.maxCellWidth", "10.0");
        defaultConfig.put("PostVis.enableJsonInformationPanel", "true");
        defaultConfig.put("ProjectView.icon.height.value", "35");
        defaultConfig.put("ProjectView.icon.width.value", "35");
        defaultConfig.put("ProjectView.cellWidth", "1.0");
        defaultConfig.put("ProjectView.minCellWidth", "0.01");
        defaultConfig.put("ProjectView.maxCellWidth", "10.0");
        defaultConfig.put("ProjectView.defaultDirectory", defaultSearchDirectory);
        defaultConfig.put("ProjectView.defaultDirectoryAttributes", defaultSearchDirectory);
        defaultConfig.put("ProjectView.defaultDirectoryScenarios", defaultSearchDirectory);
        defaultConfig.put("SettingsDialog.maxNumberOfTargets", "10");
        defaultConfig.put("SettingsDialog.dataFormat", "yyyy_MM_dd_HH_mm_ss");
        defaultConfig.put("SettingsDialog.outputDirectory.path", ".");
        defaultConfig.put("SettingsDialog.snapshotDirectory.path", ".");
        defaultConfig.put("SettingsDialog.showLogo", "false");
        defaultConfig.put("TopographyCreator.dotRadius", "0.5");

        return defaultConfig;
    }
}
