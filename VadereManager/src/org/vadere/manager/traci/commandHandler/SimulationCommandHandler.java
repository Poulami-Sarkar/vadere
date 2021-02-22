package org.vadere.manager.traci.commandHandler;

import org.apache.commons.math3.util.Pair;
import org.vadere.annotation.traci.client.TraCIApi;
import org.vadere.manager.RemoteManager;
import org.vadere.manager.TraCICommandCreationException;
import org.vadere.manager.TraCIException;
import org.vadere.manager.traci.TraCICmd;
import org.vadere.manager.traci.TraCIDataType;
import org.vadere.manager.traci.commandHandler.annotation.SimulationHandler;
import org.vadere.manager.traci.commandHandler.annotation.SimulationHandlers;
import org.vadere.manager.traci.commandHandler.variables.SimulationVar;
import org.vadere.manager.traci.commands.TraCICommand;
import org.vadere.manager.traci.commands.TraCIGetCommand;
import org.vadere.manager.traci.commands.TraCISetCommand;
import org.vadere.manager.traci.commands.get.TraCIGetCacheHashCommand;
import org.vadere.manager.traci.commands.get.TraCIGetCompoundPayload;
import org.vadere.manager.traci.compound.CompoundObject;
import org.vadere.manager.traci.compound.object.CoordRef;
import org.vadere.manager.traci.compound.object.PointConverter;
import org.vadere.manager.traci.compound.object.SimulationCfg;
import org.vadere.manager.traci.response.TraCIGetResponse;
import org.vadere.simulator.entrypoints.ScenarioFactory;
import org.vadere.simulator.projects.Scenario;
import org.vadere.simulator.utils.cache.ScenarioCache;
import org.vadere.state.scenario.Agent;
import org.vadere.state.scenario.ReferenceCoordinateSystem;
import org.vadere.util.geometry.shapes.VPoint;
import org.vadere.util.logging.Logger;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handel GET/SET/SUB {@link org.vadere.manager.traci.commands.TraCICommand}s for the Simulation
 * API
 */
@TraCIApi(
		name = "SimulationAPI",
		nameShort = "sim",
		singleAnnotation = SimulationHandler.class,
		multipleAnnotation = SimulationHandlers.class,
		cmdEnum = TraCICmd.class,
		varEnum = SimulationVar.class,
		var = "V_SIM",
		cmdGet = 0xab,
		cmdSet = 0xcb,
		cmdSub = 0xdb,
		cmdResponseSub = 0xeb,
		cmdCtx = 0x8b,
		cmdResponseCtx = 0x9b
)
public class SimulationCommandHandler extends CommandHandler<SimulationVar> {

	public static SimulationCommandHandler instance;
	private static Logger logger = Logger.getLogger(SimulationCommandHandler.class);
	private Pair<Double, Set<Integer>> allPrevious; // time at witch the given ids were send over traci
	private Pair<Double, List<String>> departedCache; // time at witch the given ids were send over traci
	private Pair<Double, List<String>> arrivedCache; // time at witch the given ids were send over traci

	static {
		instance = new SimulationCommandHandler();
	}

	private SimulationCommandHandler() {
		super();
		init(SimulationHandler.class, SimulationHandlers.class);
		allPrevious = Pair.create(-1.0, new HashSet<>()); // never called.
		departedCache = Pair.create(-1.0, new ArrayList<>());
		arrivedCache = Pair.create(-1.0, new ArrayList<>());
	}

	@Override
	protected void init_HandlerSingle(Method m) {
		SimulationHandler an = m.getAnnotation(SimulationHandler.class);
		putHandler(an.cmd(), an.var(), m);
	}

	@Override
	protected void init_HandlerMult(Method m) {
		SimulationHandler[] ans = m.getAnnotation(SimulationHandlers.class).value();
		for (SimulationHandler a : ans) {
			putHandler(a.cmd(), a.var(), m);
		}
	}

	public TraCIGetResponse responseOK(TraCIDataType responseDataType, Object responseData) {
		return responseOK(responseDataType, responseData, TraCICmd.GET_SIMULATION_VALUE, TraCICmd.RESPONSE_GET_SIMULATION_VALUE);
	}

	public TraCIGetResponse responseERR(TraCIDataType responseDataType, Object responseData) {
		return responseOK(responseDataType, responseData, TraCICmd.GET_SIMULATION_VALUE, TraCICmd.RESPONSE_GET_SIMULATION_VALUE);
	}

	public TraCIGetResponse responseERR(SimulationVar var, String err) {
		return responseERR("[" + var.toString() + "] " + err, TraCICmd.GET_SIMULATION_VALUE, TraCICmd.RESPONSE_GET_SIMULATION_VALUE);
	}


