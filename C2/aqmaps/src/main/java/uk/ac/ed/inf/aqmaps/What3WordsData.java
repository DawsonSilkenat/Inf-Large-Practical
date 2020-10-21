package uk.ac.ed.inf.aqmaps;

public class What3WordsData {
    private String country;
    
    private Square square;
    private static class Square {
        private SouthWest southwest;
        private static class SouthWest {
            private double lng;
            private double lat;
        }
        
        private NorthEast northeast;
        private static class NorthEast {
            private double lng;
            private double lat;
        }
    }
    
    private String nearestPlace;
    
    Coordinates coordinates;
    private static class Coordinates {
        private double lng;
        private double lat;
        
        public double getLng() {
            return lng;
        }

        public double getLat() {
            return lat;
        }
    }
    
    private String words;
    private String language;
    private String map;
    
    public double getLng() {
        return coordinates.getLng();
    }
    
    public double getLat() {
        return coordinates.getLat();
    }
}
