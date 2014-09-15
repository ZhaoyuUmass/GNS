package edu.umass.cs.gns.replicaCoordination.multipaxos.multipaxospacket;

import edu.umass.cs.gns.nsdesign.nodeconfig.NodeId;
import org.json.JSONException;
import org.json.JSONObject;

import edu.umass.cs.gns.replicaCoordination.multipaxos.paxosutil.Ballot;

/**
 * 
 * @author V. Arun
 *
 */
public final class AcceptReplyPacket extends PaxosPacket {
    public final NodeId<String> nodeID; // sender nodeID
    public final Ballot ballot;
    public final int slotNumber;

    public final int committedSlot;

    protected static final  String NODE_ID = "node";
    private static final  String BALLOT_NUMBER = "ballot";
    private static final  String SLOT_NUMBER = "slot";
    private static final  String COMMITTED_SLOT = "committed_slot";

    public AcceptReplyPacket(NodeId<String> nodeID, Ballot ballot, int slotNumber, int committedSlot) {
    	super((PaxosPacket)null);
        this.packetType = PaxosPacketType.ACCEPT_REPLY;
        this.nodeID = nodeID;
        this.ballot = ballot;
        this.slotNumber = slotNumber;
        this.committedSlot = committedSlot;
    }
 
    public AcceptReplyPacket(JSONObject jsonObject) throws  JSONException{
    	super(jsonObject);
    	assert(PaxosPacket.getPaxosPacketType(jsonObject)==PaxosPacketType.ACCEPT_REPLY); // coz class is final
        this.packetType = PaxosPacketType.ACCEPT_REPLY;
        this.nodeID = new NodeId<String>(jsonObject.getString(NODE_ID));
        this.ballot = new Ballot(jsonObject.getString(BALLOT_NUMBER));
        this.slotNumber = jsonObject.getInt(SLOT_NUMBER);
        this.committedSlot = jsonObject.getInt(COMMITTED_SLOT);
    }


    @Override
    public JSONObject toJSONObjectImpl() throws JSONException {
        JSONObject json= new JSONObject();
        json.put(NODE_ID, nodeID.get());
        json.put(BALLOT_NUMBER, ballot.toString());
        json.put(SLOT_NUMBER, slotNumber);
        json.put(COMMITTED_SLOT, this.committedSlot);
        return json;
    }

}
