package org.vadere.manager.traci.commandHandler;

import org.vadere.annotation.traci.client.TraCIApi;
import org.vadere.manager.RemoteManager;
import org.vadere.manager.TraCICommandCreationException;
import org.vadere.manager.traci.TraCICmd;
import org.vadere.manager.traci.TraCIDataType;
import org.vadere.manager.traci.commandHandler.annotation.SimulationHandler;
import org.vadere.manager.traci.commandHandler.annotation.SimulationHandlers;
import org.vadere.manager.traci.commandHandler.variables.SimulationVar;
import org.vadere.manager.traci.commands.TraCICommand;
import org.vadere.manager.traci.commands.TraCIGetCommand;
import org.vadere.manager.traci.commands.get.TraCIGetCacheHashCommand;
import org.vadere.manager.traci.respons.TraCIGetResponse;
import org.vadere.simulator.entrypoints.ScenarioFactory;
import org.vadere.simulator.projects.Scenario;
import org.vadere.simulator.utils.cache.ScenarioCache;
import org.vadere.util.geometry.shapes.VPoint;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;

/**
 * Handel GET/SET/SUB {@link org.vadere.manager.traci.commands.TraCICommand}s for the Simulation API
 */
@TraCIApi(
		name = "SimulationAPI",
		nameShort = "sim",
		singleAnnotation = SimulationHandler.class,
		multipleAnnotation = SimulationHandlers.class,
		cmdEnum = TraCICmd.class,
		varEnum = SimulationVar.class
)
public class SimulationCommandHandler  extends CommandHandler<SimulationVar>{

	public static SimulationCommandHandler instance;

	static {
		instance = new SimulationCommandHandler();
	}

	private SimulationCommandHandler(){
		super();
		init(SimulationHandler.class, SimulationHandlers.class);
	}

	@Override
	protected void init_HandlerSingle(Method m) {
		SimulationHandler an = m.getAnnotation(SimulationHandler.class);
		putHandler(an.cmd(), an.var(), m);
	}

	@Override
	protected void init_HandlerMult(Method m) {
		SimulationHandler[] ans = m.getAnnotation(SimulationHandlers.class).value();
		for(SimulationHandler a : ans){
			putHandler(a.cmd(), a.var(), m);
		}
	}

	public TraCIGetResponse responseOK(TraCIDataType responseDataType, Object responseData){
		return responseOK(responseDataType, responseData, TraCICmd.GET_SIMULATION_VALUE, TraCICmd.RESPONSE_GET_SIMULATION_VALUE);
	}

	public TraCIGetResponse responseERR(TraCIDataType responseDataType, Object responseData){
		return responseOK(responseDataType, responseData, TraCICmd.GET_SIMULATION_VALUE, TraCICmd.RESPONSE_GET_SIMULATION_VALUE);
	}

	public TraCIGetResponse responseERR(SimulationVar var, String err){
		return responseERR("["+var.toString()+"] "+ err, TraCICmd.GET_SIMULATION_VALUE, TraCICmd.RESPONSE_GET_SIMULATION_VALUE);
	}

	public TraCICommand process_getNetworkBound(TraCIGetCommand cmd, RemoteManager remoteManager, SimulationVar traCIVar){

		remoteManager.accessState((manager, state) -> {
			Rectangle2D.Double  rec = state.getTopography().getBounds();

			VPoint lowLeft = new VPoint(rec.getMinX(), rec.getMinY());
			VPoint highRight = new VPoint(rec.getMaxX(), rec.getMaxY());
			ArrayList<VPoint> polyList = new ArrayList<>();
			polyList.add(lowLeft);
			polyList.add(highRight);
			cmd.setResponse(responseOK(traCIVar.type, polyList));
		});

		return cmd;
	}

	@SimulationHandler(cmd = TraCICmd.GET_SIMULATION_VALUE, var = SimulationVar.CURR_SIM_TIME, name="getTime", ignoreElementId = true)
	public TraCICommand process_getSimTime(TraCIGetCommand cmd, RemoteManager remoteManager, SimulationVar traCIVar){

		remoteManager.accessState((manager, state) -> {
			// BigDecimal to ensure correct comparison in omentpp
			BigDecimal time = BigDecimal.valueOf(state.getSimTimeInSec());
			cmd.setResponse(responseOK(SimulationVar.CURR_SIM_TIME.type, time.setScale(1, RoundingMode.HALF_UP).doubleValue()));
		});

		return cmd;
	}

