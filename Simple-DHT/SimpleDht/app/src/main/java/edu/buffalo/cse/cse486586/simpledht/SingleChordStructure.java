package edu.buffalo.cse.cse486586.simpledht;

import java.io.Serializable;
import java.util.TreeMap;

public class SingleChordStructure implements Serializable {
    String currentAVDPort;
    String destinationAVDPort;
    TreeMap<String,String> data;
    String requestType;
}
