package de.felixbruns.jotify.gateway.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;

import de.felixbruns.jotify.gateway.GatewayApplication;

public class URIUtilities {
	public static Map<String, String> parseQuery(String query){
		Map<String, String> map = new HashMap<String, String>();
		
		if(query == null){
			return map;
		}
		
		String[] params = query.split("&");
		
		for(String param : params){
			String[] nameAndValue = param.split("=");
			if (nameAndValue.length != 2 || nameAndValue[0].isEmpty())
				continue;
			
			map.put(nameAndValue[0], nameAndValue[1]);
		}
		
		return map;
	}

	public static Map<String, String> parseQuery(HttpExchange exchange) throws IOException {
		String requestMethod = exchange.getRequestMethod();
		String requestQuery  = exchange.getRequestURI().getQuery();

		Map<String, String> params = new HashMap<String, String>();

		if(requestMethod.equalsIgnoreCase("GET")){
			params = parseQuery(requestQuery);
		}
		else if(requestMethod.equalsIgnoreCase("POST")){
			InputStream    input   = exchange.getRequestBody();
			BufferedReader reader  = new BufferedReader(new InputStreamReader(input));
			StringBuilder  builder = new StringBuilder();
			String         line;

			/* Convert input stream to string. */
			while((line = reader.readLine()) != null){
				builder.append(line);
			}

			/* Close input stream. */
			input.close();

			/* Parse query. */
			params = parseQuery(builder.toString());
		}

		if(exchange.getAttribute("session") != null) {
			params.put("session", (String)exchange.getAttribute("session"));
		}

		return params;
	}
}
