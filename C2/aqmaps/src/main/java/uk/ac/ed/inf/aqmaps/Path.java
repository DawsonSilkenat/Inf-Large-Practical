package uk.ac.ed.inf.aqmaps;

import java.util.ArrayList;
import java.util.List;
import com.mapbox.geojson.Point;

public class Path {
    private Point start;
    private List<Point> movePositions;
    private List<Integer> moveAngles;
    
    public Path(Point position) {
        this.start = position;
        movePositions = new ArrayList<>();
        moveAngles = new ArrayList<>();
    }
    
    private Path(int angle, double distance, Path parent) {
        var endLng = parent.getEndPosition().longitude() + Math.cos(angle * Math.PI / 180.0) * distance;
        var endLat = parent.getEndPosition().latitude() + Math.sin(angle * Math.PI / 180.0) * distance;
        
        movePositions = new ArrayList<>();
        movePositions.addAll(parent.movePositions);
        movePositions.add(Point.fromLngLat(endLng, endLat));
        
        moveAngles = new ArrayList<>();
        moveAngles.addAll(parent.moveAngles);
        moveAngles.add(angle);
    }
    
    
    public Path extend(int angle, double distance) {
        return new Path(angle, distance, this);
    }
    
    public List<Point> getPositions() {
        return movePositions;
    }
    
    public List<Integer> getMoveAngles() {
        return moveAngles;
    }
    
    // The end position of a path is either the last element of move positions or the starting position if no moves have been made
    public Point getEndPosition() {
        if (movePositions.size() == 0) {
            return start;
        }
        return movePositions.get(movePositions.size() - 1);
    }    
}
