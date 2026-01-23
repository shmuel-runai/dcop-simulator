package projects.mpcTest.models.connectivityModels;

import sinalgo.models.ConnectivityModelHelper;
import sinalgo.nodes.Node;

/**
 * Full mesh connectivity model.
 * Every node is connected to every other node.
 */
public class FullMesh extends ConnectivityModelHelper {
    
    @Override
    protected boolean isConnected(Node from, Node to) {
        // In a full mesh, all nodes are connected to all other nodes
        return true;
    }
    
    @Override
    public String toString() {
        return "Full Mesh Connectivity";
    }
}

