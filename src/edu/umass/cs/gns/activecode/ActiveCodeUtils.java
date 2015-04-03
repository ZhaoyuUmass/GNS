package edu.umass.cs.gns.activecode;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.Base64;

import edu.umass.cs.gns.activecode.protocol.ActiveCodeMessage;

public class ActiveCodeUtils {
	public static byte[] serializeObject(Object o) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		
		try {
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(o);
			oos.flush();
			oos.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return baos.toByteArray();
	}
	
	public static Object deserializeObject(byte[] data) {
		ByteArrayInputStream bais = new ByteArrayInputStream(data);
	    Object o = null;
		try {
			ObjectInputStream oin = new ObjectInputStream(bais);
			o = oin.readObject();
		} catch (IOException | ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    return o;
	}
	
	public static void sendMessage(PrintWriter out, ActiveCodeMessage acm) {
		byte[] data = serializeObject(acm);
		String data64 = Base64.getEncoder().encodeToString(data);
		out.println(data64);
	}
	
	public static ActiveCodeMessage getMessage(BufferedReader in) throws IOException {
		String res64 = in.readLine();
		
		if(res64 == null) {
			//ActiveCodeMessage acm = new ActiveCodeMessage();
			//acm.finished = true;
			//return acm;
			return new ActiveCodeMessage();
		}
		
		byte[] res = Base64.getDecoder().decode(res64);
	    return (ActiveCodeMessage) deserializeObject(res);
	}
}
