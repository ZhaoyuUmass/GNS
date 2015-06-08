/*
 * Copyright (C) 2014
 * University of Massachusetts
 * All Rights Reserved 
 *
 * Initial developer(s): Westy.
 */
package edu.umass.cs.gns.newApp.clientCommandProcessor.commands.account;

import edu.umass.cs.gns.newApp.clientCommandProcessor.commandSupport.AccountAccess;
import edu.umass.cs.gns.newApp.clientCommandProcessor.commandSupport.CommandResponse;
import static edu.umass.cs.gns.newApp.clientCommandProcessor.commandSupport.GnsProtocolDefs.*;
import edu.umass.cs.gns.newApp.clientCommandProcessor.commandSupport.GuidInfo;
import edu.umass.cs.gns.newApp.clientCommandProcessor.commands.CommandModule;
import edu.umass.cs.gns.newApp.clientCommandProcessor.commands.GnsCommand;
import edu.umass.cs.gns.newApp.clientCommandProcessor.demultSupport.ClientRequestHandlerInterface;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author westy
 */
public class LookupGuidRecord extends GnsCommand {

  public LookupGuidRecord(CommandModule module) {
    super(module);
  }

  @Override
  public String[] getCommandParameters() {
    return new String[]{GUID};
  }

  @Override
  public String getCommandName() {
    return LOOKUPGUIDRECORD;
  }

  @Override
  public CommandResponse execute(JSONObject json, ClientRequestHandlerInterface handler) throws JSONException {
//    if (CommandDefs.handleAcccountCommandsAtNameServer) {
//      return LNSToNSCommandRequestHandler.sendCommandRequest(json);
//    } else {
      String guid = json.getString(GUID);
      GuidInfo guidInfo;
      if ((guidInfo = AccountAccess.lookupGuidInfo(guid, handler)) == null) {
        return new CommandResponse(BADRESPONSE + " " + BADGUID + " " + guid);
      }
      if (guidInfo != null) {
        try {
          return new CommandResponse(guidInfo.toJSONObject().toString());
        } catch (JSONException e) {
          return new CommandResponse(BADRESPONSE + " " + JSONPARSEERROR);
        }
      } else {
        return new CommandResponse(BADRESPONSE + " " + BADGUID + " " + guid);
      }
   // }
  }

  @Override
  public String getCommandDescription() {
    return "Returns human readable name and public key associated with the given GUID. "
            + "Returns " + BADGUID + " if the GUID has not been registered.";

  }
}
