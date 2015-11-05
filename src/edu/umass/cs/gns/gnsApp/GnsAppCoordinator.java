/*
 * Copyright (C) 2015
 * University of Massachusetts
 * All Rights Reserved 
 */
package edu.umass.cs.gns.gnsApp;

import edu.umass.cs.gigapaxos.interfaces.Replicable;
import edu.umass.cs.nio.JSONMessenger;
import edu.umass.cs.nio.interfaces.Stringifiable;
import edu.umass.cs.reconfiguration.PaxosReplicaCoordinator;

/**
 * @author Westy
 * @param <String>
 */
public class GnsAppCoordinator<String> extends PaxosReplicaCoordinator<String> {

  GnsAppCoordinator(Replicable app, Stringifiable<String> unstringer, 
          JSONMessenger<String> messenger) {
    super(app, messenger.getMyID(), unstringer, messenger);
  }
}
