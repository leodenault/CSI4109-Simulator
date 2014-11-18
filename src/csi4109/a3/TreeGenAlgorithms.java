package csi4109.a3;

import java.util.LinkedList;
import java.util.Queue;

import org.graphstream.algorithm.generator.BarabasiAlbertGenerator;
import org.graphstream.algorithm.generator.Generator;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;

/**
 * Static methods for different algorithms for generating trees 
 */
public class TreeGenAlgorithms {
	private static int nodeId = 0;
	private static int edgeId = 0;
	
	/**
	 * Generates a tree graph in the shape of a list. In other words, each
	 * parent has a single child
	 * 
	 * @param graph The {@link Graph} instance with which to use for building the tree
	 * @param delta The amount of time, in milliseconds, to wait between generating nodes
	 * @throws InterruptedException
	 */
	public static void generateListTree(Graph graph, int delta, int numNodes) throws InterruptedException {
		graph.addNode(String.valueOf(nodeId++));
		Node parent = graph.addNode(String.valueOf(nodeId++));
		graph.addEdge(String.valueOf(edgeId++), 0, 1);
		
		for (int i = 2; i < numNodes; i++) {
			Node child = graph.addNode(String.valueOf(nodeId++));
			graph.addEdge(String.valueOf(edgeId++), parent.getIndex(), child.getIndex());
			parent = child;
			if (delta > 0) {
				Thread.sleep(delta);
			}
		}
	}
	
	/**
	 * Generates a tree graph in the shape of a star. In other words, there is a single
	 * internal node which is connected to all the leaves
	 * 
	 * @param graph The {@link Graph} instance with which to use for building the tree
	 * @param delta The amount of time, in milliseconds, to wait between generating nodes
	 * @throws InterruptedException
	 */
	public static void generateStarTree(Graph graph, int delta, int numNodes) throws InterruptedException {
		Node parent = graph.addNode(String.valueOf(nodeId++));
		Node firstChild = graph.addNode(String.valueOf(nodeId++));
		graph.addEdge(String.valueOf(edgeId++), parent.getIndex(), firstChild.getIndex());
		
		for (int id = 2; id < numNodes; id++) {
			Node child = graph.addNode(String.valueOf(nodeId++));
			graph.addEdge(String.valueOf(edgeId++), parent.getIndex(), child.getIndex());
			if (delta > 0) {
				Thread.sleep(delta);
			}
		}
	}
	
	/**
	 * Generates a binary tree graph. In other words, each tree has at most two children
	 * 
	 * @param graph The {@link Graph} instance with which to use for building the tree
	 * @param delta The amount of time, in milliseconds, to wait between generating nodes
	 * @throws InterruptedException
	 */
	public static void generateBinaryTree(Graph graph, int delta, int numNodes) throws InterruptedException {
		Node root = graph.addNode(String.valueOf(nodeId++));
		Node child1 = graph.addNode(String.valueOf(nodeId++));
		graph.addEdge(String.valueOf(edgeId++), root.getId(), child1.getId());
		
		if (numNodes > 2) {
			Queue<Node> parents = new LinkedList<Node>();
			
			Node child2 = graph.addNode(String.valueOf(nodeId++));
			graph.addEdge(String.valueOf(edgeId++), root.getIndex(), child2.getIndex());
			
			parents.add(child1);
			parents.add(child2);

			int childrenLeft = numNodes - 3;

			while (childrenLeft > 0) {
				Node parent = parents.remove();
				
				if (childrenLeft == 1) {
					Node child = graph.addNode(String.valueOf(nodeId++));
					graph.addEdge(String.valueOf(edgeId++), parent.getIndex(), child.getIndex());
					childrenLeft--;
				} else {
					Node nextChild1 = graph.addNode(String.valueOf(nodeId++));
					Node nextChild2 = graph.addNode(String.valueOf(nodeId++));
					graph.addEdge(String.valueOf(edgeId++), parent.getIndex(), nextChild1.getIndex());
					graph.addEdge(String.valueOf(edgeId++), parent.getIndex(), nextChild2.getIndex());
					
					parents.add(nextChild1);
					parents.add(nextChild2);
					
					childrenLeft -= 2;
				}

				if (delta > 0) {
					Thread.sleep(delta);
				}
			}
		}
	}
	
	/**
	 * Generates a random tree graph
	 * 
	 * @param graph The {@link Graph} instance with which to use for building the tree
	 * @param delta The amount of time, in milliseconds, to wait between generating nodes
	 * @throws InterruptedException
	 */
	public static void generateRandomTree(Graph graph, int delta, int numNodes) throws InterruptedException {
		// The generator initializes itself with two nodes, so we remove 2 from the following computation
		numNodes = numNodes - 2;
		
		Generator generator = new BarabasiAlbertGenerator(1);
		generator.addSink(graph);
		
		generator.begin();
		for (int i = 0; i < numNodes; i++) {
			generator.nextEvents(); // Adds a node to the tree
			if (delta > 0) {
				Thread.sleep(delta);
			}
		}
		generator.end();
	}
}
