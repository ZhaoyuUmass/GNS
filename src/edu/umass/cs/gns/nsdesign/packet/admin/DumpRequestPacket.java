package edu.umass.cs.gns.nsdesign.packet.admin;

import edu.umass.cs.gns.nsdesign.packet.BasicPacketWithLnsAddress;
import edu.umass.cs.gns.nsdesign.packet.Packet;
import java.net.InetSocketAddress;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * This class implements the packet transmitted between local nameserver and a primary nameserver to get information about the
 * contents of the nameserver;
 *
 * @author Westy
 */
public class DumpRequestPacket extends BasicPacketWithLnsAddress {

  public final static String ID = "id";
  public final static String PRIMARY_NAMESERVER = "primary";
  public final static String JSON = "json";
  private final static String ARGUMENT = "arg";
  private int id;
  /**
   * Primary name server receiving the request *
   */
  private int primaryNameServer;
  
//  /**
//   * Local name server sending the request.
//   */
//  private InetSocketAddress lnsAddress = null;
  /**
   * JOSNObject where the results are kept *
   */
  private JSONArray jsonArray;
  private String argument;

  /**
   * Constructs a new DumpRequestPacket packet
   * @param id
   * @param primaryNameServer
   * @param lnsAddress
   * @param jsonArray
   * @param argument 
   */
  public DumpRequestPacket(int id, InetSocketAddress lnsAddress, int primaryNameServer, JSONArray jsonArray, String argument) {
    super(lnsAddress);
    this.type = Packet.PacketType.DUMP_REQUEST;
    this.id = id;
    this.primaryNameServer = primaryNameServer;
    //this.localNameServer = localNameServer;
    //this.lnsAddress = lnsAddress;
    this.jsonArray = jsonArray;
    this.argument = argument;
  }

  /**
   * Constructs a new DumpRequestPacket packet
   * @param localNameServer 
   */
  public DumpRequestPacket(int id, InetSocketAddress lnsAddress) {
    this(id, lnsAddress, -1, new JSONArray(), null);
  }

  /**
   * Constructs a new DumpRequestPacket packet
   * @param localNameServer
   * @param tagName 
   */
  public DumpRequestPacket(int id, InetSocketAddress lnsAddress, String tagName) {
    this(id, lnsAddress, -1, new JSONArray(), tagName);
  }

  /**
   *
   * Constructs new DumpRequestPacket from a JSONObject
   *
   * @param json JSONObject representing this packet
   * @throws org.json.JSONException
   */
  public DumpRequestPacket(JSONObject json) throws JSONException {
    super(json.optString(LNS_ADDRESS, null), json.optInt(LNS_PORT, INVALID_PORT));
    if (Packet.getPacketType(json) != Packet.PacketType.DUMP_REQUEST) {
      Exception e = new Exception("DumpRequestPacket: wrong packet type " + Packet.getPacketType(json));
      e.printStackTrace();
      return;
    }

    this.type = Packet.getPacketType(json);
    this.id = json.getInt(ID);
    this.primaryNameServer = json.getInt(PRIMARY_NAMESERVER);
    //this.lnsAddress = new InetSocketAddress(json.getString(LNS_ADDRESS), json.getInt(LNS_PORT));
    //this.localNameServer = json.getInt(LOCAL_NAMESERVER);
    this.jsonArray = json.getJSONArray(JSON);
    this.argument = json.optString(ARGUMENT, null);
  }

  /**
   *
   * Converts a DumpRequestPacket to a JSONObject.
   *
   * @return JSONObject representing this packet.
   * @throws org.json.JSONException
   */
  @Override
  public JSONObject toJSONObject() throws JSONException {
    JSONObject json = new JSONObject();
    Packet.putPacketType(json, getType());
    super.addToJSONObject(json);
    json.put(ID, id);
    json.put(PRIMARY_NAMESERVER, primaryNameServer);
    //json.put(LOCAL_NAMESERVER, localNameServer);
//    json.put(LNS_ADDRESS, lnsAddress.getHostString());
//    json.put(LNS_PORT, lnsAddress.getPort());
    json.put(JSON, jsonArray);
    if (this.argument != null) {
      json.put(ARGUMENT, argument);
    }
    return json;
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public JSONArray getJsonArray() {
    return jsonArray;
  }

  public void setJsonArray(JSONArray jsonArray) {
    this.jsonArray = jsonArray;
  }

//  public InetSocketAddress getLnsAddress() {
//    return lnsAddress;
//  }
//
//  public void setLnsAddress(InetSocketAddress lnsAddress) {
//    this.lnsAddress = lnsAddress;
//  }

  public int getPrimaryNameServer() {
    return primaryNameServer;
  }

  public void setPrimaryNameServer(int primaryNameServer) {
    this.primaryNameServer = primaryNameServer;
  }

  public String getArgument() {
    return argument;
  }
}
