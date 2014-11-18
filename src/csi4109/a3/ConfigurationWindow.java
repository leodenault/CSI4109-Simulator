package csi4109.a3;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.Semaphore;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import csi4109.a3.ConfigurationData.TreeType;

/**
 * Modal window for configuring various algorithm parameters before executing the algorithm
 */
public class ConfigurationWindow extends JFrame implements Runnable {
	private static final long serialVersionUID = 1;

	private Semaphore running;
	private ConfigurationData data;
	
	/**
	 * Constructs a {@link ConfigurationWindow} instance
	 * 
	 * @param data THe {@link ConfigurationData} to modify upon submission
	 */
	public ConfigurationWindow(ConfigurationData data) {
		super("Algorithm Configuration");
		this.running = new Semaphore(0);
		this.data = data;
		this.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		this.initLayout();
		this.setLocationRelativeTo(null); // Center in the middle of the screen
	}

	@Override
	public void run() {
		this.setVisible(true); // Open the window
		try {
			this.running.acquire(); // Wait until the user is done with the configuration
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Initializes the layout of the UI elements in the window
	 */
	private void initLayout() {
		JPanel panel = new JPanel(new GridBagLayout());
		this.add(panel);
		GridBagConstraints c = new GridBagConstraints();
		
		c.gridx = 0;
		c.gridy = 0;
		c.gridwidth = 2;
		JButton random = new JButton("Random Execution!");
		random.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				ConfigurationWindow.this.setVisible(false);
				running.release();
			}
		} );
		panel.add(random, c);
		
		c.gridy = 1;
		
		final JSpinner size = new JSpinner(new SpinnerNumberModel(20, 2, 50, 1));
		final JSpinner initiators = new JSpinner(new SpinnerNumberModel(5, 1, 50, 1));
		InitiatorListener initiatorListener = new InitiatorListener(size, initiators);
		size.addChangeListener(initiatorListener);
		initiators.addChangeListener(initiatorListener);
		this.addField("Network Size", size, panel, c);
		this.addField("Number of Initiators", initiators, panel, c);

		final JSpinner initiatorDelay = new JSpinner(new SpinnerNumberModel(4000, 0, 10000, 50));
		this.addField("Maximum Initiator Delay", initiatorDelay, panel, c);
		
		final JSpinner transmissionDelay = new JSpinner(new SpinnerNumberModel(1500, 50, 10000, 50));
		this.addField("Maximum Message Transmission Delay", transmissionDelay, panel, c);
		
		final JComboBox<TreeType> treeType = new JComboBox<TreeType>(TreeType.values());
		this.addField("Tree Type", treeType, panel, c);
		
		c.gridwidth = 2;
		c.gridx = 0;
		c.fill = GridBagConstraints.NONE;
		JButton start = new JButton("Execute using above parameters");
		start.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				data.networkSize = (int)(size.getValue());
				data.initiators = (int)(initiators.getValue());
				data.maxInitiatorDelay = (int)(initiatorDelay.getValue());
				data.maxTransmissionDelay = (int)(transmissionDelay.getValue());
				data.treeType = (TreeType)(treeType.getSelectedItem());
				
				ConfigurationWindow.this.setVisible(false);
				running.release();
			}
		});
		panel.add(start, c);
		
		this.pack();
	}
	
	/**
	 * Helper method for adding a filed to the window
	 * 
	 * @param label The message for the label describing component
	 * @param component The component to add for configuring an algorithm parameter
	 * @param parent The parent to which the component should be added
	 * @param c The {@link GridBagConstraints} object to use for layout
	 */
	private void addField(String label, Component component, JPanel parent, GridBagConstraints c) {
		c.gridwidth = 1;
		c.gridx = 0;
		c.weightx = 0.5;
		c.weighty = 0.5;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.ipadx = 20;
		
		parent.add(new JLabel(label), c);
		c.gridx = 1;
		parent.add(component, c);
		c.gridy++;
	}
	
	/**
	 * Listener that listens for changes between the network size and initiators components 
	 */
	private class InitiatorListener implements ChangeListener {
		private JSpinner nodeSpinner;
		private JSpinner initiatorSpinner;
		
		public InitiatorListener(JSpinner numNodes, JSpinner numInitiators) {
			this.nodeSpinner = numNodes;
			this.initiatorSpinner = numInitiators;
		}
		
		@Override
		public void stateChanged(ChangeEvent e) {
			// Make sure that the number of initiators can't be larger than the
			// number of nodes in the network
			int nodes = (int)(nodeSpinner.getValue());
			int initiators = (int)(initiatorSpinner.getValue());
			if (nodes < initiators) {
				this.initiatorSpinner.setValue(nodes);
			}
		}
	}
}
