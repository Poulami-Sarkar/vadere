package org.vadere.manager.client;

import org.vadere.manager.TraCISocket;
import org.vadere.manager.traci.commands.control.TraCICloseCommand;
import org.vadere.manager.traci.commands.control.TraCIGetVersionCommand;
import org.vadere.manager.traci.commands.control.TraCISendFileCommand;
import org.vadere.manager.traci.commands.control.TraCISimStepCommand;
import org.vadere.manager.traci.compoundobjects.CompoundObject;
import org.vadere.manager.traci.compoundobjects.CompoundObjectBuilder;
import org.vadere.manager.traci.reader.TraCIPacketBuffer;
import org.vadere.manager.traci.respons.TraCIGetResponse;
import org.vadere.manager.traci.respons.TraCIResponse;
import org.vadere.manager.traci.respons.TraCISimTimeResponse;
import org.vadere.manager.traci.writer.TraCIPacket;
import org.vadere.util.geometry.shapes.VPoint;
import org.vadere.util.io.IOUtils;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

public class TestClient extends org.vadere.manager.client.AbstractTestClient implements Runnable{

	private int port;
	private TraCISocket traCISocket;
	private ConsoleReader consoleReader;
	private Thread consoleThread;
	private String basePath;
	private String defaultScenario;
	private boolean running;

	public static void main(String[] args) throws IOException, InterruptedException {
		TestClient testClient = new TestClient(9999, args);
		testClient.run();
	}


	public TestClient (int port, String basePath, String defaultScenario){
		this.port = port;
		this.running = false;
		this.basePath = basePath;
		this.defaultScenario = defaultScenario;
	}

	public TestClient(int port, String[] args) {
		this.port = port;
		this.running = false;
		if (args.length == 2){
			this.basePath = args[0];
			this.defaultScenario = args[1];
		} else if (args.length == 1){
			this.basePath = args[0];
			this.defaultScenario  = "";
		} else {
			this.basePath = "";
			this.defaultScenario  = "";
		}
	}


	private void addCommands(ConsoleReader consoleReader){
		consoleReader.addCommand("ctr.getVersion", "", this::getVersion);
		consoleReader.addCommand("ctr.sendFile", "send file. default: scenario001", this::sendFile);
		consoleReader.addCommand("ctr.nextStep", "default(-1) one loop.", this::nextSimTimeStep);
		consoleReader.addCommand("ctr.close", "Close application and stop running simulations", this::close);
	}

	private void establishConnection() throws IOException, InterruptedException {
			Socket socket = new Socket();
			socket.setTcpNoDelay(true);
			int waitTime = 500; //ms
			System.out.println("Connect to 127.0.0.1:" + this.port);
			for (int i = 0; i < 14; i++) {
				try {
					socket.connect(new InetSocketAddress("127.0.0.1", this.port));
					break;
				} catch (ConnectException ex) {
					Thread.sleep(waitTime);
					waitTime *= 2;
				}
			}

			if (!socket.isConnected()) {
				System.out.println("can't connect to Server!");
				return;
			}

			System.out.println("connected...");
			traCISocket = new TraCISocket(socket);

			running = true;
	}

	private void handleConnection() throws IOException {
		try{

			consoleReader = new ConsoleReader();
			addCommands(consoleReader);
			init(traCISocket, consoleReader);
			consoleThread = new Thread(consoleReader);
			if (!basePath.isEmpty() && !defaultScenario.isEmpty()){
				System.out.println("send default file " + Paths.get(basePath, defaultScenario).toString());
				sendFile(new String[]{"send_file"});
			}
			consoleThread.start();

			consoleThread.join();


		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			if (traCISocket != null)
				traCISocket.close();
			if (consoleReader != null)
				consoleReader.stop();
		}
	}


