package uk.ac.ed.inf.aqmaps;

import java.util.ArrayList;
import java.util.List;

import com.mapbox.geojson.Point;

public class Path implements Comparable<Path> {
    private Path parent;
    private Point position;
    private Point goal;
    private int moveAngle;
    private double cost;
    private double heuristic;
    
    public Path(Point position, Point goal) {
        parent = null;
        this.position = position;
        this.goal = goal;
        cost = 0;
        heuristic = findDistance(position, goal);
    }
    
    private Path(int angle, double distance, Path parent) {
        this.parent = parent;
        var endLng = parent.position.longitude() + Math.cos(angle * Math.PI / 180.0) * distance;
        var endLat = parent.position.latitude() + Math.sin(angle * Math.PI / 180.0) * distance;
        position = Point.fromLngLat(endLng, endLat);
        goal = parent.goal;
        moveAngle = angle;
        cost = parent.cost + distance;
        heuristic = findDistance(position, goal);
    }
    
    public Path extend(int angle, double distance) {
        return new Path(angle, distance, this);
    }
    
    public ArrayList<Point> getAllPositions() {
        if (parent == null) {
            return new ArrayList<Point>();
        } 
        var positions = parent.getAllPositions();
        positions.add(position);
        return positions;
    }
    
    public List<Integer> getAllMoveAngles() {
        if (parent == null) {
            return new ArrayList<Integer>();
        } 
        var angles = parent.getAllMoveAngles();
        angles.add(moveAngle);
        return angles;
    }
    
    public Point getEndPosition() {
        return position;
    }
    
    private double findDistance(Point start, Point end) {
        return Math.sqrt(Math.pow(start.longitude() - end.longitude(), 2) + Math.pow(start.latitude() - end.latitude(), 2));
    }

    @Override
    public int compareTo(Path path) {
        if (this.cost + this.heuristic - (path.cost + path.heuristic)  < 0) {
            return -1;
        } else if (this.cost + this.heuristic - (path.cost + path.heuristic)  > 0) {
            return 1;
        }
        return 0;
    }
    
}
