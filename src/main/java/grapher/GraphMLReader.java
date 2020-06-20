package grapher;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.w3c.dom.Document;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;

public class GraphMLReader {
	private static void parseReadInEntireDocument(String path) throws ParserConfigurationException, IOException, SAXException {
	    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
	    factory.setValidating(false);
	    factory.setIgnoringElementContentWhitespace(true);
	    DocumentBuilder builder = factory.newDocumentBuilder();
	    File file = new File(path);
	    if (!file.exists()) {
	    	throw new IllegalArgumentException(path + " does not exist");
	    }
	    Document doc = builder.parse(file);
	    
	    // Do something with the document here.
	}
	private static enum Type {
		Int,Long,String,Double,Float;
	}
	public static Map<String,Node3D> parseWithSax(String path, Map<String, Node3D> mapFromNodeIdToNode) throws Exception {
	    SAXParserFactory factory = SAXParserFactory.newInstance();
	    factory.setValidating(true);
	    SAXParser saxParser = factory.newSAXParser();
	    File file = new File(path);
	    if (!file.exists()) {
	    	throw new IllegalArgumentException(path + " does not exist");
	    }
/*
<node id="n32675">
<data key="label">Node 32675</data>
<data key="size">4.0</data>
<data key="r">0</data>
<data key="g">0</data>
<data key="b">0</data>
<data key="x">-2143.6501</data>
<data key="y">540.61694</data>
</node>


<edge source="n11667" target="n11668">
<data key="edgeid">51370</data>
<data key="weight">1.0</data>
</edge>

 */
	   final Map<String,Type> keyToTypeMap = new TreeMap<>();
	   final DefaultHandler2 handler = new DefaultHandler2() {
	    	Node3D node=null;
	    	String keyName=null;
	    	Edge edge=null;
	    	@Override
	    	public void startEntity(String name) {
	    		System.out.println(name);
	    	}
	    	@Override
	    	public void startElement(String uri,String localName,String qName,Attributes attributes) throws SAXException {
	    		if (qName.equals("node")) {
	    			String id=attributes.getValue("id");
	    			node=new Node3D(id);
	    			edge=null;
	    			mapFromNodeIdToNode.put(id,node);
	    			//showAttributes(attributes);
	    		} else if (qName.equals("edge")) {
	    			String sourceId=attributes.getValue("source");
	    			String targetId=attributes.getValue("target");
	    			Node3D source = mapFromNodeIdToNode.get(sourceId);
	    			node=null;
	    			if (source==null) {
	    				source = new Node3D(sourceId);
	    				mapFromNodeIdToNode.put(sourceId, source);
	    			}
	    			Node3D target = mapFromNodeIdToNode.get(targetId);
	    			if (target==null) {
	    				target = new Node3D(targetId);
	    				mapFromNodeIdToNode.put(targetId, target);
	    			}
	    			edge = source.addEdge(target);
	    			target.addEdge(source,edge);
	    			for(int i=0;i<attributes.getLength();i++) {
	    				String name=attributes.getLocalName(i);
	    				String value = attributes.getValue(i);
	    				edge.addProperty(name, value); 
	    			}
	    		} else if (node!=null && qName.equals("data")) {
	    			keyName=attributes.getValue("key");
	    		} else if (edge!=null && qName.equals("data")) {
	    			keyName=attributes.getValue("key");
	    		} else if (qName.equals("key")) {	
	    			processTypes(attributes);
	    		}
	    	}
/*
 <key id="x" for="node" attr.name="x" attr.type="double"/>
 <key id="tooltip" for="node" attr.name="tooltip" attr.type="string"/>
 <key id="y" for="node" attr.name="y" attr.type="double"/>

<key attr.name="label" attr.type="string" for="node" id="label"/>
<key attr.name="Edge Label" attr.type="string" for="edge" id="edgelabel"/>
<key attr.name="weight" attr.type="double" for="edge" id="weight"/>
<key attr.name="Edge Id" attr.type="string" for="edge" id="edgeid"/>
<key attr.name="r" attr.type="integer" for="node" id="r"/>
<key attr.name="g" attr.type="integer" for="node" id="g"/>
<key attr.name="b" attr.type="integer" for="node" id="b"/>
<key attr.name="x" attr.type="float" for="node" id="x"/>
<key attr.name="y" attr.type="float" for="node" id="y"/>
<key attr.name="size" attr.type="float" for="node" id="size"/>

  attr.name/attr.name: CDATA = label
  attr.type/attr.type: CDATA = string
  for/for: CDATA = node
  id/id: CDATA = label
  
  attr.name/attr.name: CDATA = Edge Label
  attr.type/attr.type: CDATA = string
  for/for: CDATA = edge
  id/id: CDATA = edgelabel
  
  attr.name/attr.name: CDATA = weight
  attr.type/attr.type: CDATA = double
  for/for: CDATA = edge
  id/id: CDATA = edgeid
...
 */
	    	private void processTypes(Attributes attributes) {
	    		//showAttributes(attributes);	System.out.println();
	    		String type=attributes.getValue("attr.type");
	    		String qName=attributes.getValue("attr.name");
	    		//System.out.println(qName + " of type " + type);
	    		keyToTypeMap.put(qName, getType(type));
			}
	    	private Type getType(String type) {
	    		switch (type) {
	    		case "string": return Type.String;
	    		case "int": return Type.Int; 
	    		case "integer": return Type.Long;
	    		case "double": return Type.Double;
	    		case "float": return Type.Float;
	    		default:
	    			System.err.println("Warning: no type for " + type);
	    			return Type.String;
	    		}
	    	}
			private void showAttributes(Attributes attributes) {
				for(int i=0;i<attributes.getLength();i++) {
					String value=attributes.getValue(i);
					String type=attributes.getType(i);
					String qName=attributes.getQName(i);
					String localName=attributes.getLocalName(i);
					System.out.println("  " + qName + "/" + localName + ": " + type + " = " + value);
				}
			}
			@Override
	    	public void characters(char[] chs,int start,int length) {
	    		if (node!=null && keyName!=null) {
	    			String valueAsString=new String(chs,start,length);
	    			Object value;
	    			try {
	    				value=convert(keyName,valueAsString);
	    			} catch (Exception exc) {
	    				System.err.println(exc.getMessage());
	    				value=valueAsString;
	    			}
	    			node.getAttributes().put(keyName, value);
	    			keyName=null;
	    		} else if (edge!=null && keyName!=null) {
	    			String valueAsString=new String(chs,start,length);
	    			Object value;
	    			try {
	    				value=convert(keyName,valueAsString);
	    			} catch (Exception exc) {
	    				System.err.println(exc.getMessage());
	    				value=valueAsString;
	    			}
	    			edge.addProperty(keyName, value);
	    			keyName=null;
	    		}
	    	}
	    	@Override
	    	public void endElement(String uri, String localName, String qName) {
	    		if (qName.equals("node")) {
	    			node=null;
	    			keyName=null;
	    		}
	    	}
	    	Object convert(String keyName,String valueAsString) {
	    		Type type = keyToTypeMap.get(keyName);
	    		if (type==null) {
	    			System.err.println("Warning: no type for " + keyName);
	    			return valueAsString;
	    		}
	    		switch (type) {
	    		case Int:
	    			return Integer.parseInt(valueAsString);
	    		case Long:
	    			return Long.parseLong(valueAsString);
	    		case String:
	    			return valueAsString;
	    		case Double:
	    			return Double.parseDouble(valueAsString);
	    		case Float:
	    			return Double.parseDouble(valueAsString);
	    		default:
	    				throw new IllegalStateException();
	    		}
	    	}
	    };
		saxParser.parse(file, handler);
		System.out.println(keyToTypeMap); 
		return mapFromNodeIdToNode;
//		Node3D[] nodes = new Node3D[idToNodeMap.size()];
//		idToNodeMap.values().toArray(nodes);
//		int countEdges=0;
//		for(Node3D n:nodes) {
//			countEdges+= n.getNeighbors().size();
//		}
//		System.out.println("Read " + nodes.length + " nodes, " + countEdges + " edges");
//		System.out.println(nodes[0]);
//		System.out.println(nodes[1]);
//		return nodes;
	}
	
	private static void showNodeAttributes(Map<String,Node3D> idToNodeMap) {
		int count=0;
		for(Node3D n: idToNodeMap.values()) {
			for(Map.Entry<String,Object> entry:n.getAttributes().entrySet()) {
				System.out.println(entry.getKey() + " = " + entry.getValue() + ": " + entry.getValue().getClass().getName());
			}
			System.out.println();
			count++;
			if (count>2) {
				break;
			}
		}
	}
	public static void main(String[] args) {
		String path="D:/Java/jzy3d-graphs/data/competition/reduced.graphml";
		//path="D:/Java/jzy3d-graphs/data/airlines.graphml";
		Map<String,Node3D> idToNodeMap= new HashMap<>();
		
		try {
			parseWithSax(path,idToNodeMap);
			showNodeAttributes(idToNodeMap);
			System.out.println("Read " + idToNodeMap.size() + " nodes");
		} catch (Throwable thr) {
			thr.printStackTrace();
			System.exit(1);
		}
	}
}
