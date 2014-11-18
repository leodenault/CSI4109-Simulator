package csi4109.a3;

import java.util.HashMap;
import java.util.Map;

import org.graphstream.graph.Edge;

/**
 * A message that can be sent between {@link NodeWrapper}s through {@link EdgeWrapper}s
 */
public class Message {
	
	/**
	 * The available types of messages that {@link NodeWrapper}s can send each other.
	 * They mostly correspond to the saturation algorithm messages seen in class
	 */
	public static enum MessageType {
		WAKE_UP, SATURATE, NOTIFICATION, CHECK_CENTER, COMPARE_SATURATED, TERMINATE_EDGE
	}
	
	private MessageType messageType;
	private Map<String, Integer> values; // The values that the message contains
	private Edge edge; // The edge that carried the message
	
	/**
	 * Constructs a new {@link Message} instance
	 * 
	 * @param type The {@link MessageType} of the message
	 */
	public Message(MessageType type) {
		this.values = new HashMap<String, Integer>();
		this.messageType = type;
		this.edge = null;
	}

	public MessageType getMessageType() {
		return this.messageType;
	}
	
	/**
	 * Adds a value to the {@link Message} using the given key
	 * 
	 * @param key The key of the value should it ever be retrieved
	 * @param value The value to store in the {@link Message}
	 */
	public void addValue(String key, Integer value) {
		this.values.put(key, value);
	}

	/**
	 * Returns the value in the {@link Message} associated with the given key
	 * 
	 * @param key The key to use to retrieve the value
	 * @return The value associated with the key
	 */
	public int getValue(String key) {
		return this.values.get(key);
	}
	
	public Edge getEdge() {
		return this.edge;
	}
	
	public void setEdge(Edge edge) {
		this.edge = edge;
	}
}
