package edu.umass.cs.gns.nameserver;

import edu.umass.cs.gns.client.UpdateOperation;
import edu.umass.cs.gns.exceptions.FieldNotFoundException;
import edu.umass.cs.gns.exceptions.RecordExistsException;
import edu.umass.cs.gns.exceptions.RecordNotFoundException;
import edu.umass.cs.gns.main.GNS;
import edu.umass.cs.gns.main.ReplicationFrameworkType;
import edu.umass.cs.gns.main.StartNameServer;
import edu.umass.cs.gns.nameserver.replicacontroller.ReplicaControllerRecord;
import edu.umass.cs.gns.util.ConfigFileInfo;
import edu.umass.cs.gns.util.HashFunction;
import edu.umass.cs.gns.util.OutputMemoryUse;
import edu.umass.cs.gns.util.Util;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.*;


/**
 * **********************************************************
 * This class provides method to add synthetic workloads to
 * the name server's record table. The synthetic workload
 * consists of integers. The integer value represents the name
 * and its popularity/rank.
 *
 * @author Hardeep Uppal, Abhigyan
 * **********************************************************
 */
public class GenerateSyntheticRecordTable {

//  public static long sleepBetweenNames = 25;

  static final String CHARACTERS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  static Random rnd = new Random(System.currentTimeMillis());

  static String randomString(int len) {
    StringBuilder sb = new StringBuilder(len);
    for (int i = 0; i < len; i++) {
      sb.append(CHARACTERS.charAt(rnd.nextInt(CHARACTERS.length())));
    }
    return sb.toString();
  }


