package csi4109.a3;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;

/**
 * A type of queue specialized for {@link Message}s and concurrency.
 * Any entity needing to handle messages should store and retrieve them
 * using this object
 */
public class MessageBox {

	private Semaphore messageSemaphore; // Used to suspend a thread waiting on a message
	private Semaphore mutex; // Used to grant excusive access to the message queue
	private Queue<Message> messages; // The queue holding the messages
	
	/**
	 * Constructs a {@link MessageBox} instance
	 */
	public MessageBox() {
		this.messageSemaphore = new Semaphore(0);
		this.mutex = new Semaphore(1);
		this.messages = new LinkedList<Message>();	
	}
	
	/**
	 * Adds a {@link Message} to the back of the {@link MessageBox} queue
	 * 
	 * @param message The {@link Message} to add
	 * @throws InterruptedException
	 */
	public void sendMessage(Message message) throws InterruptedException {
		this.mutex.acquire();
		this.messages.add(message);
		this.messageSemaphore.release();
		this.mutex.release();
	}
	
	/**
	 * Removes a {@link Message} from the {@link MessageBox} 
	 * 
	 * @return The message removed from the box
	 * @throws InterruptedException
	 */
	public Message retrieveMessage() throws InterruptedException {
		this.messageSemaphore.acquire();
		this.mutex.acquire();
		Message message = this.messages.remove();
		this.mutex.release();
		return message;
	}
}
