package com.ds.dht;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Map;

/**
 * The role of RingStabilizer is to keep the finger table up-to-date and to make
 * sure that the predecessor and successor are correct.
 *
 */
public class RingStabilizer extends Thread {

	private Node currentNode;
	private int delaySeconds = 10000;

	public RingStabilizer(Node node) {
		this.currentNode = node;
	}

	/**
	 * Method that periodically runs to determine if node's successor is
	 * uptodate by contacting the listed successor and asking for its
	 * predecessor.
	 */
	public void run() {
		try {
			// Initially sleep
			Thread.sleep(delaySeconds);

			Socket socket = null;
			PrintWriter socketWriter = null;
			BufferedReader socketReader = null;

			while (true) {
				// Only open a connection to the successor if it is not
				// ourselves
				try{
				if (!currentNode.getNodeIpAddress().equals(currentNode.getSuccessor1().getAddress())
						|| (currentNode.getPort() != currentNode.getSuccessor1().getPort())) {
					System.out.println("StabilizeProtocol will run now.");
					
					//for multiple nodes, successor1 and successor2 cannot be equal
					updateSuccessors();//by priya - initial successor1 & 2 updation 
					
					// Open socket to successor
					socket = new Socket(currentNode.getSuccessor1().getAddress(),
							currentNode.getSuccessor1().getPort());

					// Open reader/writer to chord node
					socketWriter = new PrintWriter(socket.getOutputStream(), true);
					socketReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

					// Submit a request for the predecessor
					socketWriter.println(DHTMain.REQUEST_PREDECESSOR + ":" + currentNode.getNodeId() + " asking "
							+ currentNode.getSuccessor1().getNodeId());
							// System.out.println("Sent: " +
							// DHTMain.REQUEST_PREDECESSOR + ":" +
							// currentNode.getNodeId()
							// + " asking " +
							// currentNode.getSuccessor1().getNodeId());

					// Read response from chord
					String serverResponse = socketReader.readLine();
					// System.out.println("Received: " + serverResponse);

					// Parse server response for address and port
					String[] predecessorFragments = serverResponse.split(":");
					String predecessorAddress = predecessorFragments[0];
					int predecessorPort = Integer.valueOf(predecessorFragments[1]);

					// If the address:port that was returned from the server
					// response is
					// not ourselves then we need to adopt it as our new
					// successor ( Checking if I am still the predecessor for my
					// Successor, if not update my finger table)
					if (!currentNode.getNodeIpAddress().equals(predecessorAddress)
							|| (currentNode.getPort() != predecessorPort)) {
						currentNode.lock();

						Finger newSuccessor = new Finger(predecessorAddress, predecessorPort);

						// Update finger table entries to reflect new successor
						currentNode.getFingerTable().put(1, currentNode.getFingerTable().get(0));
						currentNode.getFingerTable().put(0, newSuccessor);

						// Update successor entries to reflect new successor//commmented by priya
						////currentNode.setSuccessor2(currentNode.getFingerTable().get(1));
						////currentNode.setSuccessor1(currentNode.getFingerTable().get(0));
						
						currentNode.unlock();

						// Close connections
						socketWriter.close();
						socketReader.close();
						socket.close();
						
						// Update successor entries to reflect new successor
						updateSuccessors();//by priya
						

						// Inform new successor that I am your predecessor now
						socket = new Socket(newSuccessor.getAddress(), newSuccessor.getPort());

						// Open writer/reader to new successor node
						socketWriter = new PrintWriter(socket.getOutputStream(), true);
						socketReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

						// Tell successor that this node is its new predecessor
						socketWriter.println(DHTMain.NEW_PREDECESSOR + ":" + currentNode.getNodeIpAddress() + ":"
								+ currentNode.getPort());
						System.out.println("Sent: " + DHTMain.NEW_PREDECESSOR + ":" + currentNode.getNodeIpAddress()
								+ ":" + currentNode.getPort());

						// Redistribute key value pair from new successor to
						// itself
						/***socketWriter.println(DHTMain.REQUEST_KEY_VALUES + ":" + currentNode.getNodeId());
						serverResponse = socketReader.readLine();
						if (serverResponse != null && !serverResponse.isEmpty() && serverResponse != "") {
							String[] keyValuePairs = serverResponse.split("::");
							currentNode.lock();

							for (int i = 0; i < keyValuePairs.length; i++) {
								// String[] keyValue =
								// keyValuePairs[i].split(":", 2);
								String[] keyValue = keyValuePairs[i].split(":", 3);
								if (keyValue.length == 3) {
									String key = keyValue[0];
									String value = keyValue[1];
									String keyHashValue = keyValue[2];
									currentNode.getDataStore().put(key, value);
									System.out.println("(key,value) => (" + key + "," + value 
											+ "with a Key Hashed value of " + keyHashValue + ")" + " sent to :"
											+ currentNode.getNodeIpAddress() + ":" + currentNode.getPort());
								}
							}
							currentNode.unlock();

						}***/

					fingerTableUpdate(socketWriter, socketReader);
					// Close connections
					socketWriter.close();
					socketReader.close();
					socket.close();
					
					//update successor2 logic//code by priya
					updateSuccessors();
					
					//manage replicas///code by priya
					//replicate data to successors
					//manageReplica();

				} 
				}
				// else If I dont have successor entries and if I am not my 1st predecessor then connect to my predecessor and update my finger table
				else if (!currentNode.getNodeIpAddress().equals(currentNode.getPredecessor1().getAddress())
						|| (currentNode.getPort() != currentNode.getPredecessor1().getPort())) {
					// Open socket to successor
					socket = new Socket(currentNode.getPredecessor1().getAddress(),
							currentNode.getPredecessor1().getPort());

					// Open reader/writer to chord node
					socketWriter = new PrintWriter(socket.getOutputStream(), true);
					socketReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
					fingerTableUpdate(socketWriter, socketReader);
					// Close connections
					socketWriter.close();
					socketReader.close();
					socket.close();
					
					//update successor2 logic//by priya
					updateSuccessors();
					
					//manage replicas//code by priya
					//replicate data to successors
					//manageReplica();
					
				}
				
			}
			catch (UnknownHostException e) {
				System.err.println("stabilize() could not find host of first successor");
				e.printStackTrace();
			} catch (IOException e) {//Handles successor node failure
				System.err.println("stabilize() could not connect to first successor");
				e.printStackTrace();
				//which means something is wrong/////////////trying
				//Successor1 connection did not work
				//Contact successor2 to update finger table
				boolean callAgain=true;
				while(callAgain){
				try{
				// Open socket to successor2
				socket = new Socket(currentNode.getSuccessor2().getAddress(), currentNode.getSuccessor2().getPort());
				System.out.println("ask help from successor2 to update finger table:"+currentNode.getSuccessor2().getPort());
				// Open reader/writer to chord node
				socketWriter = new PrintWriter(socket.getOutputStream(), true);
				socketReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				//update finger table
				fingerTableUpdate(socketWriter, socketReader);
				System.out.println("successor2 helped me :)");
				}
				catch(IOException ex)
				{
					System.out.println("stabilize()-within catch - socket failed");
					ex.printStackTrace();
				}
				catch(Exception ex)
				{
					System.out.println("stabilize- within catch - sm excptn:"+ex.getMessage());
					ex.printStackTrace();
				}
				// Close connections
				socketWriter.close();
				socketReader.close();
				socket.close();
				updateSuccessors();
				//manageReplica();
				
				callAgain=false;
				}
				
			}
				
				// Stabilize again after delay
				Thread.sleep(delaySeconds);
			}
		} catch (InterruptedException e) {
			System.err.println("stabilize() thread interrupted");
			e.printStackTrace();
		} catch (Exception e) {
			System.err.println("stabilize() threw an exception :"+e.getMessage());
			e.printStackTrace();
			
		}
		
	}

