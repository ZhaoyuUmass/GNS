/*
 * Copyright (C) 2014
 * University of Massachusetts
 * All Rights Reserved 
 *
 * Initial developer(s): Westy.
 */
package edu.umass.cs.gns.util;


import edu.umass.cs.gns.newApp.clientCommandProcessor.commandSupport.GnsProtocolDefs;
import java.io.Serializable;

/**
 * This class describes the error codes for Name Server packets that
 * get sent back to the LNS and the client.
 *
 * These codes are used by the Nameserver to communicate via response packets why a value
 * could not be returned from the Nameserver to the client.
 *
 * To save space in packets, ahem, we also provide the ability to convert back and forth between
 * each code and an integer version of it.
 *
 * @author Westy
 *
 */
public enum NSResponseCode implements Serializable{

  NO_ERROR(0, "", false),
  // This should be used sparingly, if at all because it doesn't convey enough information.
  ERROR(1, GnsProtocolDefs.GENERICERROR, true),
  // Field in a record was not found.
  FIELD_NOT_FOUND_ERROR(2, GnsProtocolDefs.FIELDNOTFOUND, true),
  FAIL_ACTIVE_NAMESERVER(3, GnsProtocolDefs.FAIL_ACTIVE_NAMESERVER, true),
  ERROR_INVALID_ACTIVE_NAMESERVER(4, GnsProtocolDefs.INVALID_ACTIVE_NAMESERVER, true),
  // These next four following are access or signature errors
  SIGNATURE_ERROR(5, GnsProtocolDefs.BADSIGNATURE, true),
  ACCESS_ERROR(6, GnsProtocolDefs.ACCESSDENIED, true),
  BAD_GUID_ERROR(7, GnsProtocolDefs.BADGUID, true),
  BAD_ACCESSOR_ERROR(8, GnsProtocolDefs.BADACCESSORGUID, true),
  // An error during account guid verification.
  VERIFICATION_ERROR(9, GnsProtocolDefs.VERIFICATIONERROR, true),
  UPDATE_TIMEOUT(10, GnsProtocolDefs.UPDATETIMEOUT, true),
  DUPLICATE_ERROR(11, GnsProtocolDefs.DUPLICATENAME, true)
  ;
  
  //
  // stash the codes in a lookup table
  private static NSResponseCode[] responseCodes;

  static {
    responseCodes = new NSResponseCode[NSResponseCode.values().length];
    for (NSResponseCode code : NSResponseCode.values()) {
      responseCodes[code.getCodeValue()] = code;
    }
  }

  /**
   * Converts the integer response code value into a ResponseCode object.
   * The integer response codes are used to encode the response code when there are sent in JSON encoded packets.
   * 
   * @param codeValue
   * @return 
   */
  public static NSResponseCode getResponseCode(int codeValue) {
    return responseCodes[codeValue];
  }
  //
  private final int codeValue;
  private final String protocolCode;
  private final boolean isAnError;

  private NSResponseCode(int codeValue, String protocolCode, boolean isAnError) {
    this.codeValue = codeValue;
    this.protocolCode = protocolCode;
    this.isAnError = isAnError;
  }

  /**
   * Returns the integer equivalent of the code.
   *
   * @return
   */
  public int getCodeValue() {
    return codeValue;
  }

  /**
   * Returns the string that corresponds to this error in the client protocol.
   *
   * @return
   */
  public String getProtocolCode() {
    return protocolCode;
  }

  /**
   * Is this an error code. Some aren't, some are.
   *
   * @return
   */
  public boolean isAnError() {
    return isAnError;
  }

}
