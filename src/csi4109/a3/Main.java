package csi4109.a3;

import static csi4109.a3.Message.MessageType.TERMINATE_EDGE;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.ui.spriteManager.Sprite;
import org.graphstream.ui.spriteManager.SpriteManager;
import org.graphstream.ui.swingViewer.Viewer;

import csi4109.a3.Message.MessageType;

/**
 * Main class for CSI 4109 Assignment 3. This file runs a visual simulator
 * of the saturation algorithm being used to find the total number of
 * diametral paths in a tree network. At the end of the execution, all nodes
 * know how many paths there are.
 */
public class Main {
	
	// Maps for linking org.graphstream.graph.Node and org.graphstream.graph.Edge
	// instances to custom wrappers
	public static Map<Node, NodeWrapper> nodeWrapperMap;
	public static Map<Edge, EdgeWrapper> edgeWrapperMap;
	
	// Semaphore used to limit concurrent access to the graph. Unfortunately,
	// GraphStream doesn't handle concurrency very well
	public static Semaphore graphSemaphore = new Semaphore(1);

	// Data used to initialize the algorithm
	private static ConfigurationData data;

	// Main function. This is the entry point of the program
	public static void main(String[] args) throws InterruptedException, IOException {
		// Initialize the graph and its viewer
		Graph graph = new SingleGraph("CSI4109 - Assignment 3 Simulation");
		System.setProperty("org.graphstream.ui.renderer",
				"org.graphstream.ui.j2dviewer.J2DGraphRenderer");
		String stylesheet = String.format(readCssFile("css/graph.css"));
		SpriteManager manager = new SpriteManager(graph);
//		displayLegend(manager);
		
		// Display the graph at full screen size (windowed)
		Viewer viewer = graph.display();
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		viewer.getDefaultView().resizeFrame(screenSize.width, screenSize.height);
		
		boolean running = true;
		graph.addAttribute("ui.stylesheet", stylesheet);
		
		while (running) {
			// Reset variables
			data = new ConfigurationData();
			clearGraph(graph, manager);
			nodeWrapperMap = new HashMap<Node, NodeWrapper>();
			edgeWrapperMap = new HashMap<Edge, EdgeWrapper>();
			
			// Display the configuration window
			ConfigurationWindow configWindow = new ConfigurationWindow(data);
			Thread configThread = new Thread(configWindow);
			configThread.start();
			configThread.join();

			// Generate the tree graph
			generateTree(graph, 100);

			// Give the tree a few seconds to stabilize
			Thread.sleep(3000);

			// Get a random list of initiators and start the algorithm 
			List<NodeWrapper> initiators = initNodes(graph);
			initEdges(graph, manager);
			start(initiators);

			// Wait for the end of the algorithm and prompt the user for running it again
			waitForEnd();
			int repeat = JOptionPane.showConfirmDialog(new JFrame(), "Run a different execution?",
					"", JOptionPane.YES_NO_OPTION);
			if (repeat != JOptionPane.YES_OPTION) {
				running = false;
			}
		}
	}
	
	/**
	 * Clears the graph of any visual entities while leaving its other attributes
	 * intact
	 * 
	 * @param graph The {@link Graph} to clear
	 * @param manager The {@link SpriteManager} to use to clear the {@link Sprite}s
	 */
	private static void clearGraph(Graph graph, SpriteManager manager) {
		while (graph.getNodeCount() > 0) {
			graph.removeNode(0);
		}
		while (graph.getEdgeCount() > 0) {
			graph.removeEdge(0);
		}
		
		Iterator<Sprite> spriteIterator = manager.iterator();
		LinkedList<String> spriteIds = new LinkedList<String>();
		while (spriteIterator.hasNext()) {
			spriteIds.add(spriteIterator.next().getId());
		}
		
		for (String id : spriteIds) {
			manager.removeSprite(id);
		}
	}
	