	@SimulationHandler(cmd = TraCICmd.GET_SIMULATION_VALUE, var = SimulationVar.NET_BOUNDING_BOX,
			name = "getNetworkBound", ignoreElementId = true)
	public TraCICommand process_getNetworkBound(TraCIGetCommand cmd, RemoteManager remoteManager) {

		remoteManager.accessState((manager, state) -> {
			Rectangle2D.Double rec = state.getTopography().getBounds();

			VPoint lowLeft = new VPoint(rec.getMinX(), rec.getMinY());
			VPoint highRight = new VPoint(rec.getMaxX(), rec.getMaxY());
			ArrayList<VPoint> polyList = new ArrayList<>();
			polyList.add(lowLeft);
			polyList.add(highRight);
			cmd.setResponse(responseOK(SimulationVar.NET_BOUNDING_BOX.type, polyList));
		});

		return cmd;
	}

	@SimulationHandler(cmd = TraCICmd.GET_SIMULATION_VALUE, var = SimulationVar.TIME,
			name = "getTime", ignoreElementId = true)
	public TraCICommand process_getSimTime(TraCIGetCommand cmd, RemoteManager remoteManager) {

		remoteManager.accessState((manager, state) -> {
			// BigDecimal to ensure correct comparison in omentpp
			BigDecimal time = BigDecimal.valueOf(state.getSimTimeInSec());
			cmd.setResponse(responseOK(SimulationVar.TIME.type, time.setScale(1, RoundingMode.HALF_UP).doubleValue()));
		});

		return cmd;
	}

	@SimulationHandler(cmd = TraCICmd.GET_SIMULATION_VALUE, var = SimulationVar.DELTA_T,
			name = "getSimSte", ignoreElementId = true)
	public TraCICommand process_getSimStep(TraCIGetCommand cmd, RemoteManager remoteManager) {

		remoteManager.accessState((manager, state) -> {
			// BigDecimal to ensure correct comparison in omentpp
			double time = state.getScenarioStore().getAttributesSimulation().getSimTimeStepLength();
			cmd.setResponse(responseOK(SimulationVar.TIME.type, time));
		});

		return cmd;
	}


	@SimulationHandler(cmd = TraCICmd.SET_SIMULATION_STATE, var = SimulationVar.SIM_CONFIG,
			name = "setSimConfig", ignoreElementId = true)
	public TraCICommand process_setSimConfig(TraCISetCommand cmd, RemoteManager remoteManager) {
		try {
			SimulationCfg cfg = new SimulationCfg((CompoundObject) cmd.getVariableValue());
			remoteManager.setSimCfg(cfg);
			logger.infof("Received SimulationConfig:");
			logger.infof(cfg.toString());
			cmd.setOK();

		} catch (TraCIException ex) {
			logger.errorf("cannot parse setSimConfig object. Err: %s", ex.getMessage());
			cmd.setErr(String.format("cannot parse setSimConfig object. Err: %s", ex.getMessage()));
		}
		return cmd;
	}

	@SimulationHandler(cmd = TraCICmd.GET_SIMULATION_VALUE, var = SimulationVar.CACHE_HASH,
			name = "getHash", dataTypeStr = "String", ignoreElementId = true)
	public TraCICommand process_getCacheHash(TraCIGetCommand rawCmd, RemoteManager remoteManager) {

		try {
			TraCIGetCacheHashCommand cmd = TraCIGetCacheHashCommand.create(rawCmd);
			if (cmd.getFile().length() > 0) {
				try {
					Scenario scenario = ScenarioFactory.createScenarioWithScenarioJson(cmd.getFile());
					String hash = ScenarioCache.getHash(scenario);
					cmd.setResponse(responseOK(SimulationVar.CACHE_HASH.type, hash));
				} catch (IOException e) {
					cmd.setResponse(responseERR(SimulationVar.CACHE_HASH, "cannot read scenario"));
				}
			}
			return cmd;

		} catch (TraCICommandCreationException ee) {
			rawCmd.setResponse(responseERR(SimulationVar.CACHE_HASH, "Ill formatted TraCIGetCacheHashCommand"));
		}
		return rawCmd;
	}

	@SimulationHandler(cmd = TraCICmd.GET_SIMULATION_VALUE, var = SimulationVar.DEPARTED_PEDESTRIAN_IDS,
			name = "getDepartedPedestrianId", dataTypeStr = "ArrayList<String>", ignoreElementId = true)
	public TraCICommand process_getDepartedPedestrianIds(TraCIGetCommand cmd, RemoteManager remoteManager) {

		calcArrivedDeparted(remoteManager);
		cmd.setResponse(responseOK(SimulationVar.DEPARTED_PEDESTRIAN_IDS.type, departedCache.getSecond()));
		return cmd;
	}


	@SimulationHandler(cmd = TraCICmd.GET_SIMULATION_VALUE, var = SimulationVar.ARRIVED_PEDESTRIAN_PEDESTRIAN_IDS,
			name = "getArrivedPedestrianIds", dataTypeStr = "ArrayList<String>", ignoreElementId = true)
	public TraCICommand process_getArrivedPedestrianIds(TraCIGetCommand cmd, RemoteManager remoteManager) {

		calcArrivedDeparted(remoteManager);
		cmd.setResponse(responseOK(SimulationVar.DEPARTED_PEDESTRIAN_IDS.type, arrivedCache.getSecond()));
		return cmd;
	}

