package edu.buffalo.cse.cse486586.simpledynamo;

public class NodeStructure {
    String portNumber;
    NodeStructure predeccesor;
    NodeStructure successor;

    public NodeStructure(String portNumber, NodeStructure predeccesor, NodeStructure successor){

        this.portNumber = portNumber;
        this.predeccesor = predeccesor;
        this.successor = successor;
    }

    public void setPredeccesor (NodeStructure predeccesor){

        this.predeccesor = predeccesor;
    }
    public void setSuccessor(NodeStructure successor){

        this.successor = successor;
    }

    public String getPort(){

        return this.portNumber;
    }

    public NodeStructure getPredeccesor(){

        return this.predeccesor;
    }
    public NodeStructure getSuccessor(){

        return this.successor;
    }

}

