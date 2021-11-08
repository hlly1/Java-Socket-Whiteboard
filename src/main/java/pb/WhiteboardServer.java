package pb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;


import pb.app.Whiteboard;
import pb.app.WhiteboardApp;
import pb.managers.IOThread;
import pb.managers.ServerManager;
import pb.managers.endpoint.Endpoint;
import pb.utils.Utils;

/**
 * Simple whiteboard server to provide whiteboard peer notifications.
 * @author aaron
 *
 */
public class WhiteboardServer {
	private static Logger log = Logger.getLogger(WhiteboardServer.class.getName());
	public static HashMap<String, Object> currentSharingBoard = new HashMap<>();
	public static HashMap<Endpoint, ArrayList<String>> endpointBoardsMapping = new HashMap<>();
	public static ArrayList<Endpoint> currentEndpoints = new ArrayList();

	/**
	 * Emitted by a client to tell the server that a board is being shared. Argument
	 * must have the format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String shareBoard = "SHARE_BOARD";

	/**
	 * Emitted by a client to tell the server that a board is no longer being
	 * shared. Argument must have the format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String unshareBoard = "UNSHARE_BOARD";

	/**
	 * The server emits this event:
	 * <ul>
	 * <li>to all connected clients to tell them that a board is being shared</li>
	 * <li>to a newly connected client, it emits this event several times, for all
	 * boards that are currently known to be being shared</li>
	 * </ul>
	 * Argument has format "host:port:boardid"
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String sharingBoard = "SHARING_BOARD";

	/**
	 * The server emits this event:
	 * <ul>
	 * <li>to all connected clients to tell them that a board is no longer
	 * shared</li>
	 * </ul>
	 * Argument has format "host:port:boardid"
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String unsharingBoard = "UNSHARING_BOARD";

	/**
	 * Emitted by the server to a client to let it know that there was an error in a
	 * received argument to any of the events above. Argument is the error message.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String error = "ERROR";
	
	/**
	 * Default port number.
	 */
	private static int port = Utils.indexServerPort;
	
	
	
	private static void help(Options options){
		String header = "PB Whiteboard Server for Unimelb COMP90015\n\n";
		String footer = "\ncontact aharwood@unimelb.edu.au for issues.";
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("pb.IndexServer", header, options, footer, true);
		System.exit(-1);
	}
	
	public static void main( String[] args ) throws IOException, InterruptedException
    {
    	// set a nice log format
		System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tl:%1$tM:%1$tS:%1$tL] [%4$s] %2$s: %5$s%n");
        
    	// parse command line options
        Options options = new Options();
        options.addOption("port",true,"server port, an integer");
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

        // create a server manager and setup event handlers
        ServerManager serverManager;
        
        if(cmd.hasOption("password")) {
        	serverManager = new ServerManager(port,cmd.getOptionValue("password"));
        } else {
        	serverManager = new ServerManager(port);
        }
        
        /**
         * TODO: Put some server related code here.
         */
        // start up the server
        log.info("Whiteboard Server starting up");