	@SimulationHandler(cmd = TraCICmd.GET_SIMULATION_VALUE, var = SimulationVar.CACHE_HASH, name = "getHash", dataTypeStr = "String", ignoreElementId = true)
	public TraCICommand process_get_cache_hash(TraCIGetCommand rawCmd, RemoteManager remoteManager){

		try{
			TraCIGetCacheHashCommand cmd = TraCIGetCacheHashCommand.create(rawCmd);
			if (cmd.getFile().length() > 0){
				try {
					Scenario scenario = ScenarioFactory.createScenarioWithScenarioJson(cmd.getFile());
					String hash = ScenarioCache.getHash(scenario);
					cmd.setResponse(responseOK(SimulationVar.CACHE_HASH.type, hash));
				} catch (IOException e) {
					cmd.setResponse(responseERR(SimulationVar.CACHE_HASH,"cannot read scenario"));
				}
			}
			return cmd;

		} catch (TraCICommandCreationException ee){
			rawCmd.setResponse(responseERR(SimulationVar.CACHE_HASH,"Ill formatted TraCIGetCacheHashCommand"));
		}
		return rawCmd;
	}

	public  TraCICommand process_getVehiclesStartTeleportIDs(TraCIGetCommand cmd, RemoteManager remoteManager, SimulationVar traCIVar){

		cmd.setResponse(responseOK(traCIVar.type, new ArrayList<>()));
		return cmd;
	}

	public  TraCICommand process_getVehiclesEndTeleportIDs(TraCIGetCommand cmd, RemoteManager remoteManager, SimulationVar traCIVar){

		cmd.setResponse(responseOK(traCIVar.type, new ArrayList<>()));
		return cmd;
	}

	public  TraCICommand process_getVehiclesStartParkingIDs(TraCIGetCommand cmd, RemoteManager remoteManager, SimulationVar traCIVar){

		cmd.setResponse(responseOK(traCIVar.type, new ArrayList<>()));
		return cmd;
	}

	public  TraCICommand process_getVehiclesStopParkingIDs(TraCIGetCommand cmd, RemoteManager remoteManager, SimulationVar traCIVar){

		cmd.setResponse(responseOK(traCIVar.type, new ArrayList<>()));
		return cmd;
	}

	public TraCICommand processValueSub(TraCICommand rawCmd, RemoteManager remoteManager){
		return processValueSub(rawCmd, remoteManager, this::processGet,
				TraCICmd.GET_SIMULATION_VALUE, TraCICmd.RESPONSE_SUB_SIMULATION_VALUE);
	}


	public TraCICommand processGet(TraCICommand rawCmd, RemoteManager remoteManager){

		TraCIGetCommand cmd = (TraCIGetCommand) rawCmd;
		SimulationVar var = SimulationVar.fromId(cmd.getVariableIdentifier());
		switch (var){
			case NETWORK_BOUNDING_BOX_2D:
				return process_getNetworkBound(cmd, remoteManager, var);
			case CURR_SIM_TIME:
				return process_getSimTime(cmd, remoteManager, var);
			case VEHICLES_START_TELEPORT_IDS:
				return process_getVehiclesStartTeleportIDs(cmd, remoteManager, var);
			case VEHICLES_END_TELEPORT_IDS:
				return process_getVehiclesEndTeleportIDs(cmd, remoteManager, var);
			case VEHICLES_START_PARKING_IDS:
				return process_getVehiclesStartParkingIDs(cmd, remoteManager, var);
			case VEHICLES_STOP_PARKING_IDS:
				return process_getVehiclesStopParkingIDs(cmd, remoteManager, var);
			case CACHE_HASH:
				return process_get_cache_hash(cmd, remoteManager);
			default:
				return process_NotImplemented(cmd, remoteManager);
		}
	}

	public TraCICommand processSet(TraCICommand cmd, RemoteManager remoteManager) {
		return process_NotImplemented(cmd, remoteManager);

	}

}
