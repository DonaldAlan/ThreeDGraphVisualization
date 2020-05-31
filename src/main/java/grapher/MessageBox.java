package grapher;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;


public class MessageBox {
	private static final int width=450;
	private static final int height=400;
	private static final Font font = new Font("New Times Roman",16);
	private Text text;
	private final Stage stage;
	private Group group= new Group();
	private Scene scene = new Scene(group);
	private boolean closed=false;
	private Node3D node;
	public MessageBox(String message, String title) {
		this(message,title,null,null);
		stage.setX(20);
		stage.setY(height+100);
		scene.setFill(Color.FLORALWHITE);
	}
	public MessageBox(final String message, final String title, final Node3D node, final Visualizer plot) {
		this.node=node;
		stage = new Stage();
		stage.initModality(Modality.NONE);
		stage.setTitle(title);
		stage.setMinWidth(width);
		stage.setMinHeight(height);
		stage.setAlwaysOnTop(true);
		if (plot!=null) {
			Button button = new Button("focus");
			button.setTranslateX(width-70);
			button.setTranslateY(10);
			button.setOnAction(e -> {
				plot.focus(this.node,1);
				this.closed = true;
				stage.close();
			});
			group.getChildren().add(button);
			
			final List<Integer> list = new ArrayList<>();
			for(int i=1;i<7;i++) {list.add(i);}
			final ObservableList<Integer> items = FXCollections.observableList(list);
			//...
			final ComboBox<Integer> hider = new ComboBox<>(items);
			hider.setTranslateX(width-70);
			hider.setTranslateY(40);
			hider.setValue(1);
			hider.setOnAction(e -> {
				plot.hide(node, hider.getValue());
			});
			group.getChildren().add(hider);
		}
		
		text = new Text(message);
		text.setFont(font);
		group.getChildren().addAll(text);
		scene.setFill(Color.ANTIQUEWHITE);
		scene.setOnKeyPressed(ke -> {
			switch (ke.getCode()) {
			case Q: 
				stage.hide();
				break;
			default:
				break;
			}
		});
		stage.setScene(scene);
		stage.show();
		stage.setOnCloseRequest(v -> {closed=true;} );
	}
	public void update(String message, String title, Node3D node) {
		this.node=node;
		stage.setTitle(title);
		text.setText(message);
		group.requestLayout();
	}
	public boolean isClosed() {
		return closed;
	}
}
