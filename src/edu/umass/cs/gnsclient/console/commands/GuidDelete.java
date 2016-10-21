/*
 *
 *  Copyright (c) 2015 University of Massachusetts
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you
 *  may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 *
 *  Initial developer(s): Westy, Emmanuel Cecchet
 *
 */
package edu.umass.cs.gnsclient.console.commands;

import java.util.StringTokenizer;

import edu.umass.cs.gnsclient.client.util.GuidEntry;
import edu.umass.cs.gnsclient.client.util.KeyPairUtils;
import edu.umass.cs.gnsclient.console.ConsoleModule;

/**
 * Command that deletes a GUID/alias/Keypair from the GNS and user preferences
 * 
 * @author <a href="mailto:cecchet@cs.umass.edu">Emmanuel Cecchet </a>
 * @version 1.0
 */
public class GuidDelete extends ConsoleCommand
{

  /**
   * Creates a new <code>GuidDelete</code> object
   * 
   * @param module
   */
  public GuidDelete(ConsoleModule module)
  {
    super(module);
  }

  @Override
  public String getCommandDescription()
  {
    return "Deletes alias from the GNS (also deletes local keypair information if any)";
  }

  @Override
  public String getCommandName()
  {
    return "guid_delete";
  }

  @Override
  public String getCommandParameters()
  {
    return "alias";
  }

  /**
   * Override execute to not check for existing connectivity
   * @throws java.lang.Exception
   */
  @Override
  public void execute(String commandText) throws Exception
  {
    parse(commandText);
  }

  @Override
  public void parse(String commandText) throws Exception
  {
    try
    {
      StringTokenizer st = new StringTokenizer(commandText.trim());
      if (st.countTokens() != 1)
      {
        console.printString("Wrong number of arguments for this command.\n");
        return;
      }
      String aliasName = st.nextToken();

      if (!module.isSilent())
      {
        console.printString("Looking up alias " + aliasName + " certificates...\n");
      }
      GuidEntry guidToDelete = KeyPairUtils.getGuidEntry(module.getGnsInstance(), aliasName);

      if (guidToDelete == null)
      {
        console.printString("There is no local information for alias " + aliasName);
        console.printNewline();
        return;
      }

      module.getGnsClient().guidRemove(guidToDelete);
      console.printString("Alias " + aliasName + " removed from GNS.\n");
      KeyPairUtils.removeKeyPair(module.getGnsInstance(), aliasName);
      console.printString("Keys for " + aliasName + " removed from local repository.");
      console.printNewline();
    }
    catch (Exception e)
    {
      e.printStackTrace();
      console.printString("Failed to remove alias or delete keys ( " + e + ")\n");
    }
  }
}
