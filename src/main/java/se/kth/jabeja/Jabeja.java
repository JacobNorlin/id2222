package se.kth.jabeja;

import jdk.nashorn.internal.runtime.FindProperty;
import org.apache.log4j.Logger;
import se.kth.jabeja.config.Config;
import se.kth.jabeja.config.NodeSelectionPolicy;
import se.kth.jabeja.io.FileIO;
import se.kth.jabeja.rand.RandNoGenerator;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Jabeja {
    final static Logger logger = Logger.getLogger(Jabeja.class);
    private final Config config;
    private HashMap<Integer/*id*/, Node/*neighbors*/> entireGraph;
    private final List<Integer> nodeIds;
    private int numberOfSwaps;
    private int round;
    private float T;
    private float Tr;
    private boolean resultFileCreated = false;

    //-------------------------------------------------------------------
    public Jabeja(HashMap<Integer, Node> graph, Config config) {
        this.entireGraph = graph;
        this.nodeIds = new ArrayList(entireGraph.keySet());
        this.round = 0;
        this.numberOfSwaps = 0;
        this.config = config;
        this.T = config.getTemperature();
        this.Tr = T;
    }



    private HashMap<Integer, Node> copyGraph(HashMap<Integer, Node> G){
        HashMap<Integer, Node> copy = new HashMap<Integer, Node>();
        for(Integer i : G.keySet()){
            Node n = new Node(G.get(i));
            copy.put(n.getId(), n);
        }
        return copy;
    }

    //-------------------------------------------------------------------
    public void startJabeja() throws IOException {
        for (round = 0; round < config.getRounds(); round++) {
            HashMap<Integer, Node> oldGraph = copyGraph(entireGraph);
            //float oldCost = E(nodeIds);
            for (int id : entireGraph.keySet()) {
                sampleAndSwap(id);
            }

            //one cycle for all nodes have completed.
            //reduce the temperature
            saCoolDown(oldGraph);
            Tr = T;
            report();
        }
    }

    private boolean maybeMove(float oldCost, float newCost, float T){
        float r = new Random().nextFloat();
        double p = Math.pow(Math.E, (oldCost-newCost)/T);
        return r < p;
    }

    /**
     * Simulated analealing cooling function
     */
    private void saCoolDown(HashMap<Integer, Node> oldGraph) {
        // TODO for second task
        float newCost = E(entireGraph);
        float oldCost = E(oldGraph);
        System.out.println(oldCost +", "+ newCost +" "+(oldGraph == entireGraph));
        if (!(newCost < oldCost || (newCost >= oldCost && maybeMove(oldCost, newCost, T))))
        {
            System.out.println("rejecting");
            entireGraph = new HashMap<Integer, Node>(oldGraph);
        }
        if(T > 1){
            T *= 0.95;
        }
        if (T < 1) {
            T = 1;
        }
    }

    private float E(HashMap<Integer, Node> G){
        int sum = 0;
        for (int i : G.keySet()) {
            Node node = G.get(i);
            int nodeColor = node.getColor();
            ArrayList<Integer> nodeNeighbours = node.getNeighbours();


            if (nodeNeighbours != null) {
                for (int n : nodeNeighbours) {
                    Node p = G.get(n);
                    int pColor = p.getColor();

                    if (nodeColor != pColor)
                        sum++;
                }
            }
        }
        return sum/2;
    }


    /**
     * Sample and swap algorith at node p
     *
     * @param nodeId
     */
    private void sampleAndSwap(int nodeId) {
        Node p = entireGraph.get(nodeId);
        Node q = null;

        if (config.getNodeSelectionPolicy() == NodeSelectionPolicy.HYBRID
                || config.getNodeSelectionPolicy() == NodeSelectionPolicy.LOCAL) {
            q = findPartner(nodeId, p.getNeighbours().stream().toArray(Integer[]::new));
            // swap with random neighbors
        }

        if (config.getNodeSelectionPolicy() == NodeSelectionPolicy.HYBRID
                || config.getNodeSelectionPolicy() == NodeSelectionPolicy.RANDOM) {
            // if local policy fails then randomly sample the entire graph
            if (q == null) {
                q = findPartner(nodeId, getSample(nodeId));
            }
        }

        if (q != null) {
            if(swapColors(p, q) == true){
            }
        }

        Tr -= config.getDelta();
        if (Tr < 1) {
            Tr = 1;
        }
    }


    public boolean swapColors(Node p, Node q) {
        int dpq = getDegree(p, q.getColor());
        int dpp = getDegree(p, p.getColor());
        int dqp = getDegree(q, p.getColor());
        int dqq = getDegree(q, q.getColor());
        float a = config.getAlpha();
        double c1 = (Math.pow(dpq, a) + Math.pow(dqp, a));
        double c2 = (Math.pow(dpp, a) + Math.pow(dqq, a));
        if (c1 * Tr > c2) {
            int old = p.getColor();
            p.setColor(q.getColor());
            q.setColor(old);
            numberOfSwaps += 1;
            return true;
        }
        return false;
    }

    public Node findPartner(int p, Integer[] nodes) {


        Node bestPartner = null;
        double highestBenefit = 0;


        Node pNode = entireGraph.get(p);

        double old = 0;
        double newV = 0;
        float a = config.getAlpha();

        for (int q : nodes) {
            Node qNode = entireGraph.get(q);

            int dpp = getDegree(pNode, pNode.getColor());
            int dqq = getDegree(qNode, qNode.getColor());
            old = Math.pow(dpp, a) + Math.pow(dqq, a);
            int dpq = getDegree(pNode, qNode.getColor());
            int dqp = getDegree(qNode, pNode.getColor());
            newV = Math.pow(dpq, a) + Math.pow(dqp, a);

            if (newV * Tr > old && newV > highestBenefit) {
                bestPartner = qNode;
                highestBenefit = newV;
            }
        }

        return bestPartner;
    }

    /**
     * The the degreee on the node based on color
     *
     * @param node
     * @param colorId
     * @return how many neighbors of the node have color == colorId
     */
    private int getDegree(Node node, int colorId) {
        int degree = 0;
        for (int neighborId : node.getNeighbours()) {
            Node neighbor = entireGraph.get(neighborId);
            if (neighbor.getColor() == colorId) {
                degree++;
            }
        }
        return degree;
    }

    /**
     * Returns a uniformly random sample of the graph
     *
     * @param currentNodeId
     * @return Returns a uniformly random sample of the graph
     */
    private Integer[] getSample(int currentNodeId) {
        int count = config.getUniformRandomSampleSize();
        int rndId;
        int size = entireGraph.size();
        ArrayList<Integer> rndIds = new ArrayList<Integer>();

        while (true) {
            rndId = nodeIds.get(RandNoGenerator.nextInt(size));
            if (rndId != currentNodeId && !rndIds.contains(rndId)) {
                rndIds.add(rndId);
                count--;
            }

            if (count == 0)
                break;
        }

        Integer[] ids = new Integer[rndIds.size()];
        return rndIds.toArray(ids);
    }

    /**
     * Get random neighbors. The number of random neighbors is controlled using
     * -closeByNeighbors command line argument which can be obtained from the config
     * using {@link Config#getRandomNeighborSampleSize()}
     *
     * @param node
     * @return
     */
    private Integer[] getNeighbors(Node node) {
        ArrayList<Integer> list = node.getNeighbours();
        int count = config.getRandomNeighborSampleSize();
        int rndId;
        int index;
        int size = list.size();
        ArrayList<Integer> rndIds = new ArrayList<Integer>();

        if (size <= count)
            rndIds.addAll(list);
        else {
            while (true) {
                index = RandNoGenerator.nextInt(size);
                rndId = list.get(index);
                if (!rndIds.contains(rndId)) {
                    rndIds.add(rndId);
                    count--;
                }

                if (count == 0)
                    break;
            }
        }

        Integer[] arr = new Integer[rndIds.size()];
        return rndIds.toArray(arr);
    }


    /**
     * Generate a report which is stored in a file in the output dir.
     *
     * @throws IOException
     */
    private void report() throws IOException {
        int grayLinks = 0;
        int migrations = 0; // number of nodes that have changed the initial color
        int size = entireGraph.size();

        for (int i : entireGraph.keySet()) {
            Node node = entireGraph.get(i);
            int nodeColor = node.getColor();
            ArrayList<Integer> nodeNeighbours = node.getNeighbours();

            if (nodeColor != node.getInitColor()) {
                migrations++;
            }

            if (nodeNeighbours != null) {
                for (int n : nodeNeighbours) {
                    Node p = entireGraph.get(n);
                    int pColor = p.getColor();

                    if (nodeColor != pColor)
                        grayLinks++;
                }
            }
        }

        int edgeCut = grayLinks / 2;

        logger.info("round: " + round +
                ", edge cut:" + edgeCut +
                ", swaps: " + numberOfSwaps +
                ", migrations: " + migrations);

        saveToFile(edgeCut, migrations);
    }

    private void saveToFile(int edgeCuts, int migrations) throws IOException {
        String delimiter = "\t\t";
        String outputFilePath;

        //output file name
        File inputFile = new File(config.getGraphFilePath());
        outputFilePath = config.getOutputDir() +
                File.separator +
                inputFile.getName() + "_" +
                "NS" + "_" + config.getNodeSelectionPolicy() + "_" +
                "GICP" + "_" + config.getGraphInitialColorPolicy() + "_" +
                "T" + "_" + config.getTemperature() + "_" +
                "D" + "_" + config.getDelta() + "_" +
                "RNSS" + "_" + config.getRandomNeighborSampleSize() + "_" +
                "URSS" + "_" + config.getUniformRandomSampleSize() + "_" +
                "A" + "_" + config.getAlpha() + "_" +
                "R" + "_" + config.getRounds() + ".txt";

        if (!resultFileCreated) {
            File outputDir = new File(config.getOutputDir());
            if (!outputDir.exists()) {
                if (!outputDir.mkdir()) {
                    throw new IOException("Unable to create the output directory");
                }
            }
            // create folder and result file with header
            String header = "# Migration is number of nodes that have changed color.";
            header += "\n\nRound" + delimiter + "Edge-Cut" + delimiter + "Swaps" + delimiter + "Migrations" + delimiter + "Skipped" + "\n";
            FileIO.write(header, outputFilePath);
            resultFileCreated = true;
        }

        FileIO.append(round + delimiter + (edgeCuts) + delimiter + numberOfSwaps + delimiter + migrations + "\n", outputFilePath);
    }
}