		serverManager.on(ServerManager.sessionStarted,(eventArgs)->{
			Endpoint endpoint = (Endpoint)eventArgs[0];
			log.info("!!!!!!!!!!!!!Endpoint"+endpoint.getOtherEndpointId());
			currentEndpoints.add(endpoint);
			log.info("Client session started: "+endpoint.getOtherEndpointId());
			if (currentSharingBoard.size() != 0) {
				log.info("Sending current shared board to new peer.");
			}

			for (HashMap.Entry<String, Object> sharedBoard: currentSharingBoard.entrySet()){
				endpoint.emit(sharingBoard, sharedBoard.getValue());
			}

			endpoint.on(shareBoard, args1 -> {
				log.info("This board is shared");
				String boardName =  WhiteboardApp.getBoardName((String)args1[0]);
				currentSharingBoard.put(boardName, args1[0]);
				if (endpointBoardsMapping.get(endpoint)==null){
					endpointBoardsMapping.put(endpoint, new ArrayList<>());
				}
				endpointBoardsMapping.get(endpoint).add(boardName);
				for (Endpoint endpoint1:currentEndpoints) {
					if (!endpoint1.equals(endpoint)) {
						endpoint1.emit(sharingBoard, args1);
					}
				}
			}).on(unshareBoard, args1 -> {
				log.info("This board is not shared");
				String boardName =  WhiteboardApp.getBoardName((String)args1[0]);
				currentSharingBoard.remove(boardName);
				endpointBoardsMapping.get(endpoint).remove(boardName);
				for (Endpoint endpoint1:currentEndpoints) {
					if (!endpoint1.equals(endpoint)){
						endpoint1.emit(unsharingBoard, args1);
					}
				}
			}).on(WhiteboardApp.boardUndoUpdate, args1 -> {
				currentSharingBoard.put(WhiteboardApp.getBoardName((String)args1[0]), args1[0]);
				for (Endpoint endpoint1:currentEndpoints) {
					if (!endpoint1.equals(endpoint)){
						endpoint1.emit(WhiteboardApp.boardUndoAccepted, args1);
					}
				}

			}).on(WhiteboardApp.boardPathUpdate, args1 -> {
				String updatedBoardName =  WhiteboardApp.getBoardName((String)args1[0]);
				String originalData = (String) currentSharingBoard.get(updatedBoardName);
				String latestPaths = WhiteboardApp.getBoardPaths(originalData)+ "%" + WhiteboardApp.getBoardPaths((String)args1[0]);
				String latestData = updatedBoardName + "%" + WhiteboardApp.getBoardVersion((String)args1[0])+ "%"+latestPaths;
				currentSharingBoard.put(WhiteboardApp.getBoardName((String)args1[0]), latestData);
				for (Endpoint endpoint1:currentEndpoints) {
					if (!endpoint1.equals(endpoint)){
						endpoint1.emit(WhiteboardApp.boardPathAccepted, args1);
					}
				}
			}).on(WhiteboardApp.boardClearUpdate, args1 -> {
                currentSharingBoard.put(WhiteboardApp.getBoardName((String)args1[0]), args1[0]);
                for (Endpoint endpoint1:currentEndpoints) {
                    if (!endpoint1.equals(endpoint)){
                        endpoint1.emit(WhiteboardApp.boardClearAccepted, args1);
                    }
                }
            }).on(WhiteboardApp.boardDeleted, args1 -> {
				String boardName =  WhiteboardApp.getBoardName((String)args1[0]);
				currentSharingBoard.remove(boardName);

				for (Endpoint endpoint1:currentEndpoints) {
					if (!endpoint1.equals(endpoint)){
						endpoint1.emit(WhiteboardApp.boardDeletedAccepted, args1);
					}
				}
			});

		}).on(ServerManager.sessionStopped,(eventArgs)->{
			Endpoint endpoint = (Endpoint)eventArgs[0];
			currentEndpoints.remove(endpoint);
			for (String boardName : endpointBoardsMapping.get(endpoint)){
				for (Endpoint endpoint1:currentEndpoints) {
					endpoint1.emit(unsharingBoard, currentSharingBoard.get(boardName));
				}
				currentSharingBoard.remove(boardName);
			}
			log.info("Client session ended: "+endpoint.getOtherEndpointId());

		}).on(ServerManager.sessionError, (eventArgs)->{
			Endpoint endpoint = (Endpoint)eventArgs[0];
			currentEndpoints.remove(endpoint);
			if (endpointBoardsMapping.get(endpoint)!= null) {
				for (String boardName : endpointBoardsMapping.get(endpoint)) {
					for (Endpoint endpoint1 : currentEndpoints) {
						endpoint1.emit(unsharingBoard, currentSharingBoard.get(boardName));
					}
					currentSharingBoard.remove(boardName);
				}
			}
			log.warning("Client session ended in error: "+endpoint.getOtherEndpointId());
		}).on(IOThread.ioThread, (eventArgs)->{
			String peerport = (String) eventArgs[0];
			// we don't need this info, but let's log it
			log.info("using Internet address: "+peerport);
		});


        serverManager.start();
        // nothing more for the main thread to do
        serverManager.join();
        Utils.getInstance().cleanUp();
        
    }

}
