package grapher;

import java.util.Map;
import java.util.TreeMap;

/**
 * There is a single Edge object per pair node1 and node2 (regardless of order).
 * So users may need to check which of node1 and node2 is the node they're expecting.
 * @author Don Smith
 *
 */
public class Edge {
	private final Node3D node1;
	private final Node3D node2;
	private final Map<String,Object> properties = new TreeMap<>();
	public Edge(Node3D node1, Node3D node2) {
		this.node1 = node1;
		this.node2 = node2;
	}
	@Override
	public String toString() {
		return node1.getId() + "--" + node2.getId() + ": " + properties;
	}
	public Node3D getNode1() {return node1;}
	public Node3D getNode2() {return node2;}
	public Object getProperty(String name) {return properties.get(name);}
	public Map<String,Object> getProperties() {
		return properties; 
	}
	public void addProperty(String key, Object value) {
		properties.put(key,value);
	}
	public double getWeight() {
		Object weightObject= properties.get("weight");
		if (weightObject!=null) {
			if (weightObject instanceof Double) {
				return ((Double)weightObject).doubleValue();
			}
			if (weightObject instanceof String) {
				try {
					Double value =  Double.parseDouble(weightObject.toString());
					properties.put("weight",value);
					return value;
				}  catch (NumberFormatException exc) {}
			}
		}
		return 1.0;
	}
}
