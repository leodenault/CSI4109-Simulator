package csi4109.a3;

import static csi4109.a3.Message.MessageType.CHECK_CENTER;
import static csi4109.a3.Message.MessageType.COMPARE_SATURATED;
import static csi4109.a3.Message.MessageType.NOTIFICATION;
import static csi4109.a3.Message.MessageType.SATURATE;
import static csi4109.a3.Message.MessageType.WAKE_UP;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Node;

/**
 * A wrapper used for simulating a node in a distributed network.
 * This object runs in its own thread, running through different
 * states as it and the other nodes compute the diametral path
 * of the tree network
 */
public class NodeWrapper extends Thread {
	
	// The states in which a NodeWrapper may find itself
	private static final int AVAILABLE = 1;
	private static final int ACTIVE = 2;
	private static final int PROCESSING = 3;
	private static final int SATURATED = 4;
	private static final int NOTIFIED = 5;
	
	private Node node;
	private MessageBox messageBox;
	private Collection<Edge> neighbours;
	private Map<Edge, Integer> subtreeEccentricities; // Eccentricities of the sub-trees of this NodeWrapper
	private Map<Edge, Integer> subtreePaths; // The sums of potential diametral paths of this NodeWrapper's sub-trees
	private Edge parent; // The computed parent of this NodeWrapper
	private Edge maxEdge; // The computed Edge with maximum eccentricity of this NodeWrapper
	
	private int eccentricity = 0; // This NodeWrapper's eccentricity
	private int numPaths = 1; // The sum of potential diametral paths going through this NodeWrapper
	private boolean isSingleCenter = false; // Determines whether or not there are one or two centers in the tree
	
	/**
	 * Constructs a {@link NodeWrapper} instance
	 * 
	 * @param node The {@link Node} around which to wrap this instance
	 */
	public NodeWrapper(Node node) {
		this.node = node;
		this.messageBox = new MessageBox();
		this.subtreeEccentricities = new HashMap<Edge, Integer>();
		this.subtreePaths = new HashMap<Edge, Integer>();
	}
	
