package grapher;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class NodeProperties {
	private final int MAX_SET_SIZE= 50;
	private final Map<String, List<Class<?>>> mapFromPropertyNameToClasses = new TreeMap<>();
	private final Map<String, Set<Object>> mapFromPropertyNameToValues = new TreeMap<>();
	public NodeProperties(Node3D[] nodes) {
		buildMapFromPropertyNameToClass(nodes);
	}
	public Map<String, List<Class<?>>> getMapFromPropertyNameToClasses() {
		return mapFromPropertyNameToClasses;
	}
	private void buildMapFromPropertyNameToClass(Node3D[] nodes) {
		for(Node3D node:nodes) {
			for(Map.Entry<String,Object> entry:node.getProperties().entrySet()) {
				if (entry.getValue()==null) {
					continue;
				}
				final String key=entry.getKey();
				Set<Object> set = mapFromPropertyNameToValues.get(key);
				if (set==null) {
					set = new HashSet<>();
					mapFromPropertyNameToValues.put(key,set);
					set.add(entry.getValue());
				} else if (set.size()<MAX_SET_SIZE) {
					set.add(entry.getValue());
				}
			
				List<Class<?>> list = mapFromPropertyNameToClasses.get(entry.getKey());
				if (list==null) {
					list=new ArrayList<>();
					mapFromPropertyNameToClasses.put(entry.getKey(), list);
				}
				Class<?> clazz=entry.getValue().getClass();
				if (!list.contains(clazz)) {
					list.add(clazz);
				}
			}
		}
		System.out.println("Types:");
		StringBuilder sb=new StringBuilder();
		for(String key: mapFromPropertyNameToClasses.keySet()) {
			sb.append("  " + key + ":");
			for(Class<?> clazz: mapFromPropertyNameToClasses.get(key)) {
				sb.append(" " + clazz.getSimpleName());
			}
			Set<Object> values = mapFromPropertyNameToValues.get(key);
			if (values==null) {
				sb.append(", values = (all null)");
			} else {
				boolean first=true;
				for(Object obj: values) {
					if (first) {
						first=false;
					} else {
						sb.append(",");
					}
					sb.append(" " + obj);
				}
				if (values.size() == MAX_SET_SIZE) {
					sb.append(" ..., count = " + values.size());
				} else {
					sb.append(", count = " + values.size());
				}
			}
			sb.append("\n");
		}
		System.out.println(sb.toString());
	}
}
