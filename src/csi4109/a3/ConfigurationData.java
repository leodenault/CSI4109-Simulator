package csi4109.a3;

import static csi4109.a3.ConfigurationData.TreeType.Arbitrary;


/**
 * POJO representing the configurable data on the graph
 */
public class ConfigurationData {
	public enum TreeType {
		Arbitrary, List, Star, Binary;
	}
	
	// Default parameters used for generating the tree and initiating the algorithm 
	private static final int MIN_NODES = 2;
	private static final int MAX_NODES = 50;
	private static final int MAX_START_DELAY = 4000;
	private static final int MAX_EDGE_DELAY = 1500;

	// Number of nodes in the graph
	public int networkSize = (int)Math.floor(Math.random() * (MAX_NODES - MIN_NODES)) + MIN_NODES;
	// Number of initiators to start the algorithm
	public int initiators = (int)(Math.random() * networkSize) + 1;
	// Maximum delay before an initiator begins
	public int maxInitiatorDelay = MAX_START_DELAY;
	// Maximum delay for a message to traverse an edge
	public int maxTransmissionDelay = MAX_EDGE_DELAY;
	// Type of tree
	public TreeType treeType = Arbitrary;
}