	@Override
	public void run() {

		try {
			establishConnection();
			handleConnection();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	// Commands

	void getVersion(String[] args) throws IOException {
		TraCIPacket p = TraCIGetVersionCommand.build();
		traCISocket.sendExact(p);

		TraCIPacketBuffer buf = traCISocket.receiveExact();
		TraCIResponse cmd = buf.nextResponse();

		System.out.println(cmd.toString());

	}


	void close(String[] args) throws IOException {

		traCISocket.sendExact(TraCICloseCommand.build());

		TraCIResponse cmd = traCISocket.receiveResponse();
		System.out.println(cmd);

		System.out.println("Bye");
		consoleReader.stop();
	}


	void nextSimTimeStep(String[] args) throws IOException{
		double nextSimTime = -1.0;

		if (args.length > 1)
			nextSimTime = Double.parseDouble(args[1]);

		TraCIPacket packet = TraCISimStepCommand.build(nextSimTime);
		traCISocket.sendExact(packet);

		TraCISimTimeResponse cmd = (TraCISimTimeResponse) traCISocket.receiveResponse();
		System.out.println(cmd.toString());
	}


	void sendFile(String[] args) throws IOException {

		String filePath;

		if (args.length > 1) {
			if (!basePath.isEmpty()){
				filePath = Paths.get(basePath, args[1] + ".scenario").toString();
			} else {
				filePath = args[1];
			}
		} else {
			if (!basePath.isEmpty() && !defaultScenario.isEmpty()){
				filePath = Paths.get(basePath, defaultScenario).toString();
				System.out.println("use default " + defaultScenario);
			} else {
				System.out.println("no default scenario set");
				return;
			}
		}

		String data;
		try{
			data = IOUtils.readTextFile(filePath);
		} catch (IOException e){
			System.out.println("File not found: " + filePath);
			return;
		}

		TraCIPacket packet = TraCISendFileCommand.TraCISendFileCommand("Test", data);

		traCISocket.sendExact(packet);

		TraCIPacketBuffer buf = traCISocket.receiveExact();
		TraCIResponse cmd = buf.nextResponse();

		System.out.println(cmd.toString());
	}

	@Override
	public void personapi_getIDList(String[] args) throws IOException {
		TraCIGetResponse res = personapi.getIDList();
		System.out.println(res.getResponseData());
	}

    @Override
    public void personapi_getNextFreeId(String[] args) throws IOException {
		TraCIGetResponse res = personapi.getNextFreeId();
		System.out.println(res.getResponseData());
    }

	@Override
	public void personapi_getIDCount(String[] args) throws IOException {
		TraCIGetResponse res = personapi.getIDCount();
		System.out.println(res.getResponseData());
	}

	@Override
	public void personapi_getSpeed(String[] args) throws IOException {
		if(args.length < 2){
			System.out.println("command needs argument (id)");
			return;
		}
		String elementIdentifier = args[1];

		try {
			TraCIGetResponse res = personapi.getSpeed(elementIdentifier);
			double p = (double) res.getResponseData();
			System.out.println(p);
		} catch (ClassCastException e){
			System.out.println("Maybe the id is invalid. See getIDList for valid ids.");
			return;
		}
	}

	@Override
	public void personapi_setVelocity(String[] args) throws IOException {
		if(args.length < 3) {
			System.out.println("command needs argument id, velocity");
			return;
		}

		String elementIdentifier = args[1];
		double velocity = Double.parseDouble(args[2]);
		TraCIResponse res = personapi.setVelocity(elementIdentifier, velocity);
		System.out.println(res.toString());
	}

	@Override
	public void personapi_getPosition2D(String[] args) throws IOException {
		if(args.length < 2){
			System.out.println("command needs argument (id)");
			return;
		}
		String elementIdentifier = args[1];
		TraCIGetResponse res = personapi.getPosition2D(elementIdentifier);
		VPoint p = (VPoint) res.getResponseData();
		System.out.println(p.toString());
	}

	@Override
	public void personapi_setPosition2D(String[] args) throws IOException {
		if(args.length < 4) {
			System.out.println("command needs arguments id, x, y");
			return;
		}

		String elementIdentifier = args[1];
		double x = Double.parseDouble(args[2]);
		double y = Double.parseDouble(args[3]);
		VPoint p = new VPoint(x, y);
		TraCIResponse res = personapi.setPosition2D(elementIdentifier, p);
		System.out.println(res.toString());
	}

	@Override
	public void personapi_getPosition3D(String[] args) throws IOException {

	}

	@Override
	public void personapi_getPosition2DList(String[] args) throws IOException {
		TraCIGetResponse res = personapi.getPosition2DList();
		System.out.println(((Map<String, VPoint>) res.getResponseData()).toString());
	}

	@Override
	public void personapi_getLength(String[] args) throws IOException {

	}

	@Override
	public void personapi_getWidth(String[] args) throws IOException {

	}

	@Override
	public void personapi_getRoadId(String[] args) throws IOException {

	}

	@Override
	public void personapi_getAngle(String[] args) throws IOException {

	}

	@Override
	public void personapi_getType(String[] args) throws IOException {

	}

	@Override
	public void personapi_getTargetList(String[] args) throws IOException {
		if(args.length < 2){
			System.out.println("command needs argument (id)");
			return;
		}

		String elementIdentifier = args[1];
		TraCIGetResponse res = personapi.getTargetList(elementIdentifier);
		ArrayList<String> targets = (ArrayList<String>) res.getResponseData();
		System.out.println(elementIdentifier + ": " + Arrays.toString(targets.toArray()));
	}

	@Override
	public void personapi_setTargetList(String[] args) throws IOException {
		if(args.length < 3){
			System.out.println("command needs argument element id and at least one target id");
			return;
		}

		String elementIdentifier = args[1];
		ArrayList<String> targets = new ArrayList<>();
		for (int i = 2; i < args.length; i++){
			targets.add(args[i]);
		}

		TraCIResponse res =  personapi.setTargetList(elementIdentifier, targets);
		System.out.println(res.toString());
	}

	// todo implement new methods from AbstractTestClient....

	@Override
	public void personapi_createNew(String[] args) throws IOException {
		if(args.length < 5){
			System.out.println("command needs argument element id, x-coordinate, y-coordinate, list of targets");
			return;
		}

		String elementIdentifier = args[1];
		String x = args[2];
		String y = args[3];
		String[] targets = Arrays.copyOfRange(args,4,args.length);


		CompoundObject compoundObj = CompoundObjectBuilder.createPerson(elementIdentifier, x, y, targets);
		TraCIResponse res =  personapi.createNew(elementIdentifier, compoundObj);
		System.out.println(res.toString());
	}

}
