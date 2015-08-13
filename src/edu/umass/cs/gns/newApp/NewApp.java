/*
 * Copyright (C) 2015
 * University of Massachusetts
 * All Rights Reserved 
 */
package edu.umass.cs.gns.newApp;

import edu.umass.cs.gigapaxos.InterfaceReplicable;
import edu.umass.cs.gigapaxos.InterfaceRequest;
import edu.umass.cs.gns.newApp.clientCommandProcessor.commandSupport.CommandHandler;
import edu.umass.cs.gns.database.ColumnField;
import edu.umass.cs.gns.database.MongoRecords;
import edu.umass.cs.gns.exceptions.FailedDBOperationException;
import edu.umass.cs.gns.exceptions.FieldNotFoundException;
import edu.umass.cs.gns.exceptions.RecordExistsException;
import edu.umass.cs.gns.exceptions.RecordNotFoundException;
import edu.umass.cs.gns.main.GNS;
import static edu.umass.cs.gns.newApp.AppReconfigurableNodeOptions.disableSSL;
import edu.umass.cs.gns.newApp.clientCommandProcessor.ClientCommandProcessor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

import edu.umass.cs.gns.nodeconfig.GNSConsistentReconfigurableNodeConfig;
import edu.umass.cs.gns.nodeconfig.GNSInterfaceNodeConfig;
import edu.umass.cs.gns.nodeconfig.GNSNodeConfig;
import edu.umass.cs.gns.newApp.clientSupport.LNSQueryHandler;
import edu.umass.cs.gns.newApp.clientSupport.LNSUpdateHandler;
import edu.umass.cs.gns.newApp.packet.ConfirmUpdatePacket;
import edu.umass.cs.gns.newApp.packet.DNSPacket;
import edu.umass.cs.gns.newApp.packet.NoopPacket;
import edu.umass.cs.gns.newApp.packet.Packet;
import edu.umass.cs.gns.newApp.packet.Packet.PacketType;
import edu.umass.cs.gns.newApp.packet.StopPacket;
import edu.umass.cs.gns.newApp.packet.UpdatePacket;
import edu.umass.cs.gns.newApp.recordmap.BasicRecordMap;
import edu.umass.cs.gns.newApp.recordmap.MongoRecordMap;
import edu.umass.cs.gns.newApp.recordmap.NameRecord;
import edu.umass.cs.gns.ping.PingManager;
import edu.umass.cs.nio.IntegerPacketType;
import edu.umass.cs.nio.InterfaceSSLMessenger;
import edu.umass.cs.nio.JSONMessenger;
import edu.umass.cs.reconfiguration.interfaces.InterfaceReconfigurable;
import edu.umass.cs.reconfiguration.interfaces.InterfaceReconfigurableNodeConfig;
import edu.umass.cs.reconfiguration.interfaces.InterfaceReconfigurableRequest;
import edu.umass.cs.reconfiguration.reconfigurationutils.RequestParseException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Westy
 */
public class NewApp implements GnsApplicationInterface<String>, InterfaceReplicable, InterfaceReconfigurable {

  private final static int INITIAL_RECORD_VERSION = 0;
  private final String nodeID;
  private final GNSConsistentReconfigurableNodeConfig<String> nodeConfig;
  private final PingManager<String> pingManager;
  /**
   * Object provides interface to the database table storing name records
   */
  private final BasicRecordMap nameRecordDB;
  /**
   * The Nio server
   */
  private final InterfaceSSLMessenger<String, JSONObject> messenger;
  private final ClientCommandProcessor clientCommandProcessor;

  // Keep track of commands that are coming in
  public final ConcurrentMap<Integer, CommandHandler.CommandRequestInfo> outStandingQueries
          = new ConcurrentHashMap<>(10, 0.75f, 3);

  /**
   * Creates the application.
   *
   * @param id
   * @param nodeConfig
   * @param messenger
   * @param mongoRecords
   */
  public NewApp(String id, GNSNodeConfig<String> nodeConfig, JSONMessenger<String> messenger,
          MongoRecords<String> mongoRecords) throws IOException {
    this.nodeID = id;
    this.nodeConfig = new GNSConsistentReconfigurableNodeConfig<>(nodeConfig);
    // Start a ping server, but not a client.
    this.pingManager = new PingManager<String>(nodeID, this.nodeConfig, true);
    GNS.getLogger().info("Node " + nodeID + " started Ping server on port "
            + nodeConfig.getCcpPingPort(nodeID));
    this.nameRecordDB = new MongoRecordMap<>(mongoRecords, MongoRecords.DBNAMERECORD);
    GNS.getLogger().info("App " + nodeID + " created " + nameRecordDB);
    this.messenger = messenger;
    this.clientCommandProcessor = new ClientCommandProcessor(messenger,
            new InetSocketAddress(nodeConfig.getBindAddress(id), nodeConfig.getCcpPort(id)),
            (GNSNodeConfig<String>) nodeConfig,
            AppReconfigurableNodeOptions.debuggingEnabled,
            this,
            (String) id,
            AppReconfigurableNodeOptions.dnsGnsOnly,
            AppReconfigurableNodeOptions.dnsOnly,
            AppReconfigurableNodeOptions.gnsServerIP);
  }