	@Override
	public void run() {
		try {
			this.setState(AVAILABLE);
			this.available();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Delegate method for sending a message to this {@link NodeWrapper}'s {@link MessageBox}
	 * 
	 * @param message The {@link Message} to send to this {@link NodeWrapper}
	 * @throws InterruptedException
	 */
	public void sendMessage(Message message) throws InterruptedException {
		this.messageBox.sendMessage(message);
	}
	
	/**
	 * First state of the {@link NodeWrapper}'s execution. It runs as
	 * AVAILABLE. Once it receives a WAKE UP message either spontaneously
	 * or from another node, it spreads the message. If it's a leaf, it
	 * automatically goes to state PROCESSING
	 * 
	 * @throws InterruptedException
	 */
	private void available() throws InterruptedException {
		// First, put the node's ID on its label
		Main.graphSemaphore.acquire();
		this.node.setAttribute("ui.label", this.node.getId());
		Main.graphSemaphore.release();
		
		while (this.checkState(AVAILABLE)) {
			Message message = this.messageBox.retrieveMessage();
			
			if (message.getMessageType() == WAKE_UP) {
				this.neighbours = new LinkedList<Edge>(this.node.getEdgeSet());
				
				// Upon receiving WAKE UP, alert other neighbours. Make sure not to
				// send back to sender. If sender is null, then it sends to all neighbours
				this.messageNeighbours(message, message.getEdge());
				
				// If the node is a leaf, then send the saturate message immediately
				if (this.neighbours.size() == 1) {
					this.parent = this.neighbours.iterator().next();
					this.maxEdge = this.parent;
					this.sendSaturateMessage();
				} else {
					this.setState(ACTIVE);
				}
			}
		}
		
		// Enter the next state
		if (this.checkState(PROCESSING)) {
			this.processing();
		} else if (this.checkState(ACTIVE)) {
			this.active();
		} else {
			throw new RuntimeException("Unknown state after exiting AVAILABLE");
		}
	}

	/**
	 * A {@link NodeWrapper} running as ACTIVE can receive SATURATE messages.
	 * Once it receives these messages from all but one of its neighbours,
	 * it forwards the SATURATE message to the last one, which becomes its parent
	 * 
	 * @throws InterruptedException
	 */
	private void active() throws InterruptedException {
		while (this.checkState(ACTIVE)) {
			Message message = this.messageBox.retrieveMessage();
			
			if (message.getMessageType() == SATURATE) {
				// Extract the information from the message and update
				// local data
				this.receiveEccentricityInfo(message);
				
				// If all neighbours but one have sent their SATURATE message,
				// then forward to the last one
				if (this.neighbours.size() == 1) {
					this.parent = this.neighbours.iterator().next();
					this.sendSaturateMessage();
				}
			}
		}
		
		// Enter the next state
		if (this.checkState(PROCESSING)) {
			this.processing();
		} else {
			throw new RuntimeException("Unknown state after exiting ACTIVE");
		}
	}
	
	/**
	 * A {@link NodeWrapper} in PROCESSING will wait for one of three messages.
	 * The first is SATURATE, where the {@link NodeWrapper} will transition to
	 * SATURATED. The second is CHECK CENTER, where the node will proceed to verify
	 * if it is the center node of the tree. The third is NOTIFICATION, which contains
	 * the number of diametral paths in the tree and must be forwarded to all other
	 * neighbours 
	 * 
	 * @throws InterruptedException
	 */
	private void processing() throws InterruptedException {
		while (this.checkState(PROCESSING)) {
			Message message = this.messageBox.retrieveMessage();
			
			switch (message.getMessageType()) {
				case SATURATE:
					// Become saturated and update local information
					this.setState(SATURATED);
					this.receiveEccentricityInfo(message);
					// Send a message to the other saturated node to see who becomes leader
					Message compareSaturated = new Message(COMPARE_SATURATED);
					compareSaturated.addValue("index", this.node.getIndex());
					Main.edgeWrapperMap.get(message.getEdge()).sendMessage(compareSaturated, this.node);
					break;
				case CHECK_CENTER:
					// Update local information and check if center
					this.receiveEccentricityInfo(message);
					this.executeCenterChecking();
					break;
				case NOTIFICATION:
					// Be notified and forward to other neighbours
					this.setState(NOTIFIED);
					this.node.setAttribute("ui.label", message.getValue("notify"));
					messageNeighbours(message, message.getEdge());
					break;
				default:
					break;
			}
		}
		
		// If saturated, then proceed to saturated state. Otherwise, terminate
		if (this.checkState(SATURATED)) {
			this.saturated();
		}
	}
	
	/**
	 * Only the two saturated nodes can make it to this state.
	 * In this state, the two compare each others IDs to know who
	 * will take charge. That node then checks if it is center. If it is
	 * then it computes the number of diametral paths and notifies
	 * all the others. Otherwise, it tries to find the center node
	 * 
	 * @throws InterruptedException
	 */
	private void saturated() throws InterruptedException {
		while (this.checkState(SATURATED)) {
			Message message = this.messageBox.retrieveMessage();
			
			if (message.getMessageType() == CHECK_CENTER) {
				this.executeCenterChecking(); // If asked, check if center
			} else if (message.getMessageType() == COMPARE_SATURATED) {
				// Received message to which saturated node will be leader.
				// If this node is leader, then check if center
				if (message.getValue("index") > this.node.getIndex()) {
					this.executeCenterChecking();
				}
			} else if (message.getMessageType() == NOTIFICATION) {
				// Some other node was center. Be notified and forward the message
				this.setState(NOTIFIED);
				this.node.setAttribute("ui.label", message.getValue("notify"));
				messageNeighbours(message, message.getEdge());
			}
		}
	}
	
	/**
	 * Sends a {@link Message} to all neighbouring {@link Edge}s except for exclude.
	 * If exclude is null, then all neighbours receive the message
	 * 
	 * @param message The message to forward to the neighbours
	 * @param exclude The neighbour to exclude from the list of recipients
	 * @throws InterruptedException
	 */
	private void messageNeighbours(Message message, Edge exclude) throws InterruptedException {
		for (Edge neighbour : this.node.getEdgeSet()) {
			if (!neighbour.equals(exclude)) {
				Main.edgeWrapperMap.get(neighbour).sendMessage(message, this.node);
			}
		}
	}
	
	/**
	 * Helper method for sending a SATURATE message
	 * 
	 * @throws InterruptedException
	 */
	private void sendSaturateMessage() throws InterruptedException {
		Message saturate = new Message(SATURATE);
		
		// Add local eccentricity and diametral path information
		saturate.addValue("eccentricity", this.eccentricity + 1);
		saturate.addValue("paths", this.numPaths);
		
		// Send the message to the parent
		Main.edgeWrapperMap.get(this.parent).sendMessage(saturate, this.node);
		this.setState(PROCESSING);
	}
	
	/**
	 * Processes information received from a message and updates local
	 * eccentricity and number of diametral paths.
	 * 
	 * @param message The {@link Message} from which to extract the information
	 * @throws InterruptedException
	 */
	private void receiveEccentricityInfo(Message message) throws InterruptedException {
		Edge edge = message.getEdge();
		
		// Get the eccentricity and diametral path information from the message
		int subTreeEccentricity = message.getValue("eccentricity");
		int numPaths = message.getValue("paths");
		
		// Update local information for that edge
		this.subtreeEccentricities.put(edge, subTreeEccentricity);
		this.subtreePaths.put(edge, numPaths);
		
		// Update local eccentricity to the maximum of the two.
		// If local eccentricity was smaller, then local number of
		// diametral paths is reset to the number of paths received
		// from the message
		if (this.eccentricity < subTreeEccentricity) {
			this.eccentricity = subTreeEccentricity;
			this.maxEdge = edge;
			this.numPaths = numPaths;
		} else if (this.eccentricity == subTreeEccentricity) {
			// If the message's eccentricity is the same as the local one,
			// then add the message's number of diametral paths to the
			// local value
			this.numPaths += numPaths;
		}

		// Remove the edge from the list of neighbours NOT having sent a
		// saturation message yet
		this.neighbours.remove(edge);
		
		// Display the messages eccentricity and diametral path information on the edge
		Main.edgeWrapperMap.get(edge).displayInfo(this.node, subTreeEccentricity, numPaths);
	}
	
	/**
	 * Check if this {@link NodeWrapper} is the central node. If yes, then
	 * compute the number of diametral paths and notify the others. If not,
	 * then ask the next node to check if it's center
	 * 
	 * @throws InterruptedException
	 */
	private void executeCenterChecking() throws InterruptedException {
		if (this.isCenter()) {
			this.setState(NOTIFIED);
			
			// Compute the number of diametral paths and update
			// node label with it
			int numPaths = this.computeDiametralPaths();
			this.node.setAttribute("ui.label", numPaths);
			
			// Notify the neighbours with the computed value
			Message notify = new Message(NOTIFICATION);
			notify.addValue("notify", numPaths);
			this.messageNeighbours(notify, null);
		} else {
			Message checkCenter = new Message(CHECK_CENTER);
			
			// Compute eccentricity and number of diametral paths
			// of this node in the context of the node to which
			// the message is being sent, then ask it to check if it
			// is center
			int computedEccentricity = this.computeEccentricity(this.maxEdge);
			checkCenter.addValue("eccentricity", computedEccentricity + 1);
			checkCenter.addValue("paths", this.computeNumPaths(this.maxEdge, computedEccentricity));
			Main.edgeWrapperMap.get(this.maxEdge).sendMessage(checkCenter, this.node);
		}
	}
	
	/**
	 * Checks if this node is a center node
	 * 
	 * @return true if this node is central, false otherwise
	 */
	private boolean isCenter() {
		Iterator<Edge> edges = this.subtreeEccentricities.keySet().iterator();
		
		// Get the number of neighbours and the maximum eccentricity
		int numNeighbours = this.node.getDegree();
		int maxValue = this.subtreeEccentricities.get(this.maxEdge);
		boolean isCenter = false;
		
		// Make sure to iterate through all of the nodes so that
		// we know for sure whether it is a single or a double
		// center
		while (edges.hasNext()) {
			Edge edge = edges.next();
			int delta = maxValue - this.subtreeEccentricities.get(edge);
			
			// If the delta between the maximum eccentricity and the current edge
			// is <= 1, then we have a center node. If the node is a leaf, then it
			// must be in a 2-node tree to have made it to this point
			if ((!edge.equals(this.maxEdge) || numNeighbours == 1) && (delta) <= 1) {
				// If we find an edge for which the delta is 0, then we know that
				// the tree has a single center node
				if (delta == 0) {
					this.isSingleCenter = true;
				}
				isCenter = true;
			}
		}
		return isCenter;
	}
	
	/**
	 * Helper method for when the nodes are looking for the center node.
	 * Computes eccentricity in the context of the excluded node
	 * 
	 * @param exclude The node to which this computed eccentricity will be sent
	 * @return the eccentricity of this {@link NodeWrapper} in the context of exclude
	 */
	private int computeEccentricity(Edge exclude) {
		int eccentricity = 0;
		for (Edge edge : this.node.getEdgeSet()) {
			if (!exclude.equals(edge)) {
				eccentricity = Math.max(eccentricity, this.subtreeEccentricities.get(edge));
			}
		}
		return eccentricity;
	}
	
	/**
	 * Helper method for when the nodes are looking for the center node.
	 * Computes the number of diametral paths in the context of the excluded node
	 * 
	 * @param exclude The node to which this computed eccentricity will be sent
	 * @param eccentricity The eccentricity of this {@link NodeWrapper} in the context of
	 * exclude
	 * @return the number of diametral paths of this {@link NodeWrapper} in the context of exclude
	 */
	private int computeNumPaths(Edge exclude, int eccentricity) {
		int numPaths = 0;
		for (Edge edge : this.node.getEdgeSet()) {
			// Make sure to only add up the diametral paths of the edges that
			// have the same eccentricity
			int edgeEccentricity = this.subtreeEccentricities.get(edge);
			if (!exclude.equals(edge) && eccentricity == edgeEccentricity) {
				numPaths += this.subtreePaths.get(edge);
			}
		}
		return numPaths;
	}
	
	/**
	 * Computes the number of diametral paths in the tree
	 * 
	 * @return the number of diametral paths in the tree
	 */
	private int computeDiametralPaths() {
		return isSingleCenter ?
				computeDiametralPathsForSingleCenter() :
					computeDiametralPathsForDoubleCenter();
	}
	
	/**
	 * Computes the number of diametral paths in the tree if it contains
	 * a single center
	 * 
	 * @return the number of diametral paths in the tree
	 */
	private int computeDiametralPathsForSingleCenter() {
		Iterator<Edge> edges = this.subtreeEccentricities.keySet().iterator();
		
		// Get the maximum eccentricity
		int maxValue = this.subtreeEccentricities.get(this.maxEdge);
		LinkedList<Integer> paths = new LinkedList<Integer>();
		
		// Iterate through the edges and add all of the numbers of
		// diametral paths for each edge if the eccentricities are the same
		while (edges.hasNext()) {
			Edge edge = edges.next();
			int delta = maxValue - this.subtreeEccentricities.get(edge);
			if (delta < 1) {
				paths.add(this.subtreePaths.get(edge));
			}
		}
		
		// The number of paths will be the following formula:
		// p1*(p2 + p3 + ...) + p2*(p3 + p4 + ...) + ... + pn-1 * pn
		// Where pi is a number of paths for a given sub-tree with maximum
		// eccentricity
		int sum = 0;
		for (int i = 0; i < paths.size() - 1; i++) {
			int subSum = 0;
			
			for (int j = i + 1; j < paths.size(); j++) {
				subSum += paths.get(j);
			}
			sum += paths.get(i) * subSum;
		}
		
		return sum;
	}
	
	/**
	 * Computes the number of diametral paths in the tree if it contains
	 * two centers
	 * 
	 * @return the number of diametral paths in the tree
	 */
	private int computeDiametralPathsForDoubleCenter() {
		Iterator<Edge> edges = this.subtreeEccentricities.keySet().iterator();
		
		// Get the maximum eccentricity
		int maxValue = this.subtreeEccentricities.get(this.maxEdge);
		
		int maxPathsSum = 0; // Used to sum the numbers of diametral paths for the maximum eccentricity
		int	nextMaxPathsSum = 0; // Used to sum the numbers of diametral paths for the second maximum eccentricity
		
		while (edges.hasNext()) {
			Edge edge = edges.next();
			int delta = maxValue - this.subtreeEccentricities.get(edge);
			if (delta == 0) { // If maximum eccentricity
				maxPathsSum += this.subtreePaths.get(edge);
			} else if (delta == 1) { // If second maximum eccentricity
				nextMaxPathsSum += this.subtreePaths.get(edge);
			}
		}
		
		// Multiply the two sums together
		return maxPathsSum * nextMaxPathsSum;
	}
	
	/**
	 * Checks if the {@link NodeWrapper} is in the given state
	 * 
	 * @param state The state to verify
	 * @return true if the {@link NodeWrapper} is in state state, otherwise, false
	 */
	private boolean checkState(Integer state) {
		return state.equals(node.getAttribute("state"));
	}
	
	/**
	 * Sets the state for the given {@link NodeWrapper}
	 * 
	 * @param state The state to which to set the {@link NodeWrapper}
	 * @throws InterruptedException
	 */
	private void setState(Integer state) throws InterruptedException {
		Main.graphSemaphore.acquire();
		this.node.setAttribute("state", state);
		
		if (this.node.hasAttribute("ui.class")) {
			this.node.removeAttribute("ui.class");
		}
		
		// Switch to the appropriate CSS class
		switch (state) {
			case ACTIVE:
				this.node.setAttribute("ui.class", "active");
				break;
			case PROCESSING:
				this.node.setAttribute("ui.class", "processing");
				break;
			case SATURATED:
				this.node.setAttribute("ui.class", "saturated");
				break;
			case NOTIFIED:
				this.node.setAttribute("ui.class", "notified");
				break;
		}
		Main.graphSemaphore.release();
	}
}
