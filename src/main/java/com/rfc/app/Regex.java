package com.rfc.app;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bson.types.ObjectId;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.firefox.internal.ProfilesIni;

public class Regex {
	
	public static void main(String[] args) {
		Pattern p = Pattern.compile("^showing\\s*\\d+\\s*-\\s*(\\d+)\\s*of\\s*(\\d+)\\s*results",Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	    Matcher m = p.matcher("showing   1-30 of 1234444         results");
	    if (m.find()) {
	    	System.out.println(m.group(1));
	    	System.out.println(m.group(2));
//	    	System.out.println(m.group(3));
	    	
	    	
	    	
	    	
	    }
	    
	    
	    String[] sp = "http://www.yellowpages.com/listings/187450127/menu".split("/");
	    System.out.println(sp.length);
	    System.out.println(sp[sp.length - 1]);
	    System.out.println(sp[sp.length - 2]);
	    System.out.println("----------------");
	    for (String string : sp) {
			System.out.println(string);
		}
	    
	    
	    ObjectId id = ObjectId.get();
	    System.out.println(id.getTime());
	    
	    FirefoxProfile profile = new ProfilesIni().getProfile("defaultfff");
	    
	    System.out.println(profile);
	    
	}

}
