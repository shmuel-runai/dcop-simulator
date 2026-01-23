package dcop.common.network;

import dcop.common.DCOPProblem;

/**
 * Interface for DCOP network builders.
 * Each DCOP algorithm implementation provides its own builder
 * that creates algorithm-specific agent instances.
 * 
 * Builder configuration (domainSize, algorithmSeed, algorithm-specific params)
 * should be passed to the concrete builder's constructor.
 */
public interface IDCOPNetworkBuilder {
    
    /**
     * Builds a DCOP network from a problem instance.
     * Creates all agent objects with proper configuration.
     * 
     * @param problem The DCOP problem
     * @return A DCOPNetwork ready for deployment
     */
    DCOPNetwork buildNetwork(DCOPProblem problem);
}