	// This method is for finger Table updation
	private void fingerTableUpdate(PrintWriter socketWriter, BufferedReader socketReader) throws IOException {
		BigInteger bigQuery = BigInteger.valueOf(2L);
		BigInteger bigSelfId = BigInteger.valueOf(currentNode.getNodeId());

		currentNode.lock();

		// Update all fingers
		for (int i = 0; i < DHTMain.FINGER_TABLE_SIZE; i++) {
			BigInteger bigResult = bigQuery.pow(i);
			bigResult = bigResult.add(bigSelfId);

			// Send query to chord
			socketWriter.println(DHTMain.FIND_NODE + ":" + bigResult.longValue());
			// System.out.println("Sent: " + DHTMain.FIND_NODE + ":" +
			// bigResult.longValue());

			// Read response from chord
			String serverResponse = socketReader.readLine();

			// Parse out address and port
			String[] serverResponseFragments = serverResponse.split(":", 2);
			String[] addressFragments = serverResponseFragments[1].split(":");

			// Add response to finger table
			currentNode.getFingerTable().put(i, new Finger(addressFragments[0], Integer.valueOf(addressFragments[1])));
			currentNode.setSuccessor1(currentNode.getFingerTable().get(0));
			//currentNode.setSuccessor2(currentNode.getFingerTable().get(1));//this is wrong - DO NOT UNCOMMENT.

			//System.out.println("Received: " + serverResponse);

		}
		currentNode.unlock();
		currentNode.printFingerTableEntries();
		
		System.out.println("printing data in RingStabilizer " + currentNode.getDataStore());

	}

	
	//new logic to updsate successor2//by priya
	private void updateSuccessors() throws UnknownHostException, IOException
	{
		Socket socket = null;
		PrintWriter socketWriter = null;
		BufferedReader socketReader = null;
		
		currentNode.setSuccessor1(currentNode.getFingerTable().get(0));
		// Open socket to successor
		socket = new Socket(currentNode.getSuccessor1().getAddress(),
				currentNode.getSuccessor1().getPort());

		// Open reader/writer to chord node
		socketWriter = new PrintWriter(socket.getOutputStream(), true);
		socketReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		
		//socket to successor1 and get its successor1 entry
		BigInteger bigQuery = BigInteger.valueOf(2L);
		BigInteger bigSelfId = BigInteger.valueOf(currentNode.getSuccessor1().getNodeId());
		BigInteger bigResult = bigQuery.pow(0);
		bigResult = bigResult.add(bigSelfId);
		
		currentNode.lock();
		// Send query to chord
		socketWriter.println(DHTMain.FIND_NODE + ":" + bigResult.longValue());
		
		// Read response from chord
		String serverResponse = socketReader.readLine();
		// Parse out address and port
		String[] serverResponseFragments = serverResponse.split(":", 2);
		String[] addressFragments = serverResponseFragments[1].split(":");
		Finger newSuccessor2=new Finger(addressFragments[0], Integer.valueOf(addressFragments[1]));
		
		currentNode.setSuccessor2(newSuccessor2);
		System.out.println("On entry:successor1="+currentNode.getSuccessor1().getPort()+" successor2 set to "+currentNode.getSuccessor2().getPort());
		
		currentNode.unlock();
		// Close connections
		socketWriter.close();
		socketReader.close();
		socket.close();
	}
	
