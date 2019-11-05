package org.vadere.manager.traci.writer;

import org.vadere.manager.TraCIException;
import org.vadere.manager.traci.TraCICmd;
import org.vadere.manager.traci.TraCIDataType;
import org.vadere.manager.traci.commands.control.TraCIGetVersionCommand;
import org.vadere.manager.traci.respons.StatusResponse;
import org.vadere.manager.traci.respons.TraCIGetResponse;
import org.vadere.manager.traci.respons.TraCIGetVersionResponse;
import org.vadere.manager.traci.respons.TraCISimTimeResponse;
import org.vadere.manager.traci.respons.TraCIStatusResponse;
import org.vadere.manager.traci.respons.TraCISubscriptionResponse;

import java.nio.ByteBuffer;

/**
 *  //todo comment
 */
public class TraCIPacket  extends ByteArrayOutputStreamTraCIWriter{

//	private TraCIWriter writer;
	private boolean emptyLengthField;
	private boolean finalized; //


	public static TraCIPacket create(){
		return new TraCIPacket().addEmptyLengthField();
	}

	public static TraCIPacket create(int packetSize){
		TraCIPacket packet = new TraCIPacket();
		packet.writeInt(packetSize);
		return packet;
	}

	public static TraCIPacket sendStatus(TraCICmd cmd, TraCIStatusResponse status, String description){
		TraCIPacket response = new TraCIPacket();
		int cmdLen = 7 + response.getStringByteCount(description);

		if (cmdLen > 255){
			//extended CMD
			cmdLen += 4; // add int field
			response.writeInt(4 + cmdLen); // packet limit (4 + cmdLen) [4]
			response.writeUnsignedByte(0); // [1]
			response.writeInt(cmdLen); // [4]
		} else {
			response.writeInt(4 + cmdLen);  // [4]
			response.writeUnsignedByte(cmdLen);  // [1]
		}
		response.writeUnsignedByte(cmd.id); // [1]
		response.writeUnsignedByte(status.id); // [1]
		response.writeString(description); //[4 + strLen]
		response.finalizePacket();
		return response;
	}

	public TraCIPacket finalizePacket(){
		finalized = true;
		return this;
	}

	private void throwIfFinalized(){
		if (finalized)
			throw new TraCIException("Cannot change finalized TraCIPacket");
	}

	private TraCIPacket() {
		super();
		finalized = false;
		emptyLengthField = false;
	}


	private TraCIPacket addEmptyLengthField(){
		if(emptyLengthField)
			throw  new IllegalStateException("Should only be called at most once.");
		writeInt(-1);
		emptyLengthField = true;
		return this;
	}


	public byte[] send() {

		// packet is valid TraCI packet an can be send.
		if (finalized)
			return asByteArray();

		// packet limit must be set to correct value
		if (emptyLengthField){
			ByteBuffer packet = asByteBuffer();
			packet.putInt(packet.capacity());
			packet.position(0);
			return packet.array();
		} else {
			return asByteArray();
		}

	}

	private TraCIWriter getCmdBuilder(){
		return new ByteArrayOutputStreamTraCIWriter();
	}

	public TraCIPacket wrapSetCommand(TraCICmd commandIdentifier, String elementIdentifier,
									  int variableIdentifier, TraCIDataType dataType, Object data){

		TraCIWriter cmdBuilder = getCmdBuilder();
		cmdBuilder.writeUnsignedByte(commandIdentifier.id)
				.writeUnsignedByte(variableIdentifier)
				.writeString(elementIdentifier)
				.writeObjectWithId(dataType, data);

		addCommandWithoutLen(cmdBuilder.asByteArray());

		return this;
	}

	public TraCIPacket wrapGetResponse(TraCIGetResponse res){
		addStatusResponse(res.getStatusResponse());

		if (!res.getStatusResponse().getResponse().equals(TraCIStatusResponse.OK))
			return this; // ERR or NOT_IMPLEMENTED --> only StatusResponse

		TraCIWriter cmdBuilder = getCmdBuilder();
		cmdBuilder.writeUnsignedByte(res.getResponseIdentifier().id)
				.writeUnsignedByte(res.getVariableIdentifier())
				.writeString(res.getElementIdentifier())
				.writeObjectWithId(res.getResponseDataType(), res.getResponseData());

		addCommandWithoutLen(cmdBuilder.asByteArray());

		return this;
	}

