	package grapher;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;


public class FilterStage {
	private static final int width=600;
	private static final int height=300;
	private static final String title= "Node Filter";
	private static final Font labelFont = new Font("New Times Roman",16);
	private final Stage stage;
	private Group root= new Group();
	private Scene scene = new Scene(root);
	private boolean closed=false;
	private final NodeProperties nodeProperties;
	private final Visualizer visualizer;
	private final VBox vbox = new VBox(10);
	private final NumberFormat numberFormat = NumberFormat.getInstance();
	public FilterStage(final Visualizer visualizer) {
		this.visualizer = visualizer;
		numberFormat.setMaximumFractionDigits(2);
		vbox.setAlignment(Pos.CENTER);
		VBox.setMargin(vbox, new Insets(50,30,0,0));

		nodeProperties = new NodeProperties(visualizer.getSavedAllNodes());
		stage = new Stage();
		stage.initModality(Modality.NONE);
		stage.setTitle(title);
		stage.setMinWidth(width);
		stage.setMinHeight(height);
		stage.setAlwaysOnTop(true);

		scene.setFill(Color.BLACK);
		scene.setOnKeyPressed(ke -> {
			switch (ke.getCode()) {
			case Q: 
//				stage.hide();
				break;
			default:
				break;
			}
		});
		stage.setScene(scene);
		root.getChildren().add(vbox);
		
		makeNodePropertiesArea();
		vbox.getChildren().add(makeSpacer(0,10));
		makeFilterQueryTextArea();
		vbox.getChildren().add(makeSpacer(0,20));
		
		stage.show();
		stage.setOnCloseRequest(v -> {closed=true;} );
		
	}
	private Region makeSpacer(double width, double height) {
		Region spacer = new Region();
		if (width>0) {
			spacer.setPrefWidth(width);
		}
		if (height>0) {
			spacer.setPrefHeight(height);
		}
		return spacer;
	}
	private void makeNodePropertiesArea() {
		Label label = new Label("Node properties");
		label.setTextFill(Color.ANTIQUEWHITE);
		label.setFont(labelFont);
		HBox hbox = new HBox();
		hbox.getChildren().add(makeSpacer(50,0));
		hbox.setAlignment(Pos.CENTER);
		vbox.getChildren().add(label);
		vbox.getChildren().add(hbox);
		final TextArea textArea= new TextArea();
		textArea.setPrefRowCount(5);
		textArea.setEditable(false);
		StringBuilder sb = new StringBuilder();
		for(String key: nodeProperties.getMapFromPropertyNameToClasses().keySet()) {
			sb.append(key+ ":");
			List<Class<?>> classes=nodeProperties.getMapFromPropertyNameToClasses().get(key);
			for(Class<?> clazz: classes) {
				String string = " " + clazz.getSimpleName();
				sb.append(string);
				Set<Object> objects = nodeProperties.getMapFromPropertyNameToValues().get(key);
				int col = string.length();
				for(Object object:objects) {
					String value;
					if (object instanceof Double) {
						value = numberFormat.format((Double)object);
					} else {
						value = object.toString();
					}
					col += 2+value.length();
					if (col>=80) {
						sb.append(" ...");
						break;
					}
					sb.append("  ");
					sb.append(value);
				}
				
			}
			sb.append("\n");
		}
		textArea.setText(sb.toString());
		hbox.getChildren().add(textArea);
	}
	private void makeFilterQueryTextArea() {
		Label label = new Label("Node filter query:");
		label.setTextFill(Color.ANTIQUEWHITE);
		label.setFont(labelFont);
		Label help = new Label("For example:  weight > 1 and ( job ~ \"senior*\" or gender = \"female\") and NOT ( state = null)");
		help.setFont(new Font(10));
		help.setTextFill(Color.ANTIQUEWHITE);
		HBox hbox = new HBox();
		hbox.setAlignment(Pos.CENTER);
		hbox.getChildren().add(makeSpacer(50,0));
		vbox.getChildren().add(label);
		vbox.getChildren().add(help);
		vbox.getChildren().add(hbox);
		final TextArea textArea = new TextArea();

		textArea.setPrefRowCount(3);
		BorderWidths widths = new BorderWidths(5);
		BorderStroke borderStroke= new BorderStroke(Color.CADETBLUE,BorderStrokeStyle.SOLID,CornerRadii.EMPTY,widths);
		Border border = new Border(borderStroke);
		textArea.setBorder(border);
		hbox.getChildren().add(textArea);
		textArea.setOnKeyPressed(ke -> {
			if (ke.getCode() == KeyCode.ENTER) {
				System.out.println(textArea.getText());
				try {
					NodeFilter rowFilter = new NodeFilter(textArea.getText(), nodeProperties);
					final List<Node3D> nodesToDisplay = new ArrayList<>();
					for(Node3D node: visualizer.getSavedAllNodes()) {
						if (rowFilter.shouldInclude(node)) {
							nodesToDisplay.add(node);
							node.setVisible(true);
						} else {
							node.setVisible(false);
						}
					}
					final Node3D[] nodesTosDisplayArray = new Node3D[nodesToDisplay.size()];
					nodesToDisplay.toArray(nodesTosDisplayArray);
					System.out.println("Filtered to " + nodesToDisplay.size() + " nodes");
					// TODO: there is a bug involving sorting savedAllNodes for importance and visibility
					visualizer.setNodesToDisplay(nodesTosDisplayArray); 
					visualizer.computeConnectedComponentsFromNodesToDisplay();
					visualizer.requestReplaceOnePass();
					stage.hide();
					System.out.println("hid FilterStage");
				} catch (Throwable exc) {
					System.err.println("Illegal query: " + exc.getMessage());
					exc.printStackTrace();
				}
			}
		});
	}
	private void makeButton() {
		Button button = new Button("focus");
		button.setTranslateX(width-70);
		button.setTranslateY(10);
		button.setOnAction(e -> {
			this.closed = true;
			stage.close();
		});
		root.getChildren().add(button);
		
		final List<Integer> list = new ArrayList<>();
		for(int i=1;i<7;i++) {list.add(i);}
		final ObservableList<Integer> items = FXCollections.observableList(list);
		//...
		final ComboBox<Integer> hider = new ComboBox<>(items);
		hider.setTranslateX(width-70);
		hider.setTranslateY(40);
		hider.setValue(1);
		hider.setOnAction(e -> {
		});
		root.getChildren().add(hider);
}
	public boolean isClosed() {
		return closed;
	}
	public void show() {
		stage.show();
	}
}
