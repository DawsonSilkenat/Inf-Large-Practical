package uk.ac.ed.inf.heatmap;

import com.mapbox.geojson.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class App {
    
    /* 
     * Constants the program depends on are stored here so they can be changed easily
     * It has been assumed the confinement area is defined by a maximum and minimum longitude and latitude
     */
    private static final double MINIMUM_LONGITUDE = -3.192473;
    private static final double MAXIMUM_LONGITUDE = -3.184319;
    private static final double MINIMUM_LATITUDE = 55.942617;
    private static final double MAXIMUM_LATITUDE = 55.946233;
    private static final int GRID_WIDTH = 10;
    private static final int GRID_HEIGHT = 10;
    private static final double FILL_OPACITY = 0.75;
    
    /*
     * These are also constants the program depends on, but are calculated from other constants rather 
     * than entered directly to reduce the chance of human error
     */
    private static final double RECTANGE_WIDTH = (MAXIMUM_LONGITUDE - MINIMUM_LONGITUDE) / GRID_HEIGHT;
    private static final double RECTANGE_HIGHT = (MAXIMUM_LATITUDE - MINIMUM_LATITUDE) / GRID_WIDTH; 
    
    
    public static void main(String[] args) throws IOException { 
        var input = new BufferedReader(new FileReader(new File(args[0])));
        var heatmap = new ArrayList<Feature>();
        
        for (int i = 0; i < GRID_HEIGHT; i++) {
            String currentLine = input.readLine();
            
            // We have run out of lines to read before expected, inform the user. We choose to interpret an empty line as the end of the file
            if (currentLine == null || currentLine.strip().equals("")) {
                System.out.println("expected " + GRID_HEIGHT + " rows but found " + i);
                break;
            }
            
            String[] values = currentLine.split(",");
            
            // Found a different number of values in a row than expected, so inform the user but otherwise proceed as normal
            if (values.length != GRID_WIDTH) {
                System.out.println("expected " + GRID_WIDTH + " values on row " + (i + 1) + " but found " + values.length);
            }
            
            // We choose to map at most the number of values specified in GRID_WIDTH, even if more are provided
            for (int j = 0; j < values.length && j < GRID_WIDTH; j++) {
                // Building the rectangle by specifying its vertices (corners), the coordinates of which are easy to calculate
                var vertices = new ArrayList<List<Point>>();
                vertices.add(new ArrayList<Point>());
                vertices.get(0).add(Point.fromLngLat(MINIMUM_LONGITUDE + j * RECTANGE_WIDTH, MAXIMUM_LATITUDE - i * RECTANGE_HIGHT));
                vertices.get(0).add(Point.fromLngLat(MINIMUM_LONGITUDE + j * RECTANGE_WIDTH, MAXIMUM_LATITUDE - (i + 1) * RECTANGE_HIGHT));
                vertices.get(0).add(Point.fromLngLat(MINIMUM_LONGITUDE + (j + 1) * RECTANGE_WIDTH, MAXIMUM_LATITUDE - (i + 1) * RECTANGE_HIGHT));
                vertices.get(0).add(Point.fromLngLat(MINIMUM_LONGITUDE + (j + 1) * RECTANGE_WIDTH, MAXIMUM_LATITUDE - i * RECTANGE_HIGHT));
                vertices.get(0).add(Point.fromLngLat(MINIMUM_LONGITUDE + j * RECTANGE_WIDTH, MAXIMUM_LATITUDE - i * RECTANGE_HIGHT));
                var rectangle = Feature.fromGeometry(Polygon.fromLngLats(vertices));
                
                /* 
                 * Adding the required properties to the rectangle before adding it to the heatmap. 
                 * If the properties cannot be processed, skip the cell but otherwise proceed as normal.
                 */
                try {
                    int prediction = Integer.parseInt(values[j].strip());
                    String color = getAirQualityColor(prediction); 
                    rectangle.addStringProperty("rgb-string", color); 
                    rectangle.addStringProperty("fill", color);
                    rectangle.addNumberProperty("fill-opacity", FILL_OPACITY);
                    heatmap.add(rectangle);
                } catch (Exception e) {
                    System.out.println("An error occurred when processing entry " + (j + 1) + " on row " + (i + 1) 
                            + ". Please insure this entry is a valid integer with a specified color mapping. This entry will be skipped.");
                }
            }
        }
        
        input.close();
        var output = new FileWriter("heatmap.geojson");
        output.write(FeatureCollection.fromFeatures(heatmap).toJson());
        output.close();
        System.out.println("Finished");
    }
    
    // Maps an air quality estimate to its associated color code
    private static String getAirQualityColor(int airQuality) {
        if (airQuality >= 0 && airQuality < 32) {
            return "#00ff00";
        } else if (airQuality >= 32 && airQuality < 64) {
            return "#40ff00";
        } else if (airQuality >= 64 && airQuality < 96) {
            return "#80ff00";
        } else if (airQuality >= 96 && airQuality < 128) {
            return "#c0ff00";
        } else if (airQuality >= 128 && airQuality < 160) {
            return "#ffc000";
        } else if (airQuality >= 160 && airQuality < 192) {
            return "#ff8000";
        } else if (airQuality >= 192 && airQuality < 224) {
            return "#ff4000";
        } else if (airQuality >= 224 && airQuality < 256) {
            return "#ff0000";
        }
        throw new IllegalArgumentException("Air quality was not within an excepted range");
    }
}
