package csi4109.a3;

import static csi4109.a3.Message.MessageType.TERMINATE_EDGE;

import java.util.HashMap;
import java.util.Map;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Node;
import org.graphstream.ui.spriteManager.Sprite;
import org.graphstream.ui.spriteManager.SpriteManager;

/**
 * Wrapper class for {@link Edge} instances. These objects run
 * in their own threads. They take care of transferring messages
 * between nodes and add variable delay to message transmission
 */
public class EdgeWrapper extends Thread {
	
	private static final int ANIMATION_PERIOD = 50;
	private static final String SPRITE_ID = "sprite";
	
	private static int CURRENT_SPRITE = 0;

	private Edge edge;
	private SpriteManager manager; // Manager for animated sprites
	private MessageBox messageBox;
	private Map<Message, Node> messageNodeMap; // Mapping from message to sending node
	private int maxEdgeDelay;
	
	/**
	 * Constructs an {@link EdgeWrapper} instance
	 * 
	 * @param edge The {@link Edge} to be wrapped
	 * @param manager The {@link SpriteManager} to use for creating {@link Sprite}s
	 */
	public EdgeWrapper(Edge edge, SpriteManager manager, int maxEdgeDelay) {
		this.edge = edge;
		this.manager = manager;
		this.messageBox = new MessageBox();
		this.messageNodeMap = new HashMap<Message, Node>();
		this.maxEdgeDelay = maxEdgeDelay;
	}
	
	/**
	 * Delegate method for storing messages in this {@link EdgeWrapper}'s {@link MessageBox} 
	 * 
	 * @param message The {@link Message} to store
	 * @param sender The {@link Node} that is sending the message
	 * @throws InterruptedException
	 */
	public void sendMessage(Message message, Node sender) throws InterruptedException {
		this.messageNodeMap.put(message, sender);
		this.messageBox.sendMessage(message);
	}

	@Override
	public void run() {
		try {
			// Edges loop forever
			while (true) {
				// Wait for a message to arrive
				Message message = this.messageBox.retrieveMessage();
				
				if (message.getMessageType() == TERMINATE_EDGE) {
					break;
				}
				
				// "Transmit" the message
				this.animateMessage(generateEdgeDelay(), message);
				
				// Notify the receiver of the new message
				Node sender = this.messageNodeMap.get(message);
				Node receiver = this.edge.getOpposite(sender);
				message.setEdge(this.edge);
				Main.nodeWrapperMap.get(receiver).sendMessage(message);
				
				this.messageNodeMap.remove(message);
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Displays the given eccentricity and number of diametral paths on the edge.
	 * The information is displayed near node
	 * 
	 * @param node The {@link Node} next to which the information should be displayed
	 * @param eccentricity The value of the eccentricity to be displayed
	 * @param numPaths The value of the number of diametral paths to be displayed 
	 * @throws InterruptedException
	 */
	public void displayInfo(Node node, int eccentricity, int numPaths) throws InterruptedException {
		Sprite sprite = this.createSprite(this.manager, null);
		sprite.setAttribute("ui.label", String.format("%d,%d", eccentricity, numPaths));
		
		if (this.edge.getSourceNode().equals(node)) {
			sprite.setPosition(0.25);
		} else {
			sprite.setPosition(0.75);
		}
	}
	
	/**
	 * Animates a {@link Sprite} to traverse the length of the {@link Edge}
	 * to simulated a message being sent
	 * 
	 * @param duration The amount of time to animate the {@link Sprite}
	 * @param message The message being sent
	 * @throws InterruptedException
	 */
	private void animateMessage(int duration, Message message) throws InterruptedException {
		Sprite sprite = this.createSprite(this.manager, "message");
		
		int countdown = duration; // Amount of time left until the animation must terminate
		double speed = 1.0 / duration; // Speed at which the message visually travels
		double position = 0.0; // Current position of the message
		Node messageSource = this.messageNodeMap.get(message); // Source from which the message is sent
		
		// Switch directions depending on which nodes the edge considers
		// to be the source and target nodes
		if (this.edge.getTargetNode().equals(messageSource)) {
			speed = -speed;
			position = 1.0;
		}
		
		while (countdown > 0) {
			Main.graphSemaphore.acquire();
			sprite.setPosition(position); // Set the sprite in its new position
			Main.graphSemaphore.release();
			Thread.sleep(ANIMATION_PERIOD); // Wait
			countdown -= ANIMATION_PERIOD; // Update countdown
			position += speed * ANIMATION_PERIOD; // Move the position relative to the time passed
		}
		
		Main.graphSemaphore.acquire();
		sprite.addAttribute("ui.class", "sent"); // Update the sprite's visual representation once finished
		Main.graphSemaphore.release();
	}
	
	/**
	 * Helper method for creating {@link Sprite}s
	 * 
	 * @param manager The {@link SpriteManager} to use to generate the {@link Sprite}
	 * @param classes The CSS classes to apply to the newly created {@link Sprite}
	 * @return the newly created {@link Sprite}
	 * @throws InterruptedException
	 */
	private Sprite createSprite(SpriteManager manager, String classes) throws InterruptedException {
		Main.graphSemaphore.acquire();
		Sprite sprite = manager.addSprite(String.format("%s%d", SPRITE_ID, CURRENT_SPRITE++));
		
		if (classes != null) {
			sprite.addAttribute("ui.class", classes);
		}
		
		sprite.attachToEdge(this.edge.getId());
		Main.graphSemaphore.release();
		return sprite;
	}
	
	/**
	 * Generates a random delay
	 * 
	 * @return the randomly generated delay
	 */
	private int generateEdgeDelay() {
		return (int)(Math.random() * this.maxEdgeDelay);
	}
}
