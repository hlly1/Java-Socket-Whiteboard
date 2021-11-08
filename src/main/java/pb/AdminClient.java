package pb;

import java.io.IOException;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import pb.managers.ClientManager;
import pb.managers.ServerManager;
import pb.managers.endpoint.Endpoint;
import pb.utils.Utils;

/**
 * Admin Client main. Parse command line options and provide default values.
 * TODO: For project 2A, modify this client to take command line options
 * -shutdown, -forceShutdown and -vaderShutdown. The client should send the
 * appropriate event over the event protocol and then simply stop the session
 * and terminate. Make sure the client does not send the event until the
 * SESSION_STARTED event has been emitted, etc. And the client should attempt to
 * cleanly terminate, not just system exit.
 * 
 * @see {@link pb.managers.ClientManager}
 * @see {@link pb.utils.Utils}
 * @author aaron
 *
 */
public class AdminClient  {
	private static Logger log = Logger.getLogger(AdminClient.class.getName());
	private static int port=Utils.serverPort; // default port number for the server
	private static String host=Utils.serverHost; // default host for the server
	
	private static void help(Options options){
		String header = "PB Admin Client for Unimelb COMP90015\n\n";
		String footer = "\ncontact aharwood@unimelb.edu.au for issues.";
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("pb.Client", header, options, footer, true);
		System.exit(-1);
	}
	
	public static void main( String[] args ) throws IOException, InterruptedException
    {
    	// set a nice log format
		System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tl:%1$tM:%1$tS:%1$tL] %2$s %4$s: %5$s%n");
        
    	// parse command line options
        Options options = new Options();
        options.addOption("port",true,"server port, an integer");
        options.addOption("host",true,"hostname, a string");
        options.addOption("shutdown",false,"shutdown the server");
        options.addOption("force",false,"in conjuction with shutdown, asking sessions to stop");
        options.addOption("vader",false,"in conjuction with shutdown, closing endpoints immediately");
        options.addOption("password",true,"password for server");
        
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
			cmd = parser.parse( options, args);
		} catch (ParseException e1) {
			help(options);
		}
        
        if(cmd.hasOption("port")){
        	try{
        		port = Integer.parseInt(cmd.getOptionValue("port"));
			} catch (NumberFormatException e){
				System.out.println("-port requires a port number, parsed: "+cmd.getOptionValue("port"));
				help(options);
			}
        }
        
        if(cmd.hasOption("host")) {
        	host = cmd.getOptionValue("host");
        }
        
        // start up the client
        log.info("PB Client starting up");
        final CommandLine cmd2 = cmd;
        // the client manager will make a connection with the server
        // and the connection will use a thread that prevents the JVM
        // from terminating immediately
        ClientManager clientManager = new ClientManager(host,port);
        clientManager.on(ClientManager.sessionStarted, (eventArgs)->{
        	Endpoint endpoint = (Endpoint) eventArgs[0];
        	if(cmd2.hasOption("shutdown")) {
        		String password="";
        		if(cmd2.hasOption("password")) {
        			password=cmd2.getOptionValue("password");
        		} else {
        			System.out.println("using a blank password");
        		}
	        	if(cmd2.hasOption("force")) {
	        		endpoint.emit(ServerManager.forceShutdownServer, password);
	        	} else if(cmd2.hasOption("vader")) {
	        		endpoint.emit(ServerManager.vaderShutdownServer, password);
	        	} else {
	        		endpoint.emit(ServerManager.shutdownServer, password);
	        	}
        	} else {
        		System.out.println("not shutting down server");
        	}
        	// nothing more to do
        	clientManager.shutdown();
        }).on(ClientManager.sessionStopped, (eventArgs)->{
        	log.info("session stopped");
        }).on(ClientManager.sessionError, (eventArgs)->{
        	log.info("session stopped in error");
        });
        clientManager.start();
        // nothing more to do but wait for client to finish
        clientManager.join();
        Utils.getInstance().cleanUp();
    }
}
