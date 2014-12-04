package edu.umass.cs.gns.nio;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import edu.umass.cs.gns.main.GNS;
import edu.umass.cs.gns.nio.nioutils.NIOInstrumenter;
import java.util.logging.Level;

/**
 * @author V. Arun
 */
public abstract class AbstractPacketDemultiplexer implements InterfacePacketDemultiplexer {

	private static final boolean NON_BLOCKING_PROCESSING = false;
  /**
   * ******************************** Start of new, untested parts **************************
   */
  private final ScheduledThreadPoolExecutor executor
          = new ScheduledThreadPoolExecutor(1); // FIXME: Not sure on what basis to limit number of threads
  private final HashMap<Integer, Boolean> demuxTypes
          = new HashMap<Integer, Boolean>();
  private final HashMap<Integer, InterfacePacketDemultiplexer> demuxMap
          = new HashMap<Integer, InterfacePacketDemultiplexer>();
  private static final Logger log
          = NIOTransport.LOCAL_LOGGER ? Logger.getLogger(AbstractPacketDemultiplexer.class.getName())
                  : GNS.getLogger();

  public void register(IntegerPacketType type) {
    register(type, this);
  }

  public void register(IntegerPacketType type, InterfacePacketDemultiplexer pd) {
    log.finest("Registering type " + type.getInt() + " with " + pd);
    this.demuxTypes.put(type.getInt(), true);
    this.demuxMap.put(type.getInt(), pd);
  }

  public void register(Set<IntegerPacketType> types, InterfacePacketDemultiplexer pd) {
    log.info("Registering types " + types + " for " + pd);
    for (IntegerPacketType type : types) {
      register(type, pd);
    }
  }

  public void register(Set<IntegerPacketType> types) {
    this.register(types, this);
  }

  public void register(Object[] types, InterfacePacketDemultiplexer pd) {
    log.info("Registering types " + (new HashSet<Object>(Arrays.asList(types))) + " for " + pd);
    for (Object type : types) {
      register((IntegerPacketType) type, pd);
    }
  }

  // This method will be invoked by NIO
  protected boolean handleJSONObjectSuper(JSONObject jsonObject) throws JSONException {
	NIOInstrumenter.rcvdJSONPacket(jsonObject);
    InterfacePacketDemultiplexer pd = this.demuxMap.get(JSONPacket.getPacketType(jsonObject));
    Tasker tasker = new Tasker(jsonObject, pd != null ? pd : this);
    boolean handled = this.demuxTypes.containsKey(JSONPacket.getPacketType(jsonObject));
    if (handled) {
    	if(NON_BLOCKING_PROCESSING) tasker.run(); // tasker better be really quick
    	else executor.schedule(tasker, 0, TimeUnit.MILLISECONDS);  
    } else {
      log.log(Level.WARNING, "Ignoring unknown packet type: {0}", JSONPacket.getPacketType(jsonObject));
    }
    return handled;
  }

  private class Tasker implements Runnable {

    private final JSONObject json;
    private final InterfacePacketDemultiplexer pd;

    Tasker(JSONObject json, InterfacePacketDemultiplexer pd) {
      this.json = json;
      this.pd = pd;
    }

    public void run() {
      try {
        pd.handleJSONObject(this.json);
      } catch (Exception e) {
        e.printStackTrace(); // unless printed task will die silently
      } catch (Error e) {
        e.printStackTrace();
      }
    }
  }

  public void stop() {
    this.executor.shutdown();
  }

  /**
   * ******************************** End of new, untested parts **************************
   */
  public void incrPktsRcvd() {
    NIOInstrumenter.incrPktsRcvd();
  } // Used for testing and debugging
}
