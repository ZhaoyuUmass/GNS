package edu.umass.cs.gns.nsdesign.activeReconfiguration;

import edu.umass.cs.gns.exceptions.CancelExecutorTaskException;
import edu.umass.cs.gns.main.GNS;
import edu.umass.cs.gns.nsdesign.Config;
import edu.umass.cs.gns.nsdesign.packet.NewActiveSetStartupPacket;
import org.json.JSONException;

import java.io.IOException;
import java.util.HashSet;
import java.util.TimerTask;

/**
 *
 * todo write doc here.
 * Created by abhigyan on 3/28/14.
 */
public class CopyStateFromOldActiveTask extends TimerTask {

  private ActiveReplica activeReplica;

  private NewActiveSetStartupPacket packet;

  private HashSet<Integer> oldActivesQueried;

  private int requestID;

  public CopyStateFromOldActiveTask(NewActiveSetStartupPacket packet, ActiveReplica activeReplica) throws JSONException {
    this.oldActivesQueried = new HashSet<Integer>();
    this.activeReplica = activeReplica;
    // first, store the original packet in hash map
    this.requestID = activeReplica.getOngoingStateTransferRequests().put(packet);
    // next, create a copy as which we will modify
    this.packet = new NewActiveSetStartupPacket(packet.toJSONObject());
    this.packet.setUniqueID(this.requestID);  // ID assigned by this active replica.
    this.packet.changePacketTypeToPreviousValueRequest();
    this.packet.changeSendingActive(activeReplica.getNodeID());
  }

  @Override
  public void run() {
    try {

      if (Config.debugMode) GNS.getLogger().info(" NEW_ACTIVE_START_FORWARD received packet: " + packet.toJSONObject());

      if (activeReplica.getOngoingStateTransferRequests().get(requestID) == null) {
        GNS.getLogger().info(" COPY State from Old Active Successful! Cancel Task; Actives Queried: " + oldActivesQueried);
        throw new CancelExecutorTaskException();
      }

      // select old active to send request to
      int oldActive = activeReplica.getGnsNodeConfig().getClosestServer(packet.getOldActiveNameServers(), oldActivesQueried);

      if (oldActive == -1) {
        // this will happen after all actives have been tried at least once.
        GNS.getLogger().severe(" Exception ERROR:  No More Actives Left To Query. Cancel Task!!! " + packet);
        activeReplica.getOngoingStateTransferRequests().remove(requestID);
        throw new CancelExecutorTaskException();
      }
      oldActivesQueried.add(oldActive);
      if (Config.debugMode) GNS.getLogger().info(" OLD ACTIVE SELECTED = : " + oldActive);

      try {
        activeReplica.getNioServer().sendToID(oldActive, packet.toJSONObject());
      } catch (IOException e) {
        GNS.getLogger().severe(" IOException here: " + e.getMessage());
        e.printStackTrace();
      } catch (JSONException e) {
        GNS.getLogger().severe(" JSONException here: " + e.getMessage());
        e.printStackTrace();
      }
      if (Config.debugMode) GNS.getLogger().info(" REQUESTED VALUE from OLD ACTIVE. PACKET: " + packet);

    } catch (JSONException e) {
      e.printStackTrace();
    } catch (Exception e) {
      if (e.getClass().equals(CancelExecutorTaskException.class)) {
        throw new RuntimeException(); // this is only way to cancel this task as it is run via ExecutorService
      }
      // other types of exception are not expected. so, log them.
      GNS.getLogger().severe("Exception in Copy State from old actives task. " + e.getMessage());
      e.printStackTrace();
      throw new RuntimeException();
    }
  }
}