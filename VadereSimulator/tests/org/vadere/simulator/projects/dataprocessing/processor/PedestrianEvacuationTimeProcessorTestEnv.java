package org.vadere.simulator.projects.dataprocessing.processor;

import org.mockito.Mockito;
import org.vadere.simulator.projects.dataprocessing.datakey.PedestrianIdKey;
import org.vadere.simulator.projects.dataprocessing.writer.VadereWriterFactory;
import org.vadere.state.attributes.processor.AttributesPedestrianEvacuationTimeProcessor;
import org.vadere.state.scenario.Pedestrian;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import static org.mockito.Mockito.when;

public class PedestrianEvacuationTimeProcessorTestEnv extends ProcessorTestEnv<PedestrianIdKey, Double> {

	PedestrianListBuilder b = new PedestrianListBuilder();

	PedestrianEvacuationTimeProcessorTestEnv(){this(1);}

	PedestrianEvacuationTimeProcessorTestEnv(int nextProcesorId) {
		try {
			testedProcessor = processorFactory.createDataProcessor(PedestrianEvacuationTimeProcessor.class);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		testedProcessor.setId(nextProcesorId);
		this.nextProcessorId = nextProcesorId + 1;

		DataProcessor pedStartTimeProc;
		PedestrianStartTimeProcessorTestEnv pedStartTimeProcEnv;
		int pedStartTimeProcId = nextProcessorId();

		//add ProcessorId of required Processors to current Processor under test
		AttributesPedestrianEvacuationTimeProcessor attr =
				(AttributesPedestrianEvacuationTimeProcessor) testedProcessor.getAttributes();
		attr.setPedestrianStartTimeProcessorId(pedStartTimeProcId);

		//create required Processor enviroment and add it to current Processor under test
		pedStartTimeProcEnv = new PedestrianStartTimeProcessorTestEnv(pedStartTimeProcId);
		pedStartTimeProc = pedStartTimeProcEnv.getTestedProcessor();
		Mockito.when(manager.getProcessor(pedStartTimeProcId)).thenReturn(pedStartTimeProc);
		addRequiredProcessors(pedStartTimeProcEnv);

		//setup output file with different VadereWriter impl for test
		try {
			outputFile = outputFileFactory.createDefaultOutputfileByDataKey(
					PedestrianIdKey.class,
					testedProcessor.getId()
			);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		outputFile.setVadereWriterFactory(VadereWriterFactory.getStringWriterFactory());

	}

	@Override
	public void loadDefaultSimulationStateMocks() {
		addSimState(new SimulationStateMock(1) {
			@Override
			public void mockIt() {

				b.clear().add(new Integer[]{1, 3, 4});

				when(state.getTopography().getElements(Pedestrian.class)).thenReturn(b.getList());
				when(state.getSimTimeInSec()).thenReturn(0.4);

				addToExpectedOutput(new PedestrianIdKey(1), 0.0);
				addToExpectedOutput(new PedestrianIdKey(3), 0.0);
				addToExpectedOutput(new PedestrianIdKey(4), 0.0);

			}
		});

		addSimState(new SimulationStateMock(2) {
			@Override
			public void mockIt() {

				b.clear().add(new Integer[]{1, 3, 4, 5, 8});

				when(state.getTopography().getElements(Pedestrian.class)).thenReturn(b.getList());
				when(state.getSimTimeInSec()).thenReturn(12.8);

				addToExpectedOutput(new PedestrianIdKey(1), 12.8 - 0.4);
				addToExpectedOutput(new PedestrianIdKey(3), 12.8 - 0.4);
				addToExpectedOutput(new PedestrianIdKey(4), 12.8 - 0.4);
				addToExpectedOutput(new PedestrianIdKey(5), 0.0);
				addToExpectedOutput(new PedestrianIdKey(8), 0.0);

			}
		});

		addSimState(new SimulationStateMock(3) {
			@Override
			public void mockIt() {

				b.clear().add(new Integer[]{1, 5, 8});

				when(state.getTopography().getElements(Pedestrian.class)).thenReturn(b.getList());
				when(state.getSimTimeInSec()).thenReturn(34.7);

				addToExpectedOutput(new PedestrianIdKey(1), 34.7 - 0.0);
				addToExpectedOutput(new PedestrianIdKey(5), 34.7 - 12.8);
				addToExpectedOutput(new PedestrianIdKey(8), 34.7 - 12.8);

			}
		});


		addSimState(new SimulationStateMock(4) {
			@Override
			public void mockIt() {

				b.clear().add(new Integer[]{1});

				when(state.getTopography().getElements(Pedestrian.class)).thenReturn(b.getList());
				when(state.getSimTimeInSec()).thenReturn(40.0);

				addToExpectedOutput(new PedestrianIdKey(1), Double.POSITIVE_INFINITY);

			}
		});
	}

	@Override
	List<String> getExpectedOutputAsList() {
		List<String> outputList = new ArrayList<>();
		expectedOutput.entrySet()
				.stream()
				.sorted(Comparator.comparing(Map.Entry::getKey))
				.forEach(e -> {
					StringJoiner sj = new StringJoiner(getDelimiter());
					sj.add(Integer.toString(e.getKey().getPedestrianId()))
							.add(Double.toString(e.getValue()));
					outputList.add(sj.toString());
				});
		return outputList;
	}
}
