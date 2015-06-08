/*
 * Copyright (C) 2014
 * University of Massachusetts
 * All Rights Reserved 
 *
 * Initial developer(s): Westy.
 */
package edu.umass.cs.gns.newApp.clientCommandProcessor.commands.data;

import edu.umass.cs.gns.newApp.clientCommandProcessor.commands.CommandModule;
import edu.umass.cs.gns.newApp.clientCommandProcessor.commandSupport.UpdateOperation;
import static edu.umass.cs.gns.newApp.clientCommandProcessor.commandSupport.GnsProtocolDefs.*;

/**
 *
 * @author westy
 */
public class ReplaceOrCreateListUnsigned extends AbstractUpdateList {

  public ReplaceOrCreateListUnsigned(CommandModule module) {
    super(module);
  }

  @Override
  public UpdateOperation getUpdateOperation() {
    return UpdateOperation.SINGLE_FIELD_REPLACE_ALL_OR_CREATE;
  }

  @Override
  public String getCommandName() {
    return REPLACEORCREATELIST;
  }

  @Override
  public String[] getCommandParameters() {
    return new String[]{GUID, FIELD, VALUE};
  }

  @Override
  public String getCommandDescription() {
    return "Adds a key value pair to the GNS for the given GUID if it does not exist otherwise "
            + "replaces the value of this key value pair for the given GUID with the value. "
            + "Value is a list of items formated as a JSON list."
            + " Field must be world writeable as this command does not specify the writer and is not signed.";
  }
}