  public static void generateRecordTableWithActives(int regularWorkloadSize, int mobileWorkloadSize,
                                                    int defaultTTLRegularNames,int defaultTTLMobileNames,
                                                    String activesFile){


//		InCoreRecordMap recordMap = new InCoreRecordMap();
//		ConcurrentMap<String, NameRecord> recordTable = new ConcurrentHashMap<String, NameRecord>(
//				( regularWorkloadSize + mobileWorkloadSize + 1), 0.75f, 8);

    HashMap<Integer, Set<Integer>> nameActives = null;
    try {
      nameActives = readActives(activesFile);
    } catch (IOException e) {
      GNS.getLogger().severe("Exception: error reading the set of actives from file. Quitting." + e.getMessage());
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      System.exit(2);
    }
    // reset the database
    NameServer.resetDB();

    int batchSize = 10000;

    int numActivesAdded = 0;
    int numPrimariesAdded = 0;

    ArrayList<JSONObject> rcRecords = new ArrayList<JSONObject>();
    ArrayList<JSONObject> nameRecords = new ArrayList<JSONObject>();

    long t0 = System.currentTimeMillis();
    for (int name = 0; name < (regularWorkloadSize + mobileWorkloadSize); name++) {

      if (nameActives.containsKey(name) == false) continue; // this name will not be queried anytime

      // After how much delay will paxos start coordinator election ...
//      long t1 = System.currentTimeMillis();
//      long timeSpent = t1 - t0;
//      long initScoutDelay = 0;
//      if (StartNameServer.paxosStartMinDelaySec > 0 && StartNameServer.paxosStartMaxDelaySec > 0) {
//        if (timeSpent > StartNameServer.paxosStartMaxDelaySec*1000) {
//          initScoutDelay = 0;
//        } else {
//          initScoutDelay = (StartNameServer.paxosStartMinDelaySec*1000 - timeSpent)
//                  + new Random().nextInt(StartNameServer.paxosStartMaxDelaySec*1000 - StartNameServer.paxosStartMinDelaySec*1000);
//        }
//      }

      try {
        String strName = Integer.toString(name);
        Set<Integer> primaryNameServer = HashFunction.getPrimaryReplicasNoCache(strName);

        // Add record into the name server's record table if the name server
        // is the primary replica of this name
        if (primaryNameServer.contains(NameServer.nodeID)) {
          numPrimariesAdded++;
          //Use the SHA-1 hash of the name as its address

          if (StartNameServer.debugMode) GNS.getLogger().fine("PrimaryRecordAdded\tName:\t" + name);
          //Generate an entry for the name and add its record to the name server record table
          ReplicaControllerRecord nameRecordPrimary = new ReplicaControllerRecord(strName, nameActives.get(name), true);
          rcRecords.add(nameRecordPrimary.toJSONObject());
          if (rcRecords.size() == batchSize) {
            NameServer.replicaController.bulkInsertRecords(rcRecords);
            GNS.getLogger().info("Bulk insert RC records. count:" + numPrimariesAdded + "\tname: " + name);
            rcRecords = new ArrayList<JSONObject>();
          }
//          try {
//            NameServer.addNameRecordPrimary(nameRecordPrimary);
//          } catch (RecordExistsException e) {
//            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//            continue;
//          }

          // Don't need to create per-name paxos among primaries.
//          try {
//            PaxosManager.createPaxosInstance(ReplicaController.getPrimaryPaxosID(nameRecordPrimary), primaryNameServer,
//                    nameRecordPrimary.toString(), 0);
//          } catch (FieldNotFoundException e) {
//            GNS.getLogger().severe("Field not found exception. " + e.getMessage());
//            e.printStackTrace();
//          }
        }

        // Don't need to create per-name paxos among primaries
        if (nameActives.get(name).contains(NameServer.nodeID)){
          if (StartNameServer.debugMode) GNS.getLogger().fine("NameRecordAdded\tName:\t" + name);
          numActivesAdded++;
          ValuesMap valuesMap = new ValuesMap();
          valuesMap.put(NameRecordKey.EdgeRecord.getName(), new ResultValue(Arrays.asList(randomString(10))));

          //Add to DB
          NameRecord nameRecord = new NameRecord(Integer.toString(name), nameActives.get(name), name + "-1", valuesMap,
                  0);
          nameRecords.add(nameRecord.toJSONObject());
          if (nameRecords.size() == batchSize) {
            NameServer.recordMap.bulkInsertRecords(nameRecords);
            GNS.getLogger().info("Bulk insert name records. count:" + numActivesAdded + "\tname: " + name);
            nameRecords = new ArrayList<JSONObject>();
          }
          //Create paxos instance
          ListenerReplicationPaxos.createPaxosInstanceForName(Integer.toString(name), nameActives.get(name),
                  name + "-1", valuesMap, 0, 0);
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    long t1 = System.currentTimeMillis();
    GNS.getLogger().info(" Number of records added in primary DB: " + numPrimariesAdded + " In active DB: " + numActivesAdded);
    GNS.getLogger().info(" Time to add all records " + (t1 - t0)/1000 + " sec");
  }


  public static void generateRecordTableWithActivesNew(String activesFile) {
    int numActives = (StartNameServer.regularWorkloadSize + StartNameServer.mobileWorkloadSize);
    try {
      int numActivesFile = countActives(activesFile);
      if (numActivesFile != numActives) {
        GNS.getLogger().severe("NameActives files does not match with workload. Names (in file): " + numActivesFile
        + " Workload: " + numActives);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    // reset the database
    if (StartNameServer.noLoadDB == false) NameServer.resetDB();
    int batchSize = 10000;

    int numPrimariesAdded = 0;
    int numActivesAdded = 0;

    long t0 = System.currentTimeMillis();
    // read actives and add to actives database
    try {
      BufferedReader br = new BufferedReader(new FileReader(activesFile));
      HashMap<Integer, Set<Integer>> nameActives = new HashMap<Integer, Set<Integer>>();
      int count = 0;
      while (true) {
        count ++;
        String line = br.readLine();
        if (line == null) break;
        String[] tokens = line.split("\\s+");
        int name1 = Integer.parseInt(tokens[0]);
        HashSet<Integer> actives = new HashSet<Integer>();
        for (int i = 1; i < tokens.length; i++) {
          actives.add(Integer.parseInt(tokens[i]));
        }
        if (actives.contains(NameServer.nodeID)) nameActives.put(name1, actives);
        if (nameActives.size() == batchSize) {
          ArrayList<JSONObject> nameRecords = new ArrayList<JSONObject>();
          for (int nameInt: nameActives.keySet()) {
            ValuesMap valuesMap = new ValuesMap();
            valuesMap.put(NameRecordKey.EdgeRecord.getName(), new ResultValue(Arrays.asList(randomString(10))));
            //Add to DB
            NameRecord nameRecord = new NameRecord(Integer.toString(nameInt), nameActives.get(nameInt), nameInt + "-1",
                    valuesMap, 0);
            nameRecords.add(nameRecord.toJSONObject());
            ListenerReplicationPaxos.createPaxosInstanceForName(Integer.toString(nameInt), nameActives.get(nameInt),
                    nameInt + "-1", valuesMap, 0, 0);
          }
          if (StartNameServer.noLoadDB == false) NameServer.recordMap.bulkInsertRecords(nameRecords);
          numActivesAdded += nameRecords.size();
          nameActives.clear();
          double percent = count*100.0/(StartNameServer.mobileWorkloadSize + StartNameServer.regularWorkloadSize);
          GNS.getLogger().info(" Loading actives. " + percent + "% done. Time = " +
                  (System.currentTimeMillis() - t0)/1000 + " sec. Name = " + name1 );
        }
      }
      ArrayList<JSONObject> nameRecords = new ArrayList<JSONObject>();
      for (int nameInt: nameActives.keySet()) {
        ValuesMap valuesMap = new ValuesMap();
        valuesMap.put(NameRecordKey.EdgeRecord.getName(), new ResultValue(Arrays.asList(randomString(10))));
        //Add to DB
        NameRecord nameRecord = new NameRecord(Integer.toString(nameInt), nameActives.get(nameInt), nameInt + "-1",
                valuesMap, 0);
        nameRecords.add(nameRecord.toJSONObject());
        ListenerReplicationPaxos.createPaxosInstanceForName(nameRecord, 0);
      }
      numActivesAdded += nameRecords.size();
      if (StartNameServer.noLoadDB == false) NameServer.recordMap.bulkInsertRecords(nameRecords);
      GNS.getLogger().info(" Loading actives. Complete. Time = " + (System.currentTimeMillis() - t0)/1000 + " sec");
//      return nameActives;
//      nameActives = readActives(activesFile);
    } catch (IOException e) {
      GNS.getLogger().severe("Exception: error reading the set of actives from file. Quitting." + e.getMessage());
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      System.exit(2);
    } catch (RecordExistsException e) {
      e.printStackTrace();
    } catch (JSONException e) {
      e.printStackTrace();
    }

    long t1 = System.currentTimeMillis();

    try {
      if (StartNameServer.noLoadDB == false) {
        BufferedReader br = new BufferedReader(new FileReader(activesFile));
        HashMap<Integer, Set<Integer>> nameActives = new HashMap<Integer, Set<Integer>>();
        int count = 0;
        while (true) {
          String line = br.readLine();
          count++;
          if (line == null) break;
          String[] tokens = line.split("\\s+");
          int name1 = Integer.parseInt(tokens[0]);

          Set<Integer> primaries = HashFunction.getPrimaryReplicas(tokens[0]);
          if (primaries.contains(NameServer.nodeID) == false) continue;

          HashSet<Integer> actives = new HashSet<Integer>();
          for (int i = 1; i < tokens.length; i++) {
            actives.add(Integer.parseInt(tokens[i]));
          }
          nameActives.put(name1,actives);

          if (nameActives.size() == batchSize) {
            ArrayList<JSONObject> rcRecords = new ArrayList<JSONObject>();
            for (int nameInt: nameActives.keySet()) {
              ReplicaControllerRecord rcRecord = new ReplicaControllerRecord(Integer.toString(nameInt),
                      nameActives.get(nameInt), true);
              rcRecords.add(rcRecord.toJSONObject());
            }
            NameServer.replicaController.bulkInsertRecords(rcRecords);
            numPrimariesAdded+= nameActives.size();
            nameActives.clear();
            double percent = count*100.0/(StartNameServer.mobileWorkloadSize + StartNameServer.regularWorkloadSize);
            GNS.getLogger().info(" Loading primaries. " + percent + "% done. Time = " +
                    (System.currentTimeMillis() - t1)/1000 + " sec. Name = " + name1 );
          }
        }
        ArrayList<JSONObject> rcRecords = new ArrayList<JSONObject>();
        for (int nameInt: nameActives.keySet()) {
          ReplicaControllerRecord rcRecord = new ReplicaControllerRecord(Integer.toString(nameInt),
                  nameActives.get(nameInt), true);
          rcRecords.add(rcRecord.toJSONObject());
        }
        numPrimariesAdded += nameActives.size();
        NameServer.replicaController.bulkInsertRecords(rcRecords);
      }
      GNS.getLogger().info(" Loading primaries. Complete. Time = " + (System.currentTimeMillis() - t1)/1000 + " sec");
    } catch (IOException e) {
      GNS.getLogger().severe("Exception: error reading the set of actives from file. Quitting." + e.getMessage());
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      System.exit(2);
    } catch (RecordExistsException e) {
      e.printStackTrace();
    } catch (JSONException e) {
      e.printStackTrace();
    }

    long t2 = System.currentTimeMillis();
    GNS.getLogger().info(" Number of records added in primary DB: " + numPrimariesAdded + " In active DB: " + numActivesAdded);
    GNS.getLogger().info(" Time to add all records " + (t1 - t0)/1000 + " sec + " + (t2 - t1)/1000 + " sec");
  }

  /**
   * Read set of active name servers from file.
   * @param activesFile
   * @return
   * @throws IOException
   */
  public static HashMap<Integer, Set<Integer>> readActives(String activesFile) throws IOException{
    BufferedReader br = new BufferedReader(new FileReader(activesFile));
    HashMap<Integer, Set<Integer>> nameActives = new HashMap<Integer, Set<Integer>>();
    while (true) {
      String line = br.readLine();
      if (line == null) break;
      String[] tokens = line.split("\\s+");
      int name = Integer.parseInt(tokens[0]);
      HashSet<Integer> actives = new HashSet<Integer>();
      for (int i = 1; i < tokens.length; i++) {
        actives.add(Integer.parseInt(tokens[i]));
      }
      if (actives.contains(NameServer.nodeID))
        nameActives.put(name,actives);
    }
    return nameActives;
  }

  public static int countActives(String activesFile) throws IOException{
    BufferedReader br = new BufferedReader(new FileReader(activesFile));
    int count = 0;
    while (true) {
      String line = br.readLine();
      if (line == null) return count;
      count ++;
    }
  }
      /**
       * This method generates a record table at the name server
       * from a synthetic workload. The workload is a list of
       * integers where the integer's value represents the name
       * and its popularity/rank. The address of the name is the
       * SHA-1 hash of the name
       *
       * @param regularWorkloadSize
       * @param mobileWorkloadSize
       * @param defaultTTLRegularNames
       * @param defaultTTLMobileNames
       * @throws NoSuchAlgorithmException
       */
  public static void generateRecordTableBulkInsert(int regularWorkloadSize, int mobileWorkloadSize,
                                                   int defaultTTLRegularNames,int defaultTTLMobileNames){

    if (StartNameServer.eventualConsistency && StartNameServer.noLoadDB) return;

//		InCoreRecordMap recordMap = new InCoreRecordMap();
//		ConcurrentMap<String, NameRecord> recordTable = new ConcurrentHashMap<String, NameRecord>(
//				(regularWorkloadSize + mobileWorkloadSize + 1), 0.75f, 8);
    // reset the database
    if (StartNameServer.noLoadDB == false) NameServer.resetDB();

//    MessageDigest sha1 = null;
//    try {
//      sha1 = MessageDigest.getInstance("SHA-1");
//    } catch (NoSuchAlgorithmException e) {
//      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//    }
    long t0 = System.currentTimeMillis();
    Random random = new Random();

    ArrayList<JSONObject> rcRecords = new ArrayList<JSONObject>();
    ArrayList<JSONObject> nameRecords = new ArrayList<JSONObject>();
    int batchSize = 10000;
    int totalRecords = 0;

    for (int name = 0; name < (regularWorkloadSize + mobileWorkloadSize); name++) {

      try {
        String strName = Integer.toString(name);
        Set<Integer> primaryNameServer = HashFunction.getPrimaryReplicas(strName);
        if (primaryNameServer.contains(NameServer.nodeID)) {

          if (StartNameServer.debugMode) GNS.getLogger().fine("RecordAdded\tName:\t" + name);
          //generate record
          ReplicaControllerRecord rcRecord = new ReplicaControllerRecord(strName, true);
          rcRecords.add(rcRecord.toJSONObject());

          ValuesMap valuesMap = new ValuesMap();
          int address = random.nextInt();
          valuesMap.put(NameRecordKey.EdgeRecord.getName(), new ResultValue(Arrays.asList(Integer.toString(address))));

          NameRecord nameRecord = new NameRecord(strName, rcRecord.getActiveNameservers(),
                  rcRecord.getActivePaxosID(), valuesMap, defaultTTLRegularNames);
          nameRecords.add(nameRecord.toJSONObject());
          if (StartNameServer.eventualConsistency == false) {
            ListenerReplicationPaxos.createPaxosInstanceForName(rcRecord.getName(), rcRecord.getActiveNameservers(),
                    rcRecord.getActivePaxosID(), valuesMap, 0, defaultTTLRegularNames);
//            ReplicaController.handleNameRecordAddAtPrimary(nameRecordPrimary, valuesMap, 0, defaultTTLRegularNames);
          }
          if (rcRecords.size() == batchSize) {
            if (StartNameServer.noLoadDB == false) {
              NameServer.replicaController.bulkInsertRecords(rcRecords);
              NameServer.recordMap.bulkInsertRecords(nameRecords);
            }
            GNS.getLogger().info("Bulk insert of records. count:" + rcRecords.size() + "\tname: " + name);
            totalRecords += rcRecords.size();
            rcRecords = new ArrayList<JSONObject>();
            nameRecords = new ArrayList<JSONObject>();
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    long t1 = System.currentTimeMillis();
//    GNS.getLogger().severe("Time to add all records " + (t1 - t0)/1000 + " sec");
    if (nameRecords.size() > 0) {
      try {
        if (StartNameServer.noLoadDB == false) {
          NameServer.replicaController.bulkInsertRecords(rcRecords);
          NameServer.recordMap.bulkInsertRecords(nameRecords);
        }
        totalRecords += rcRecords.size();
        GNS.getLogger().info("Last bulk insert of records. count:" + rcRecords.size());
      } catch (RecordExistsException e) {
        e.printStackTrace();
      }
    }
    GNS.getLogger().info("Bulk insert of records. All complete. Total Records: " + totalRecords + "\tTime: "
            + (t1 - t0)/1000 + " sec");
    OutputMemoryUse.outputMemoryUse("BeforeGC " + NameServer.nodeID + ":");
    System.gc();
    OutputMemoryUse.outputMemoryUse("AfterGC " + NameServer.nodeID + ":");
  }



  /**
   * This method generates a record table at the name server
   * from a synthetic workload. The workload is a list of
   * integers where the integer's value represents the name
   * and its popularity/rank. The address of the name is the
   * SHA-1 hash of the name
   *
   * @param regularWorkloadSize
   * @param mobileWorkloadSize
   * @param defaultTTLRegularNames
   * @param defaultTTLMobileNames
   * @throws NoSuchAlgorithmException
   */
  public static void generateRecordTable(int regularWorkloadSize, int mobileWorkloadSize,
                                         int defaultTTLRegularNames,int defaultTTLMobileNames){



//		InCoreRecordMap recordMap = new InCoreRecordMap();
//		ConcurrentMap<String, NameRecord> recordTable = new ConcurrentHashMap<String, NameRecord>(
//				( regularWorkloadSize + mobileWorkloadSize + 1), 0.75f, 8);
    // reset the database
    NameServer.resetDB();

//    MessageDigest sha1 = null;
//    try {
//      sha1 = MessageDigest.getInstance("SHA-1");
//    } catch (NoSuchAlgorithmException e) {
//      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//    }
    long t0 = System.currentTimeMillis();
    Random random = new Random();


    for (int name = 0; name < (regularWorkloadSize + mobileWorkloadSize); name++) {
      if (name%1000 == 0) GNS.getLogger().info("Name complete: " + name);
      try {
        String strName = Integer.toString(name);
        Set<Integer> primaryNameServer = HashFunction.getPrimaryReplicas(strName);

        // Add record into the name server's record table if the name server
        // is the primary replica if this name
        if (StartNameServer.replicationFramework == ReplicationFrameworkType.OPTIMAL) {
//          //Use the SHA-1 hash of the name as its address
//          byte[] hash = HashFunction.SHA(strName, sha1);
//          int address = ByteUtils.BAToInt(hash);
//
//          //Generate an entry for the name and add its record to the name server record table
//          ReplicaControllerRecord nameRecordPrimary = new ReplicaControllerRecord(strName);
//          try {
//            NameServer.addNameRecordPrimary(nameRecordPrimary);
//          } catch (RecordExistsException e) {
//            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//            continue;
//          }
//          long t1 = System.currentTimeMillis();
//          long timeSpent = t1 - t0;
//          long initScoutDelay = 0;
//          if (StartNameServer.paxosStartMinDelaySec > 0 && StartNameServer.paxosStartMaxDelaySec > 0) {
//            initScoutDelay = (StartNameServer.paxosStartMinDelaySec - timeSpent)*1000 + new Random().nextInt(StartNameServer.paxosStartMaxDelaySec*1000 - StartNameServer.paxosStartMinDelaySec*1000);
//          }
//          ValuesMap valuesMap = new ValuesMap();
//          valuesMap.put(NameRecordKey.EdgeRecord.getName(), new ResultValue(Arrays.asList(Integer.toString(address))));
//          try {
//            ReplicaController.handleNameRecordAddAtPrimary(nameRecordPrimary, valuesMap, initScoutDelay, 0);
//          } catch (FieldNotFoundException e) {
//            GNS.getLogger().fine("Field not found exception. " + e.getMessage());
//            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//          }
////					NameRecord recordEntry = new NameRecord( strName, NameRecordKey.EdgeRecord, new ArrayList(Arrays.asList(Integer.toString(address))));
//          //Set a default ttl value for regular and mobile names
////					recordEntry.setTTL(( name <= regularWorkloadSize )? defaultTTLRegularNames : defaultTTLMobileNames);
////					NameServer.addNameRecord(recordEntry);
////					recordMap.addNameRecord(recordEntry);
////					recordTable.put( recordEntry.name, recordEntry );
        } else if (primaryNameServer.contains(NameServer.nodeID)) {
          //Use the SHA-1 hash of the name as its address
//          byte[] hash = HashFunction.SHA(strName, sha1);
          int address = random.nextInt(); //ByteUtils.ByteArrayToInt(hash);
          if (StartNameServer.debugMode) GNS.getLogger().fine("RecordAdded\tName:\t" + name);
          //Generate an entry for the name and add its record to the name server record table
          ReplicaControllerRecord rcRecord = new ReplicaControllerRecord(strName, true);
          try {
            NameServer.addNameRecordPrimary(rcRecord);
          } catch (RecordExistsException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            continue;
          }

//          NameServer.addNameRecord();
//          ReplicaControllerRecord nameRecordPrimary1 = NameServer.getNameRecordPrimary(strName);
//          if (StartNameServer.debugMode) GNS.getLogger().fine("Record checked out: "  + nameRecordPrimary1);

          ValuesMap valuesMap = new ValuesMap();
          valuesMap.put(NameRecordKey.EdgeRecord.getName(), new ResultValue(Arrays.asList(Integer.toString(address))));
          long t1 = System.currentTimeMillis();
          long timeSpent = t1 - t0;
          long initScoutDelay = 0;
          if (StartNameServer.paxosStartMinDelaySec > 0 && StartNameServer.paxosStartMaxDelaySec > 0) {
            if (timeSpent > StartNameServer.paxosStartMaxDelaySec*1000) {
              initScoutDelay = 0;
            } else {
              initScoutDelay = (StartNameServer.paxosStartMinDelaySec*1000 - timeSpent)
                      + new Random().nextInt(StartNameServer.paxosStartMaxDelaySec*1000 - StartNameServer.paxosStartMinDelaySec*1000);
            }
          }
          try {
            ListenerReplicationPaxos.createPaxosInstanceForName(rcRecord.getName(), rcRecord.getActiveNameservers(),
                    rcRecord.getActivePaxosID(), valuesMap, initScoutDelay, defaultTTLRegularNames);
//            ReplicaController.handleNameRecordAddAtPrimary(nameRecordPrimary, valuesMap, initScoutDelay, defaultTTLRegularNames);
          } catch (FieldNotFoundException e) {
            GNS.getLogger().fine("Field not found exception. " + e.getMessage());
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
          }
          if (name%1000 == 0) {
            GNS.getLogger().info("Added record " + name + "\tinit scout delay\t" + initScoutDelay);
          }
//					NameRecord recordEntry = new NameRecord(strName, NameRecordKey.EdgeRecord, new ArrayList(Arrays.asList(Integer.toString(address))));
//
//					//Set a default ttl value for regular and mobile names
//					recordEntry.setTTL(( name <= regularWorkloadSize )? defaultTTLRegularNames : defaultTTLMobileNames);
//					NameServer.addNameRecord(recordEntry);
//          if (StartNameServer.debugMode) GNS.getLogger().fine("Name record added: " + nameRecordPrimary);
//					recordTable.put( recordEntry.name, recordEntry );

//          Thread.sleep(sleepBetweenNames);
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    long t1 = System.currentTimeMillis();
    GNS.getLogger().info(" Time to add all records " + (t1 - t0)/1000 + " sec");

//		if( !debugMode )
//			writeTable( recordTable );
//		return recordTable;
//		return recordMap;
  }

//	private static void writeTable( Map<String, NameRecord> recordTable ) {
//		try {
//			FileWriter fstream = new FileWriter( "ns_table", false );
//			BufferedWriter out = new BufferedWriter( fstream );
//			for( NameRecord nameRecord : recordTable.values() ) {
//				out.write( nameRecord.toString() + "\n" );
//			}
//			out.close();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//	}


  public static void testMongoLookups(String[] args) {
    String configFile = args[0]; //"/Users/abhigyan/Documents/gns_output/local/local_config";
    int names = Integer.parseInt(args[1]);
    int numberRequests = Integer.parseInt(args[2]);//10000;//Integer.parseInt(args[1]);

    String fileName = "longDelayLookups.txt";
    String summaryFilename = "summary.txt";
    boolean loadDB = false;

    int numValues = 1;
    //StartNameServer.mongoPort = 12345;
    NameServer.nodeID = 0;
    StartNameServer.replicationFramework = ReplicationFrameworkType.LOCATION;
    ConfigFileInfo.readHostInfo(configFile, 0);
    GNS.numPrimaryReplicas = GNS.DEFAULT_NUM_PRIMARY_REPLICAS;
    HashFunction.initializeHashFunction();
//    ConfigFileInfo.setNumberOfNameServers(3);
    try{
      new NameServer(0);
    }catch (IOException exception) {
      System.out.println(" IO EXCEPTION _-- " + exception.getMessage());
      exception.printStackTrace();
    }
    if (loadDB){
      NameServer.resetDB();
      addNameRecordsToDB(names,0);
    }
    System.out.println("Name record add complete.");

    Random r = new Random();
    ArrayList<Long> delayTimes = new ArrayList<Long>();
    ArrayList<Long> delays = new ArrayList<Long>();
    long t0 = System.currentTimeMillis();
    for (int i = 0; i < numberRequests; i++) {
      int name = r.nextInt(names);
      long t1 = System.currentTimeMillis();
      try {
        NameRecord record = NameServer.getNameRecord(Integer.toString(name));
      } catch (RecordNotFoundException e) {
        System.out.println("Name record not found. record = " + name);
        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      }
      long t2 = System.currentTimeMillis();
      if (t2 - t1 > 20) {
        System.out.println("Long delay\t"+ i + "\t" + (t2 - t1));
        delayTimes.add(t2 - t0);
        delays.add(t2 - t1);
      }

      if (i > 0 && i % ((numberRequests)/10) == 0) {
        System.out.println(" Request complete = " + i);
        double throughput = (i*1.0)/(t2 - t0)*1000;
        System.out.println("Throughput = " + (int)throughput);
      }
    }

    long t1 = System.currentTimeMillis();
    double throughput = (numberRequests*1.0)/(t1 - t0)*1000;
    System.out.println("\n\nThroughput = " + (int)throughput);

    try {
      FileWriter fileWriter = new FileWriter(fileName);
      for (int i = 0; i < delays.size(); i++) {
        fileWriter.write(delayTimes.get(i) + "\t" + delays.get(i) + "\n");
      }
      fileWriter.close();

      FileWriter fileWriter2 = new FileWriter(summaryFilename);
      fileWriter2.write("Names\t" + names + "\n");
      fileWriter2.write("Lookups\t" + numberRequests + "\n");
      fileWriter2.write("Throughput\t" + throughput + "\n");
      fileWriter2.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

    System.exit(0); // necessary to exit code.

  }

  public static void testMongoLookupsUpdates(String[] args) {
    String configFile = args[0]; //"/Users/abhigyan/Documents/gns_output/local/local_config";
    int names = Integer.parseInt(args[1]);
    int numberRequests = Integer.parseInt(args[2]);//10000;//Integer.parseInt(args[1]);
    double fractionWrites = Double.parseDouble(args[3]);
    String outputFolder = args[4];

    try {
      Runtime.getRuntime().exec("mkdir -p " + outputFolder);
    } catch (IOException e) {
      e.printStackTrace();
    }
    String fileName = outputFolder + "/longDelay.txt";
    String summaryFilename = outputFolder + "/summary.txt";

    boolean loadDB = false;

    int numValues = 1;
    //StartNameServer.mongoPort = 12345;
    NameServer.nodeID = 0;
    StartNameServer.replicationFramework = ReplicationFrameworkType.LOCATION;
    ConfigFileInfo.readHostInfo(configFile, 0);
    GNS.numPrimaryReplicas = GNS.DEFAULT_NUM_PRIMARY_REPLICAS;
    HashFunction.initializeHashFunction();
//    ConfigFileInfo.setNumberOfNameServers(3);
    try{
      new NameServer(0);
    }catch (IOException exception) {
      System.out.println(" IO EXCEPTION _-- " + exception.getMessage());
      exception.printStackTrace();
    }
    if (loadDB){
      NameServer.resetDB();
      addNameRecordsToDB(names,0);
      System.out.println("Name record add complete.");
    }


    Random r = new Random();
    ArrayList<Long> delayTimes = new ArrayList<Long>();
    ArrayList<Long> delays = new ArrayList<Long>();
    ArrayList<Boolean> write = new ArrayList<Boolean>();
    long t0 = System.currentTimeMillis();
    for (int i = 0; i < numberRequests; i++) {

      int nameInt = r.nextInt(names);
      long t1 = System.currentTimeMillis();
      double f = r.nextDouble();

      if (f < fractionWrites) {
        doUpdate(nameInt);
      } else {
        doLookup(nameInt);
      }
      long t2 = System.currentTimeMillis();
      if (t2 - t1 > 20) {
        System.out.println("Long delay\t"+ i + "\t" + (t2 - t1));
        delayTimes.add(t2 - t0);
        delays.add(t2 - t1);
        if (f < fractionWrites) write.add(true);
        else write.add(false);
      }

      if (i > 0 && i % ((numberRequests)/10) == 0) {
        System.out.println(" Request complete = " + i);
        double throughput = (i*1.0)/(t2 - t0)*1000;
        System.out.println("Throughput = " + (int)throughput);
        try {
          FileWriter fileWriter = new FileWriter(fileName);
          for (int j = 0; j < delays.size(); j++) {
            fileWriter.write(delayTimes.get(j) + "\t" + delays.get(j) + "\t" + write.get(j) + "\n");
          }
          fileWriter.close();

          FileWriter fileWriter2 = new FileWriter(summaryFilename);
          fileWriter2.write("Names\t" + names + "\n");
          fileWriter2.write("Requests\t" + numberRequests + "\n");
          fileWriter2.write("WriteFraction\t" + fractionWrites + "\n");
          fileWriter2.write("Throughput\t" + throughput + "\n");
          fileWriter2.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }

    long t1 = System.currentTimeMillis();
    double throughput = (numberRequests*1.0)/(t1 - t0)*1000;
    System.out.println("\n\nThroughput = " + (int)throughput);

    try {
      FileWriter fileWriter = new FileWriter(fileName);
      for (int i = 0; i < delays.size(); i++) {
        fileWriter.write(delayTimes.get(i) + "\t" + delays.get(i) + "\t" + write.get(i) + "\n");
      }
      fileWriter.close();

      FileWriter fileWriter2 = new FileWriter(summaryFilename);
      fileWriter2.write("Names\t" + names + "\n");
      fileWriter2.write("Requests\t" + numberRequests + "\n");
      fileWriter2.write("WriteFraction\t" + fractionWrites + "\n");
      fileWriter2.write("Throughput\t" + throughput + "\n");
      fileWriter2.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    System.exit(0); // necessary to exit code.
  }

  private static void doLookup(int nameInt) {

    try {
      NameRecord record = NameServer.getNameRecord(Integer.toString(nameInt));
    } catch (RecordNotFoundException e) {
      System.out.println("Name record not found. record = " + nameInt);
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    }

  }

  private static void doUpdate(int nameInt) {
    NameRecord record = new NameRecord(Integer.toString(nameInt));
    ResultValue value = new ResultValue();
    value.add(Util.randomString(10));
    try {
      record.updateKey(NameRecordKey.EdgeRecord.getName(), value, null, UpdateOperation.REPLACE_ALL);
    } catch (FieldNotFoundException e) {
      e.printStackTrace();
    }
  }

  public static void testMongoUpdates(String[] args) {
    String configFile = args[0]; //"/Users/abhigyan/Documents/gns_output/local/local_config";
    int names = Integer.parseInt(args[1]);
    int numberRequests = Integer.parseInt(args[2]);//10000;//Integer.parseInt(args[1]);

    String fileName = "longDelayUpdates.txt";
    String summaryFilename = "summary.txt";
    boolean loadDB = false;

    int numValues = 1;
    //StartNameServer.mongoPort = 12345;
    NameServer.nodeID = 0;
    StartNameServer.replicationFramework = ReplicationFrameworkType.LOCATION;
    ConfigFileInfo.readHostInfo(configFile, 0);
    GNS.numPrimaryReplicas = GNS.DEFAULT_NUM_PRIMARY_REPLICAS;
    HashFunction.initializeHashFunction();
//    ConfigFileInfo.setNumberOfNameServers(3);
    try{
      new NameServer(0);
    }catch (IOException exception) {
      System.out.println(" IO EXCEPTION _-- " + exception.getMessage());
      exception.printStackTrace();
    }
    if (loadDB){
      NameServer.resetDB();
      addNameRecordsToDB(names,0);
    }
    System.out.println("Name record add complete.");

//    try {
//      record = NameServer.getNameRecord(Integer.toString(0));
//    } catch (RecordNotFoundException e) {
//      System.out.println("Record does not exist");
//      System.exit(2);
//      e.printStackTrace();
//    }

    long t0 = System.currentTimeMillis();
//    Random random = new Random();
    ArrayList<Long> delayTimes = new ArrayList<Long>();
    ArrayList<Long> delays = new ArrayList<Long>();
    for (int i = 0; i < numberRequests; i++) {
      // we can add mongo records to this
//      int nameInt = random.nextInt(names);
      int nameInt = i % names;
      NameRecord record = new NameRecord(Integer.toString(nameInt));
      ResultValue value = new ResultValue();
      value.add(Util.randomString(10));
      long t1 = System.currentTimeMillis();
      try {
        record.updateKey(NameRecordKey.EdgeRecord.getName(), value, null, UpdateOperation.REPLACE_ALL);
      } catch (FieldNotFoundException e) {
        e.printStackTrace();
      }
      long t2 = System.currentTimeMillis();
      if (t2 - t1 > 20) {
        System.out.println("Long delay\t"+ i + "\t" + (t2 - t1));
        delayTimes.add(t2 - t0);
        delays.add(t2 - t1);
      }
//      record.setValuesMap(getValuesMapSynthetic(numValues));
//      NameServer.updateNameRecord(record);

      if (i > 0 && i % (numberRequests/10) == 0) {
        System.out.println(" Request complete = " + i);

        double throughput = (i*1.0)/(t2 - t0)*1000;
        System.out.println("Throughput = " + (int)throughput);
      }
    }

    long t1 = System.currentTimeMillis();
    double throughput = (numberRequests*1.0)/(t1 - t0)*1000;
    System.out.println("\n\nThroughput = " + (int)throughput);

    try {
      FileWriter fileWriter = new FileWriter(fileName);
      for (int i = 0; i < delays.size(); i++) {
        fileWriter.write(delayTimes.get(i) + "\t" + delays.get(i) + "\n");
      }
      fileWriter.close();

      FileWriter fileWriter2 = new FileWriter(summaryFilename);
      fileWriter2.write("Names\t" + names + "\n");
      fileWriter2.write("Updates\t" + numberRequests + "\n");
      fileWriter2.write("Throughput\t" + throughput + "\n");
      fileWriter2.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    System.exit(0); // necessary to exit code.
  }

  private static ValuesMap getValuesMapSynthetic(int numValues) {
    ValuesMap valuesMap =new ValuesMap();
    ResultValue value = new ResultValue();

    for (int i = 0; i < numValues; i++)
      value.add(randomString(10));
    valuesMap.put(NameRecordKey.EdgeRecord.getName(), value);
    return valuesMap;
  }

  public static void addNameRecordsToDB(int regularWorkloadSize, int mobileWorkloadSize) {
    for (int name = 0; name < (regularWorkloadSize + mobileWorkloadSize); name++) {
      String strName = Integer.toString(name);
      int numValues = 1;
      ValuesMap valuesMap = getValuesMapSynthetic(numValues);

      NameRecord nameRecord = new NameRecord(strName,ConfigFileInfo.getAllNameServerIDs(),strName+"-2",valuesMap, 0);
//      nameRecord.handleNewActiveStart(ConfigFileInfo.getAllNameServerIDs(),
//              strName +"-2", valuesMap);
      // first add name record, then create paxos instance for it.
      try {
        NameServer.addNameRecord(nameRecord);
      } catch (RecordExistsException e) {
        GNS.getLogger().warning("Name record already exists. Name = " + strName);
        e.printStackTrace();
      }
      if (name > 0 && name % ((regularWorkloadSize + mobileWorkloadSize)/10) == 0) {
        System.out.println(" Name added = " + name);
      }
    }
  }

  /**
   * Test
   *
   * @throws NoSuchAlgorithmException *
   */
  public static void main(String[] args) throws NoSuchAlgorithmException {
    testMongoLookupsUpdates(args);
//    testMongoLookups(args);
//    testMongoUpdates(args);

//    ConfigFileInfo.readHostInfo("/Users/hardeep/Desktop/Workspace/PlanetlabScripts/src/Ping/name_server_ssh_local", 119);
//		ConcurrentMap<String, NameRecord> table = generateRecordTable( 5000, 10000, 240, 10 );
//		System.out.println( table.size() );
//		System.out.println( table.get("1"));

//		NameServer.nodeID = 3;
//		ConcurrentMap<String, NameRecord> table2 = generateRecordTable( 10, 5, 2, 3 );
//		System.out.println( table2 );
  }

}