	public TraCIPacket wrapValueSubscriptionCommand(TraCISubscriptionResponse res){
		addStatusResponse(res.getStatusResponse());

		if (!res.getStatusResponse().getResponse().equals(TraCIStatusResponse.OK))
			return this; // ERR or NOT_IMPLEMENTED --> only StatusResponse

		wrapSubscription(res);

		return this;
	}

	private void wrapSubscription(TraCISubscriptionResponse res){
		TraCIWriter cmdBuilder = getCmdBuilder();
		cmdBuilder.writeUnsignedByte(res.getResponseIdentifier().id) // (i.e. TraCICmd.RESPONSE_SUB_PERSON_VARIABLE)
				.writeString(res.getElementId())
				.writeUnsignedByte(res.getNumberOfVariables());
		res.getResponses().forEach( var -> {
			cmdBuilder.writeUnsignedByte(var.getVariableId())
					.writeUnsignedByte(var.getStatus().id)
					.writeObjectWithId(var.getVariableDataType(), var.getVariableValue());
		});

		addCommandWithExtendedLenField(cmdBuilder.asByteArray());
	}

	public TraCIPacket wrapGetVersionCommand(TraCIGetVersionCommand cmd){
		TraCIGetVersionResponse res = cmd.getResponse();

		if(res.isOKResponseStatus())
			add_OK_StatusResponse(cmd.getTraCICmd());
		else
			addStatusResponse(res.getStatusResponse());

		TraCIWriter cmdBuilder = getCmdBuilder();
		cmdBuilder.writeUnsignedByte(res.getResponseIdentifier().id)
				.writeInt(res.getVersionId())
				.writeString(res.getVersionString());

		addCommandWithoutLen(cmdBuilder.asByteArray());

		return this;
	}

	public TraCIPacket wrapSimTimeStepCommand(TraCISimTimeResponse res){
		addStatusResponse(res.getStatusResponse());

		if (!res.getStatusResponse().getResponse().equals(TraCIStatusResponse.OK))
			return this; // ERR or NOT_IMPLEMENTED --> only StatusResponse


		// not length field! Directly add number of subscription responses.
		writeInt(res.getNumberOfSubscriptions());

		// add each SubscriptionsResponse as its own command (with length and responseID)
		res.getSubscriptionResponses().forEach(this::wrapSubscription);


		return this;
	}

	public void addCommandWithExtendedLenField(byte[] buffer){
		writeUnsignedByte(0);
		writeInt(buffer.length + 5); // 1 + 4 length field
		writeBytes(buffer);
	}


	public void addCommandWithoutLen(byte[] buffer){
		if (buffer.length > 255){
			writeUnsignedByte(0);
			writeInt(buffer.length + 5); // 1 + 4 length field
			writeBytes(buffer);
		} else {
			writeUnsignedByte(buffer.length + 1); // 1 length field
			writeBytes(buffer);
		}
	}


	public TraCIPacket add_Err_StatusResponse(int cmdIdentifier, String description){
		throwIfFinalized();
		return addStatusResponse(cmdIdentifier, TraCIStatusResponse.ERR, description);
	}

	public TraCIPacket add_OK_StatusResponse(TraCICmd traCICmd){
		throwIfFinalized();
		return add_OK_StatusResponse(traCICmd.id);
	}

	public TraCIPacket add_OK_StatusResponse(int cmdIdentifier){
		throwIfFinalized();
		// simple OK Status without description.
		writeUnsignedByte(7);
		writeUnsignedByte(cmdIdentifier);
		writeUnsignedByte(TraCIStatusResponse.OK.id);
		writeInt(0);
		return this;
	}

	public TraCIPacket addStatusResponse(StatusResponse res) {
		addStatusResponse(res.getCmdIdentifier().id, res.getResponse(), res.getDescription());
		return this;
	}

	public TraCIPacket addStatusResponse(int cmdIdentifier, TraCIStatusResponse response, String description){
		throwIfFinalized();
		// expect single byte cmdLenField.
		// cmdLenField + cmdIdentifier + cmdResult + strLen + str
		// 1 + 1 + 1 + 4 + len(strBytes)
		int cmdLen = 7 + stringByteCount(description);

		writeCommandLength(cmdLen); // 1b
		writeUnsignedByte(cmdIdentifier); // 1b
		writeUnsignedByte(response.id); // 4b
		writeString(description); // 4b + X

		return this;
	}

}
