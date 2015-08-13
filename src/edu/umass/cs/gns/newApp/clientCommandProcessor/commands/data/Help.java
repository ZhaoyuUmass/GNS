/*
 * Copyright (C) 2014
 * University of Massachusetts
 * All Rights Reserved 
 *
 * Initial developer(s): Westy.
 */
package edu.umass.cs.gns.newApp.clientCommandProcessor.commands.data;

import edu.umass.cs.gns.newApp.clientCommandProcessor.commandSupport.CommandResponse;
import edu.umass.cs.gns.newApp.clientCommandProcessor.commands.GnsCommand;
import edu.umass.cs.gns.newApp.clientCommandProcessor.commands.CommandModule;
import static edu.umass.cs.gns.newApp.clientCommandProcessor.commandSupport.GnsProtocolDefs.*;
import edu.umass.cs.gns.newApp.clientCommandProcessor.demultSupport.ClientRequestHandlerInterface;
import org.json.JSONObject;

/**
 *
 * @author westy
 */
public class Help extends GnsCommand {

  public Help(CommandModule module) {
    super(module);
  }

  @Override
  public String[] getCommandParameters() {
    return new String[]{};
  }

  @Override
  public String getCommandName() {
    return HELP;
  }

  @Override
  public CommandResponse<String> execute(JSONObject json, ClientRequestHandlerInterface handler) {
    if (json.has("tcp")) {
      return new CommandResponse<String>("Commands are sent as TCP packets." + NEWLINE + NEWLINE
              + "Note: We use the terms field and key interchangably below." + NEWLINE + NEWLINE
              + "Commands:" + NEWLINE
              + module.allCommandDescriptions(CommandModule.CommandDescriptionFormat.TCP));
    } else if (json.has("tcpwiki")) {
      return new CommandResponse<String>("Commands are sent as TCP packets." + NEWLINE + NEWLINE
              + "Note: We use the terms field and key interchangably below." + NEWLINE + NEWLINE
              + "Commands:" + NEWLINE
              + module.allCommandDescriptions(CommandModule.CommandDescriptionFormat.TCP_Wiki));
    } else {
      return new CommandResponse<String>("Commands are sent as HTTP GET queries." + NEWLINE + NEWLINE
              + "Note: We use the terms field and key interchangably below." + NEWLINE + NEWLINE
              + "Commands:" + NEWLINE
              + module.allCommandDescriptions(CommandModule.CommandDescriptionFormat.HTML));
    }
  }

  @Override
  public String getCommandDescription() {
    return "Returns this help message";
  }
}
