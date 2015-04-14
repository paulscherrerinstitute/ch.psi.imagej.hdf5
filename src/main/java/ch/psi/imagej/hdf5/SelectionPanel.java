package ch.psi.imagej.hdf5;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

import ncsa.hdf.object.Dataset;
import javax.swing.JTextField;
import java.awt.FlowLayout;

public class SelectionPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private final JList<Dataset> list;
	private JCheckBox checkbox;
	private JCheckBox checkBoxVirtualStack;
	private JLabel lblSlice;
	private JPanel panel;
	private JTextField textField;
	
	public SelectionPanel(){
		this(new ArrayList<Dataset>());
	}
	
	public SelectionPanel(List<Dataset> datasets){
		// Filter datasets that are not potential images / that cannot be displayed
		List<Dataset> fdatasets = new ArrayList<Dataset>();
		for(Dataset d: datasets){
			if(d.getRank()>=2 && d.getRank()<=5){
				fdatasets.add(d);
			}
		}
		
		list = new JList<>(new DefaultListModel<Dataset>());
		list.setListData(fdatasets.toArray(new Dataset[fdatasets.size()]));
		list.setCellRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)	{
				JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				final Dataset d = ((Dataset) value);
				label.setText(d.getFullName()+" ("+d.getRank()+"D)");
				return label;

			}
		});
	    
	    JScrollPane scroll = new JScrollPane(list);
	    scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
	    
		setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
		add(scroll);
		checkbox = new JCheckBox("Group Datasets (2D datasets only)");
		add(checkbox);
		
		checkBoxVirtualStack = new JCheckBox("Virtual Stack");
		checkBoxVirtualStack.setSelected(true);
		add(checkBoxVirtualStack);
		
		panel = new JPanel();
		FlowLayout flowLayout = (FlowLayout) panel.getLayout();
		flowLayout.setAlignment(FlowLayout.LEFT);
		add(panel);
		
		lblSlice = new JLabel("Slice (3D only):");
		panel.add(lblSlice);
		
		textField = new JTextField();
		panel.add(textField);
		textField.setColumns(10);
	}
	
	public List<Dataset> getSelectedValues(){
		return list.getSelectedValuesList();
	}
	
	public boolean groupValues(){
		return checkbox.isSelected();
	}
	
	public Integer getSlice(){
		String text = textField.getText();
		if(text.matches("^[0-9]+$")){
			return new Integer(text);
		}
		return null;
	}
	
	public Integer getModulo(){
		String text = textField.getText();
		if(text.matches("^%[0-9]+$")){
			return new Integer(text.replace("%", ""));
		}
		return null;
	}
	
	public boolean useVirtualStack(){
		return checkBoxVirtualStack.isSelected();
	}
}
