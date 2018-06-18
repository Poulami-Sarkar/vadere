package org.vadere.gui.postvisualization.control;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.vadere.gui.components.utils.Resources;
import org.vadere.gui.postvisualization.PostVisualisation;
import org.vadere.gui.postvisualization.utils.SVGGenerator;
import org.vadere.gui.postvisualization.view.PostvisualizationRenderer;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.prefs.Preferences;

public class ActionGenerateTikZ extends ActionVisualization {
	private static Logger logger = LogManager.getLogger(ActionGenerateTikZ.class);
	private static Resources resources = Resources.getInstance("postvisualization");
	private final SVGGenerator svgGenerator;

	public ActionGenerateTikZ(final String name, final Icon icon, final PostvisualizationRenderer renderer) {
		super(name, icon, renderer.getModel());
		this.svgGenerator = new SVGGenerator(renderer, renderer.getModel());
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		Date todaysDate = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat(resources.getProperty("View.dataFormat"));
		String formattedDate = formatter.format(todaysDate);

		JFileChooser fileChooser = new JFileChooser(Preferences.userNodeForPackage(PostVisualisation.class).get("PostVis.snapshotDirectory.path", "."));
		File outputFile = new File("pv_snapshot_" + formattedDate + ".svg");

		fileChooser.setSelectedFile(outputFile);

		int returnVal = fileChooser.showDialog(null, "Save");

		if (returnVal == JFileChooser.APPROVE_OPTION) {

			outputFile = fileChooser.getSelectedFile().toString().endsWith(".svg") ? fileChooser.getSelectedFile()
					: new File(fileChooser.getSelectedFile().toString() + ".svg");
			// TODO: add code to generate TikZ code (get topography from "renderer").
			svgGenerator.generateSVG(outputFile);
		}
	}
}
