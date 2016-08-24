package org.vadere.simulator.projects.dataprocessing_mtp;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

public abstract class LogFile<K extends Comparable<K>> {
	private String[] keyHeaders;
	private String fileName;

	private List<Integer> processorIds;
	private List<Processor<K, ?>> processors;

    private static Character SEPARATOR = ' ';

	LogFile(final String... keyHeaders) {
		this.keyHeaders = keyHeaders;
		this.processors = new ArrayList<>();
	}

	public void setFileName(final String fileName) {
		this.fileName = fileName;
	}

	public void setProcessorIds(final List<Integer> processorIds) {
		this.processorIds = processorIds;
		this.processors.clear();
	}

	public void init(final ProcessorManager manager) {
		processorIds.forEach(pid -> this.processors.add((Processor<K, ?>) manager.getProcessor(pid)));
	}

	public void write() {
	    try {
            File file = new File(this.fileName);

            if (!file.exists())
                file.createNewFile();

            try (PrintWriter out = new PrintWriter(new FileWriter(file))) {
                // Print header
                out.println(StringUtils.substringBeforeLast(
                		(this.keyHeaders == null || this.keyHeaders.length == 0
							? ""
							: String.join(SEPARATOR.toString(), this.keyHeaders) + SEPARATOR)
						+ this.processors.stream().map(p -> String.join(SEPARATOR.toString(), p.getHeaders()) + SEPARATOR).reduce("", (s1, s2) -> s1 + s2), SEPARATOR.toString()));

                this.processors.stream().flatMap(p -> p.getKeys().stream()).distinct().sorted()
                        .forEach(key -> printRow(out, key, this.processors));

                out.flush();
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
	}

	private void printRow(PrintWriter out, final K key, final List<Processor<K, ?>> ps) {
		out.println(StringUtils.substringBeforeLast(
				(this.toString() == null || this.toStrings(key).length == 0
					? ""
					: String.join(SEPARATOR.toString(), String.join(SEPARATOR.toString(), this.toStrings(key)) + SEPARATOR))
				+ ps.stream().map(p -> String.join(SEPARATOR.toString(), p.toStrings(key)) + SEPARATOR).reduce("", (s1, s2) -> s1 + s2), SEPARATOR.toString()));
	}

	public String[] toStrings(K key) {
		return new String[] { key.toString() };
	}

	public String getFileName() {
		return fileName;
	}

	public List<Integer> getProcessorIds() {
		return processorIds;
	}
}
