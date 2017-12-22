package org.onosproject.loadBalanceRouting.intf;

import org.onosproject.net.ElementId;
import org.onosproject.net.Path;
import org.onosproject.net.topology.Topology;

import java.util.Set;

/**
 * Created by jiayit on 8/3/17.
 */
public interface loadBalanceRoutingService {
    Set<Path> getLoadPaths(Topology topo, ElementId src, ElementId dst);
    Set<Path> getLoadPaths(ElementId src, ElementId dst);
}
