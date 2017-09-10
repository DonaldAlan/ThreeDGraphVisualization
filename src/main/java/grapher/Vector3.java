package grapher;

import java.util.concurrent.ThreadLocalRandom;

import javafx.geometry.Point3D;

public class Vector3 {	
	private double x=0;
	private double y=0;
	private double z=0;
	public Vector3(double x,double y, double z) {
		this.x=x;
		this.y=y;
		this.z=z;
	}
	@Override
	public String toString() {
		return "{" + ConnectedComponent.toString(x) + "," + ConnectedComponent.toString(y) + "," + ConnectedComponent.toString(z) + "}";
	}
	public double length() {
		return Math.sqrt(x*x+y*y+z*z);
	}
	public void randomUnitVector() {
		x=ThreadLocalRandom.current().nextDouble();
		y=ThreadLocalRandom.current().nextDouble();
		z=ThreadLocalRandom.current().nextDouble();
		normalize();
	}
	public void multiply(double d) {
		x*=d;
		y*=d;
		z*=d;
	}
	public void add(Vector3 v) {
		x+= v.x;
		y+= v.y;
		z+= v.z;
	}
	/**
	 * To length 1.
	 */
	public void normalize() {
		double length=length();
		x=x/length;
		y=y/length;
		z=z/length;
	}
	public void substract(Vector3 v) {
		x-= v.x;
		y-= v.y;
		z-= v.z;
	}
	public double getX() {
		return x;
	}
	public void setX(double x) {
		this.x = x;
	}
	public double getY() {
		return y;
	}
	public void setY(double y) {
		this.y = y;
	}
	public double getZ() {
		return z;
	}
	public void setZ(double z) {
		this.z = z;
	}
	public void add(double x2, double y2, double z2) {
		x+=x2;
		y+=y2;
		z+=z2;
	}
	public void setToMinus(Point3D p1, Point3D p2) {
		x=p1.getX()-p2.getX();
		y=p1.getY()-p2.getY();
		z=p1.getZ()-p2.getZ();
	}
	public void set(double x, double y, double z) {
		this.x=x;
		this.y=y;
		this.z=z;
	}
}
