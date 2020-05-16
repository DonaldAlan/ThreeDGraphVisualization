package grapher;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;


public class MessageBox {
	private static final int width=450;
	private static final int height=400;
	private static final Font font = new Font("New Times Roman",16);
	private Text text;
	private Stage stage;
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
				plot.focus(this.node);
				this.closed = true;
				stage.close();
			});
			group.getChildren().add(button);
			
			//...
			Button hideButton = new Button("hide");
			hideButton.setTranslateX(width-70);
			hideButton.setTranslateY(40);
			hideButton.setOnAction(e -> {
				plot.hide(node);
			});
			group.getChildren().add(hideButton);
		}
		
		text = new Text(message);
		text.setFont(font);
		group.getChildren().addAll(text);
		scene.setFill(Color.ANTIQUEWHITE);
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
