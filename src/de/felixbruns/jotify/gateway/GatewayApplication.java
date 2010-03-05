package de.felixbruns.jotify.gateway;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.Filter.Chain;

import de.felixbruns.jotify.gateway.handlers.*;
import de.felixbruns.jotify.gateway.util.URIUtilities;
import de.felixbruns.jotify.protocol.channel.ChannelCallback;

public class GatewayApplication {
	public static Map<String, GatewayConnection> sessions;
	public static ExecutorService                executor;

	private static Filter consoleSessionFilter;
	private static Filter requestLoggingFilter;

	private static Logger log = Logger.getLogger(GatewayApplication.class.getName());

	public static String commandLineSession = null;
	
	/* Statically create session map and executor for sessions. */
	static {
		sessions = new HashMap<String, GatewayConnection>();
		executor = Executors.newCachedThreadPool();

		consoleSessionFilter = new Filter() {
			public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
				Map<String, String> params = URIUtilities.parseQuery(exchange);
				if(!params.containsKey("session") && GatewayApplication.commandLineSession != null) {
					exchange.setAttribute("session", GatewayApplication.commandLineSession);
				}

				chain.doFilter(exchange);
			}

			public String description() { return "Adds command line session if missing"; }
		};

		requestLoggingFilter = new Filter() {
			public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
				System.out.println("[-] " + exchange.getRequestMethod() + " " + exchange.getRequestURI() +
						" -> " + exchange.getHttpContext().getHandler().getClass().getSimpleName() +
						(exchange.getAttribute("session") != null ? " (using command line session)" : ""));
				chain.doFilter(exchange);
			}

			public String description() { return "Logging filter"; }
		};
	}

	private static void addContextToServer(String path, HttpHandler handler, HttpServer server) {
		HttpContext context = server.createContext(path, handler);
		List<Filter> filters = context.getFilters();
		filters.add(consoleSessionFilter);
		filters.add(requestLoggingFilter);
	}

	/* Main thread to listen for client connections. */
	public static void main(String[] args) throws IOException {
		int port = 8080;
		
		if(args.length > 0){
			port = Integer.parseInt(args[0]);
		}
		
		/* Create a HTTP server that listens for connections on port 8080 or the given port. */
		HttpServer server = null;
		try {
			server = HttpServer.create(new InetSocketAddress(port), 0);
		} catch (Exception e) {
			System.err.println("Could not start HTTP server: " + e.getMessage());
			System.exit(1);
		}
		
		/* Set up content handlers. */
		addContextToServer("/",       new ContentHandler(), server);
		addContextToServer("/images", new ContentHandler(), server);
		
		/* Set up gateway handlers. */
		addContextToServer("/start",     new StartHandler(), server);
		addContextToServer("/check",     new CheckHandler(), server);
		addContextToServer("/close",     new CloseHandler(), server);
		addContextToServer("/user",      new UserHandler(), server);
		addContextToServer("/toplist",   new ToplistHandler(), server);
		addContextToServer("/search",    new SearchHandler(), server);
		addContextToServer("/image",     new ImageHandler(), server);
		addContextToServer("/browse",    new BrowseHandler(), server);
		addContextToServer("/playlist",  new PlaylistHandler(), server);
		addContextToServer("/playlists", new PlaylistsHandler(), server);
		addContextToServer("/stream",    new StreamHandler(), server);
		
		/* Play on server. */
		addContextToServer("/play",   new PlayHandler(), server);
		addContextToServer("/pause",  new PauseHandler(), server);
		addContextToServer("/stop",   new StopHandler(), server);
		//addContextToServer("/volume", new VolumeHandler());

		/* Set up login handler and check for login parameters on command line */
		LoginHandler loginHandler = new LoginHandler();
		addContextToServer("/login", loginHandler, server);

		if(args.length > 2){
			Map<String, String> loginParameters = new HashMap<String, String>();
			loginParameters.put("username", args[1]);
			loginParameters.put("password", args[2]);
			String result = loginHandler.handle(loginParameters);
			String cleanedResult = result.replaceAll("<.*?>", "");
			if (result.indexOf("<error>") == -1) {
				System.out.println("Successfully logged in as user '" + args[1] + "'");
				log.fine("Session key is: " + cleanedResult);
				commandLineSession = cleanedResult;
			} else {
				System.err.println("Failed to log in: " + cleanedResult);
				System.exit(1);
			}
		}
		
		/* Set executor for server threads. */
		server.setExecutor(executor);
		
		/* Start HTTP server. */
		server.start();
	}
}