	/**
	 * Generates a tree graph according to the selected type
	 * 
	 * @param graph The {@link Graph} instance with which to use for building the tree
	 * @param delta The amount of time, in milliseconds, to wait between generating nodes
	 * @throws InterruptedException
	 */
	private static void generateTree(Graph graph, int delta) throws InterruptedException {
		switch (data.treeType) {
			case List:
				TreeGenAlgorithms.generateListTree(graph, delta, data.networkSize);
				break;
			case Star:
				TreeGenAlgorithms.generateStarTree(graph, delta, data.networkSize);
				break;
			case Binary:
				TreeGenAlgorithms.generateBinaryTree(graph, delta, data.networkSize);
				break;
			default:
				TreeGenAlgorithms.generateRandomTree(graph, delta, data.networkSize);
				break;
		}
	}
	
	/**
	 * Initializes the {@link Node}s of the {@link Graph} by wrapping them in a {@link NodeWrapper}
	 * 
	 * @param graph The {@link Graph} from which to retrieve the nodes
	 * @return a list of initiator nodes that should be spontaneously activated
	 */
	private static List<NodeWrapper> initNodes(Graph graph) {
		List<NodeWrapper> initiators = new LinkedList<NodeWrapper>();
		Iterator<Node> nodes = graph.getNodeIterator();
		
		// Loop through all the nodes. Until i = numInitiators, add the nodes
		// to the list of initiators. For the entire loop, wrap the node and
		// add it to the node wrapper map
		for (int i = 0; nodes.hasNext(); i++) {
			Node node = nodes.next();
			NodeWrapper wrapper = new NodeWrapper(node);
			nodeWrapperMap.put(node, wrapper);
			wrapper.start();
			if (i < data.initiators) {
				initiators.add(wrapper);
			}
		}
		
		return initiators;
	}
	
	/**
	 * Initializes the {@link Edge}s of the {@link Graph} by wrapping them in an {@link EdgeWrapper}
	 * 
	 * @param graph The {@link Graph} from which to retrieve the edges
	 */
	private static void initEdges(Graph graph, SpriteManager manager) {
		Iterator<Edge> edges = graph.getEdgeIterator();
		
		while (edges.hasNext()) {
			Edge edge = edges.next();
			EdgeWrapper wrapper = new EdgeWrapper(edge, manager, data.maxTransmissionDelay);
			edgeWrapperMap.put(edge, wrapper);
			wrapper.start();
		}
	}
	
	/**
	 * Begins the algorithm
	 * 
	 * @param initiators The list of initiator nodes to spontaneously activate
	 */
	private static void start(List<NodeWrapper> initiators) {
		
		// For each initiator assign a timer with a random delay.
		// Once the timer finishes counting down, activate the node
		for (final NodeWrapper initiator : initiators) {
			int delay = (int)(Math.random() * data.maxInitiatorDelay);
			java.util.Timer timer = new Timer();
			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					try {
						initiator.sendMessage(new Message(MessageType.WAKE_UP));
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}, delay);
		}
	}
	
	/**
	 * Reads the content of a file into a {@link String} and returns it. It's meant
	 * to be used for reading the contents of a CSS file
	 * 
	 * @param path The path to the CSS file from which to read its contents
	 * @return The contents of the CSS file in a {@link String}
	 * @throws FileNotFoundException
	 */
	private static String readCssFile(String path) throws FileNotFoundException {
		Scanner scanner = new Scanner(new File(path));
		String content = scanner.useDelimiter("\\Z").next();
		scanner.close();
		return content;
	}
	
	/**
	 * Waits for the algorithm to terminate
	 * @throws InterruptedException 
	 */
	private static void waitForEnd() throws InterruptedException {
		Iterator<NodeWrapper> nodeIterator = nodeWrapperMap.values().iterator();
		while (nodeIterator.hasNext()) {
			nodeIterator.next().join();
		}
		
		Iterator<EdgeWrapper> edgeIterator = edgeWrapperMap.values().iterator();
		while (edgeIterator.hasNext()) {
			EdgeWrapper edge = edgeIterator.next();
			edge.sendMessage(new Message(TERMINATE_EDGE), null);
			edge.join();
		}
	}
}