	//replicate data to successor1 and 2.//by priya
	private void manageReplica() throws UnknownHostException, IOException
    {
		
    	//loop through data map entries of this node
		
    	for(Map.Entry<String, String> entry:currentNode.getDataStore().entrySet()){
    		String dataKey=entry.getKey();
    		String dataValue=entry.getValue();
    		// Get long of query
    		SHAHelper keyHasher = new SHAHelper(dataKey);
    		long keyNodeId = keyHasher.getLong();

    		// Wrap the queryNodeId if it is as big as the ring
    		if (keyNodeId >= DHTMain.RING_SIZE) {
    			keyNodeId -= DHTMain.RING_SIZE;
    		}
    		// If the query is greater than our predecessor id and less than equal
    		// to our id then we have the value
    		if (isThisMyNode(keyNodeId)) //if(dataKey<=currentNode.getId() && dataKey>currentNode.getPredecessor1().getNodeId())
    		{
    			//this node's data
	    	
	    		//if datakey is present in successor2's datamap
		    		//socket to successor2
		    		//Store_replica:dataKey:dataValue
					//copy dataKey,dataValue to successor1's datamap
					//close socket
					//check response
					 
	    		currentNode.lock();
	    		connectToSuccessor(currentNode.getSuccessor1().getAddress(), currentNode.getSuccessor1().getPort(), dataKey, dataValue);
	    		connectToSuccessor(currentNode.getSuccessor2().getAddress(), currentNode.getSuccessor2().getPort(), dataKey, dataValue);
	    		currentNode.unlock();
	    	}
    	}
    	
    }
	
	//copied it for use in manageReplica
	private boolean isThisMyNode(long queryNodeId) {
		boolean response = false;

		// If we are working in a nice clockwise direction without wrapping
		if (currentNode.getNodeId() > currentNode.getPredecessor1().getNodeId()) {
			// If the queryNodeId is between my predecessor and me, the query
			// belongs to me
			if ((queryNodeId > currentNode.getPredecessor1().getNodeId()) && (queryNodeId <= currentNode.getNodeId())) {
				response = true;
			}
		} else { // If we are wrapping
			if ((queryNodeId > currentNode.getPredecessor1().getNodeId()) || (queryNodeId <= currentNode.getNodeId())) {
				response = true;
			}
		}

		return response;
	}
	
	//socket to given node and put replica//by priya
	private void connectToSuccessor(String address, int port,String key,String value) throws UnknownHostException, IOException 
	{
		Socket socket=null;
		PrintWriter socketWriter = null;
		BufferedReader socketReader = null;
		//socket to successor
		socket=new Socket(address,port);
		// Open reader/writer to chord node
		socketWriter = new PrintWriter(socket.getOutputStream(), true);
		socketReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		//submit a request to copy dataKey,dataValue to successor's datamap
		socketWriter.println(DHTMain.PUT_REPLICA + ":" + address + ":"+port+":"+key+":"+value);
		// Read response from chord
		String serverResponse = socketReader.readLine();
		
		//close socket
		socketWriter.close();
		socketReader.close();
		socket.close();
		//check response
		System.out.println("replicate data to "+address+"-"+port+": "+serverResponse);
		
	}

}
