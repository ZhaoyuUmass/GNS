/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.umass.cs.gns.nameserver.recordmap;

import edu.umass.cs.gns.nameserver.NameRecord;
import edu.umass.cs.gns.nameserver.NameRecord;

/**
 *
 * @author westy
 */
public abstract class BasicRecordMapV2 implements RecordMapInterfaceV2 {
  
  @Override
  public String tableToString() {
    StringBuilder table = new StringBuilder();
    for (NameRecord record : getAllNameRecords()) {
      table.append(record.getName() + ": ");
      table.append(record.toString());
      table.append("\n");
    }
    return table.toString();
  }

}