  private static PacketType[] types = {
    PacketType.DNS,
    PacketType.UPDATE,
    PacketType.SELECT_REQUEST,
    PacketType.SELECT_RESPONSE,
    PacketType.UPDATE_CONFIRM,
    PacketType.ADD_CONFIRM,
    PacketType.REMOVE_CONFIRM,
    PacketType.STOP,
    PacketType.NOOP,
    PacketType.COMMAND,
    PacketType.COMMAND_RETURN_VALUE};

  @Override
  public boolean handleRequest(InterfaceRequest request, boolean doNotReplyToClient) {
    boolean executed = false;
    try {
      //IntegerPacketType intPacket = request.getRequestType();
      JSONObject json = new JSONObject(request.toString());
      Packet.PacketType packetType = Packet.getPacketType(json);
      if (AppReconfigurableNodeOptions.debuggingEnabled) {
        GNS.getLogger().info("&&&&&&& APP " + nodeID + "&&&&&&& Handling " + packetType.name()
                + " packet: " + json.toString());
      }
      switch (packetType) {
        case DNS:
          // the only dns response we should see are coming in response to LNSQueryHandler requests
          DNSPacket<String> dnsPacket = new DNSPacket<String>(json, nodeConfig);
          if (!dnsPacket.isQuery()) {
            LNSQueryHandler.handleDNSResponsePacket(dnsPacket, this);
          } else {
            // otherwise it's a query
            AppLookup.executeLookupLocal(dnsPacket, this, doNotReplyToClient);
          }
          break;
        case UPDATE:
          AppUpdate.executeUpdateLocal(new UpdatePacket<String>(json, nodeConfig), this, doNotReplyToClient);
          break;
        case SELECT_REQUEST:
          Select.handleSelectRequest(json, this);
          break;
        case SELECT_RESPONSE:
          Select.handleSelectResponse(json, this);
          break;
        // HANDLE CONFIRMATIONS COMING BACK FROM AN LNS (SIDE-TO-SIDE)
        case UPDATE_CONFIRM:
        case ADD_CONFIRM:
        case REMOVE_CONFIRM:
          LNSUpdateHandler.handleConfirmUpdatePacket(new ConfirmUpdatePacket<String>(json, nodeConfig), this);
          break;
        case STOP:
          break;
        case NOOP:
          break;
        case COMMAND:
          CommandHandler.handleCommandPacketForApp(json, this);
          break;
        case COMMAND_RETURN_VALUE:
          CommandHandler.handleCommandReturnValuePacketForApp(json, this);
          break;
        default:
          GNS.getLogger().severe(" Packet type not found: " + json);
          return false;
      }
      executed = true;
    } catch (JSONException e) {
      e.printStackTrace();
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    } catch (SignatureException e) {
      e.printStackTrace();
    } catch (InvalidKeySpecException e) {
      e.printStackTrace();
    } catch (InvalidKeyException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (FailedDBOperationException e) {
      // all database operations throw this exception, therefore we keep throwing this exception upwards and catch this
      // here.
      // A database operation error would imply that the application hasn't been able to successfully execute
      // the request. therefore, this method returns 'false', hoping that whoever calls handleDecision would retry
      // the request.
      GNS.getLogger().severe("Error handling request: " + request.toString());
      e.printStackTrace();
    }
    return executed;
  }

  class CommandQuery {

    private String host;
    private int port;

    public CommandQuery(String host, int port) {
      this.host = host;
      this.port = port;
    }

    public String getHost() {
      return host;
    }

    public int getPort() {
      return port;
    }
  }

  // For InterfaceApplication
  @Override
  public InterfaceRequest getRequest(String string)
          throws RequestParseException {
    //GNS.getLogger().info(">>>>>>>>>>>>>>> GET REQUEST: " + string);
    if (AppReconfigurableNodeOptions.debuggingEnabled) {
      GNS.getLogger().fine(">>>>>>>>>>>>>>> GET REQUEST: " + string);
    }
    // Special case handling of NoopPacket packets
    if (InterfaceRequest.NO_OP.toString().equals(string)) {
      return new NoopPacket();
    }
    try {
      JSONObject json = new JSONObject(string);
      InterfaceRequest request = (InterfaceRequest) Packet.createInstance(json, nodeConfig);
//      if (request instanceof InterfaceReplicableRequest) {
//        GNS.getLogger().info(">>>>>>>>>>>>>>>UPDATE PACKET********* needsCoordination is "
//                + ((InterfaceReplicableRequest) request).needsCoordination());
//      }
      return request;
    } catch (JSONException e) {
      throw new RequestParseException(e);
    }
  }

  @Override
  public Set<IntegerPacketType> getRequestTypes() {
    return new HashSet<IntegerPacketType>(Arrays.asList(types));
  }

  @Override
  public boolean handleRequest(InterfaceRequest request) {
    return this.handleRequest(request, false);
  }

  private final static ArrayList<ColumnField> curValueRequestFields = new ArrayList<>();

  static {
    curValueRequestFields.add(NameRecord.VALUES_MAP);
    curValueRequestFields.add(NameRecord.TIME_TO_LIVE);
  }

  @Override
  public String getState(String name) {
    try {
      NameRecord nameRecord = NameRecord.getNameRecordMultiField(nameRecordDB, name, curValueRequestFields);
      NRState state = new NRState(nameRecord.getValuesMap(), nameRecord.getTimeToLive());
      if (AppReconfigurableNodeOptions.debuggingEnabled) {
        GNS.getLogger().info("&&&&&&& APP " + nodeID + "&&&&&&& Getting state: " + state.toString());
      }
      return state.toString();
    } catch (RecordNotFoundException e) {
      // normal result
    } catch (FieldNotFoundException e) {
      GNS.getLogger().severe("Field not found exception: " + e.getMessage());
      e.printStackTrace();
    } catch (FailedDBOperationException e) {
      GNS.getLogger().severe("State not read from DB: " + e.getMessage());
      e.printStackTrace();
    }
    return null;
  }

  /**
   *
   * @param name
   * @param state
   * @return
   */
  @Override
  public boolean updateState(String name, String state) {
    if (AppReconfigurableNodeOptions.debuggingEnabled) {
      GNS.getLogger().info("&&&&&&& APP " + nodeID + "&&&&&&& Updating " + name + " state: " + state);
    }
    try {
      if (state == null) {
        // If state is null the only thing it means is that we need to delete 
        // the record. If the record does not exists this is just a noop.
        NameRecord.removeNameRecord(nameRecordDB, name);
      } else { //state does not equal null so we either create a new record or update the existing one
        NameRecord nameRecord = null;
        try {
          nameRecord = NameRecord.getNameRecordMultiField(nameRecordDB, name, curValueRequestFields);
        } catch (RecordNotFoundException e) {
          // normal result if the field does not exist
        }
        if (nameRecord == null) { // create a new record
          try {
            NRState nrState = new NRState(state); // parse the new state
            nameRecord = new NameRecord(nameRecordDB, name, INITIAL_RECORD_VERSION,
                    nrState.valuesMap, nrState.ttl,
                    nodeConfig.getReplicatedReconfigurators(name));
            NameRecord.addNameRecord(nameRecordDB, nameRecord);
          } catch (RecordExistsException e) {
            GNS.getLogger().severe("Problem updating state, record already exists: " + e.getMessage());
          } catch (JSONException e) {
            GNS.getLogger().severe("Problem updating state: " + e.getMessage());
          }
        } else { // update the existing record
          try {
            NRState nrState = new NRState(state); // parse the new state
            nameRecord.updateState(nrState.valuesMap, nrState.ttl);
          } catch (JSONException | FieldNotFoundException e) {
            GNS.getLogger().severe("Problem updating state: " + e.getMessage());
          }
        }
      }
      return true;
    } catch (FailedDBOperationException e) {
      GNS.getLogger().severe("Failed update exception: " + e.getMessage());
      e.printStackTrace();
    }
    return false;
  }

  /**
   *
   * @param name
   * @param epoch
   * @return
   */
  @Override
  public InterfaceReconfigurableRequest getStopRequest(String name, int epoch) {
    return new StopPacket(name, epoch);
  }

  @Override
  public String getFinalState(String name, int epoch) {
    throw new RuntimeException("This method should not have been called");
  }

  @Override
  public void putInitialState(String name, int epoch, String state) {
    throw new RuntimeException("This method should not have been called");
  }

  @Override
  public boolean deleteFinalState(String name, int epoch) {
    throw new RuntimeException("This method should not have been called");
  }

  @Override
  public Integer getEpoch(String name) {
    throw new RuntimeException("This method should not have been called");
  }

  //
  // GnsApplicationInterface implementation
  //
  @Override
  public String getNodeID() {
    return nodeID;
  }

  @Override
  public BasicRecordMap getDB() {
    return nameRecordDB;
  }

  @Override
  public InterfaceReconfigurableNodeConfig<String> getGNSNodeConfig() {
    return nodeConfig;
  }

  @Override
  public void sendToClient(InetSocketAddress isa, JSONObject msg) throws IOException {
//    InetSocketAddress clientAddress = new InetSocketAddress(isa.getAddress(),
//            ActiveReplica.getClientFacingPort(isa.getPort()));
    //GNS.getLogger().info("&&&&&&& APP " + nodeID + "&&&&&&& Sending to: " + isa + " " + msg);
    if (!disableSSL) {
      messenger.getClientMessenger().sendToAddress(isa, msg);
    } else {
      messenger.sendToAddress(isa, msg);
    }
  }

  @Override
  public void sendToID(String id, JSONObject msg) throws IOException {
    messenger.sendToID(id, msg);
  }

  @Override
  public PingManager<String> getPingManager() {
    return pingManager;
  }

  @Override
  public ClientCommandProcessor getClientCommandProcessor() {
    return clientCommandProcessor;
  }

}