	public void  calcArrivedDeparted(RemoteManager remoteManager){

		remoteManager.accessState((manager, state) -> {
			if (allPrevious.getFirst().equals(state.getSimTimeInSec())){
				// do nothing everything up to date. (called multiple times in one timeStep.)
			}else {
				// ped that were there but are not now
				Set<Integer> all_now = state.getTopography().getPedestrianDynamicElements().getElements()
						.stream()
						.map(Agent::getId)
						.collect(Collectors.toSet());
				Set<Integer> departed = new HashSet<>(all_now); // copy
				departed.removeAll(allPrevious.getSecond()); // departed (newly created): Ids which were ARE present at time t but were not at time t-1
				Set<Integer> arrived = new HashSet<>(allPrevious.getSecond()); // copy
				arrived.removeAll(all_now); // arrived (removed in this timestep): Ids which are NOT present at time t but were at time t-1
				allPrevious = Pair.create(state.getSimTimeInSec(), all_now);
				departedCache = Pair.create(state.getSimTimeInSec(), departed.stream()
						.map(i -> Integer.toString(i))
						.collect(Collectors.toList()));
				arrivedCache = Pair.create(state.getSimTimeInSec(), arrived.stream()
						.map(i->Integer.toString(i))
						.collect(Collectors.toList()));
			}
		});
	}

	@SimulationHandler(cmd = TraCICmd.GET_SIMULATION_VALUE, var = SimulationVar.POSITION_CONVERSION,
			name = "getPositionConversion", dataTypeStr = "ArrayList<String>", ignoreElementId = true)
	public TraCICommand process_PostionConversion(TraCIGetCommand cmd, RemoteManager remoteManager) {
		TraCIGetCompoundPayload pCmd = new TraCIGetCompoundPayload(cmd);
		PointConverter pointConverter = new PointConverter(pCmd.getData());
		remoteManager.accessState((manager, state) -> {
			ReferenceCoordinateSystem coord =
					state.getScenarioStore().getTopography()
							.getAttributes().getReferenceCoordinateSystem();
			coord.initialize();
			if (!coord.supportsConversion()){
				cmd.setResponse(responseERR("Conversion not supported. Is ReferenceCoordinateSystem correctly set in Vadere?",
						TraCICmd.GET_SIMULATION_VALUE, TraCICmd.RESPONSE_GET_SIMULATION_VALUE));
			} else {
				Pair<TraCIDataType, VPoint> p = pointConverter.convert(coord);
				cmd.setResponse(responseOK(p.getFirst(), p.getSecond()));
			}
		});

		return cmd;
	}


	@SimulationHandler(cmd = TraCICmd.GET_SIMULATION_VALUE, var = SimulationVar.COORD_REF,
			name = "getCoordinateReference", dataTypeStr = "ArrayList<String>", ignoreElementId = true)
	public TraCICommand process_GetCoordinateReference(TraCIGetCommand cmd, RemoteManager remoteManager){
		remoteManager.accessState((manager, state) -> {
			ReferenceCoordinateSystem coord =
					state.getScenarioStore().getTopography()
							.getAttributes().getReferenceCoordinateSystem();
			coord.initialize();
			CompoundObject o = CoordRef.asCompoundObject(
					coord.getEpsgCode(),
					coord.getTranslation()
			);
			cmd.setResponse(responseOK(SimulationVar.COORD_REF.type, o));
		});
		return cmd;
	}


	public TraCICommand processValueSub(TraCICommand rawCmd, RemoteManager remoteManager) {
		return processValueSub(rawCmd, remoteManager, this::processGet,
				TraCICmd.GET_SIMULATION_VALUE, TraCICmd.RESPONSE_SUB_SIMULATION_VALUE);
	}


	public TraCICommand processGet(TraCICommand rawCmd, RemoteManager remoteManager) {

		TraCIGetCommand cmd = (TraCIGetCommand) rawCmd;
		SimulationVar var = SimulationVar.fromId(cmd.getVariableIdentifier());

		Method m = getHandler(cmd.getTraCICmd(), var);

		logger.tracef("invokeHandler: SimulationCommandHandler.%s [CMD: %s VAR: %s]",
				m.getName(),
				cmd.getTraCICmd().logShort(),
				var.toString());
		return  invokeHandler(m, this, cmd, remoteManager);

	}

	public TraCICommand processSet(TraCICommand rawCmd, RemoteManager remoteManager) {

		TraCISetCommand cmd = (TraCISetCommand) rawCmd;
		SimulationVar var = SimulationVar.fromId(cmd.getVariableId());

		Method m = getHandler(cmd.getTraCICmd(), var);

		return invokeHandler(m, this, cmd, remoteManager);

	}

}
