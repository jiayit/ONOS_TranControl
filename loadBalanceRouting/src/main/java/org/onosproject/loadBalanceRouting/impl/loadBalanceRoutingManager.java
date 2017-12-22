package org.onosproject.loadBalanceRouting.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Prefix;
import org.onosproject.common.DefaultTopology;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.incubator.net.PortStatisticsService;
import org.onosproject.loadBalanceRouting.intf.loadBalanceRoutingService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DefaultEdgeLink;
import org.onosproject.net.DefaultPath;
import org.onosproject.net.DeviceId;
import org.onosproject.net.EdgeLink;
import org.onosproject.net.Element;
import org.onosproject.net.ElementId;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.host.HostService;
import org.onosproject.net.intent.PathIntent;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.net.topology.DefaultTopologyVertex;
import org.onosproject.net.topology.LinkWeight;
import org.onosproject.net.topology.Topology;
import org.onosproject.net.topology.TopologyEdge;
import org.onosproject.net.topology.TopologyGraph;
import org.onosproject.net.topology.TopologyService;
import org.onosproject.net.topology.TopologyVertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by jiayit on 7/19/17.
 */
@Component
@Service
public class loadBalanceRoutingManager implements loadBalanceRoutingService {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private PortStatisticsService portStatisticsService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    private ApplicationId appId;

    private InternalPacketProcessor packetProcessor = new InternalPacketProcessor();

    private loadRouting loadRouting;

    @Activate
    public void activate() {
        appId = coreService.registerApplication("loadBalanceRouting");
        packetService.addProcessor(packetProcessor, PacketProcessor.director(0));
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
        log.info("Started");
    }

    @Deactivate
    public void deactivate() {
        packetService.removeProcessor(packetProcessor);
        packetService.cancelPackets(DefaultTrafficSelector.builder()
                                            .matchEthType(Ethernet.TYPE_IPV4).build(),
                                    PacketPriority.REACTIVE, appId);
        log.info("Stopped");
    }

    @Override
    public Set<Path> getLoadPaths(Topology topo, ElementId src, ElementId dst) {
        return loadRouting.getLoadPath(topo, src, dst);
    }

    @Override
    public Set<Path> getLoadPaths(ElementId src, ElementId dst) {
        return loadRouting.getLoadPath(src, dst);
    }

    private class InternalPacketProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext packetContext) {
            if (packetContext.isHandled()) {
                return;
            }

            Ethernet pkt = packetContext.inPacket().parsed();

            if (pkt.getEtherType() != Ethernet.TYPE_IPV4) {
                return;
            }

            HostId srcId = HostId.hostId(pkt.getSourceMAC());
            HostId dstId = HostId.hostId(pkt.getDestinationMAC());

            Set<Path> paths = getLoadPaths(srcId, dstId);

            if (paths.isEmpty()) {
                log.info("paths are not exsits");
                packetContext.block();
                return;
            }

            IPv4 ipPkt = (IPv4) pkt.getPayload();
            TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
                    .matchEthType(Ethernet.TYPE_IPV4)
                    .matchIPSrc(Ip4Prefix.valueOf(ipPkt.getSourceAddress(), Ip4Prefix.MAX_MASK_LENGTH))
                    .matchIPDst(Ip4Prefix.valueOf(ipPkt.getDestinationAddress(), Ip4Prefix.MAX_MASK_LENGTH));

            Path path = paths.iterator().next();
            PathIntent pathIntent = PathIntent.builder()
                    .path(path)
                    .selector(selector.build())
                    .appId(appId)
                    .priority(40000)
                    .treatment(DefaultTrafficTreatment.emptyTreatment())
                    .build();
        }
    }

    /**
     *Load balance module
     */
    private class loadRouting {
        private final ProviderId providerId = new ProviderId("jiayi","loadBalance");
        private final BandwidthLinkWeight bandwidthLinkWeight = new BandwidthLinkWeight();

        private Set<Path> getLoadPath(ElementId src, ElementId dst) {
            Topology currentTopo = topologyService.currentTopology();
            return getLoadPath(currentTopo, src, dst);
        }
        private Set<Path> getLoadPath(Topology topo, ElementId src, ElementId dst) {
            if (src instanceof DeviceId && dst instanceof DeviceId) {
                Set<List<TopologyEdge>> routes = findAllRoutes(topo, (DeviceId)src, (DeviceId)dst);
                Set<Path> paths = caculateRoutes(routes);
                Path path = selectedPath(paths);

                return ImmutableSet.of(path);
            }
            else if (src instanceof HostId && dst instanceof HostId) {
                Host srcHost = hostService.getHost((HostId) src);
                Host dstHost = hostService.getHost((HostId) dst);

                EdgeLink srcLink = getEdgeLink(srcHost, true);
                EdgeLink dstLink = getEdgeLink(dstHost, false);

                Set<List<TopologyEdge>> routes = findAllRoutes(topo, srcLink.dst().deviceId(), dstLink.dst().deviceId());
                Set<Path> paths = caculateRoutes(routes);
                Path path = selectedPath(paths);

                Path wholePath = buildWholePath(srcLink, dstLink, path);

                return ImmutableSet.of(wholePath);
            }
            return ImmutableSet.of();
        }


        private Set<List<TopologyEdge>> findAllRoutes(Topology topo, DeviceId src, DeviceId dst) {
            Set<List<TopologyEdge>> routes = new HashSet<List<TopologyEdge>>();
            dfsfindAllRoutes(new DefaultTopologyVertex(src),
                             new DefaultTopologyVertex(dst),
                             new ArrayList<TopologyEdge>(),
                             new ArrayList<TopologyVertex>(),
                             ((DefaultTopology) topo).getGraph(),
                             routes
            );
            return routes;
        }

        private void dfsfindAllRoutes(TopologyVertex src,
                                      TopologyVertex dst,
                                      List<TopologyEdge> passEdge,
                                      List<TopologyVertex> passDevice,
                                      TopologyGraph topologyGraph,
                                      Set<List<TopologyEdge>> result) {
            passDevice.add(src);
            Set<TopologyEdge> edges = topologyGraph.getEdgesFrom(src);
            edges.forEach(edge -> {
                TopologyVertex dstVex = edge.dst();
                if (dstVex.equals(dst)) {
                    passEdge.add(edge);
                    result.add(ImmutableList.copyOf(passEdge.iterator()));
                    passEdge.remove(edge);
                }
                else {
                    passEdge.add(edge);
                    dfsfindAllRoutes(dstVex, dst, passEdge, passDevice, topologyGraph, result);
                    passEdge.remove(edge);
                }
            });

            passDevice.remove(src);
        }
    // calculate the routes cost
        private Set<Path> caculateRoutes(Set<List<TopologyEdge>> routes) {
            Set<Path> paths = new HashSet<Path>();
            routes.forEach(route -> {
                double cost = maxLinkWeight(route);
                paths.add(parseEdgeToLink(route,cost));
            });
            return  paths;

        }

        private  double maxLinkWeight(List<TopologyEdge> edges) {
            double maxWeight = 0;
            for (TopologyEdge edge : edges) {
                double weight = bandwidthLinkWeight.weight(edge);
                maxWeight = maxWeight < weight ? weight : maxWeight;
            }
            return maxWeight;
        }

        private Path parseEdgeToLink(List<TopologyEdge> edges, double cost) {
            ArrayList<Link> links = new ArrayList<Link>();
            edges.forEach(edge -> {
                links.add(edge.link());
            });
            return new DefaultPath(providerId, links, cost);
        }

        // select a path

        private  Path selectedPath(Set<Path> paths) {
            if (paths.size() == 0) {
                return null;
            }
            return getMinHopPath(getMinCostPath(new ArrayList<Path>(paths)));
        }

        private List<Path> getMinCostPath(List<Path> paths) {
            final double measureTolerance = 0.05;

            paths.sort((p1, p2) -> p1.cost() > p2.cost() ? 1 : p1.cost() < p2.cost() ? -1 : 0);

            List<Path> minCostPath = new ArrayList<Path>();
            minCostPath.add(paths.get(0));

            for (int i = 1; i < paths.size(); i++) {
                if (paths.get(i).cost() - paths.get(0).cost() < measureTolerance ) {
                    minCostPath.add(paths.get(i));
                }
            }
            return minCostPath;
        }

        private Path getMinHopPath(List<Path> paths) {
            Path result = paths.get(0);
            for (int i = 1; i < paths.size(); i++) {
                result = paths.get(i).links().size() < result.links().size() ? result = paths.get(i) : result;
            }
            return result;
        }

        private EdgeLink getEdgeLink(Host host, boolean isIngress) {
            return new DefaultEdgeLink(providerId, new ConnectPoint(host.id(), PortNumber.portNumber(0)),
                                       host.location(), isIngress);
        }

        private Path buildWholePath(EdgeLink srcLink, EdgeLink dstLink, Path linkPath) {
            if (linkPath == null && !(srcLink.dst().deviceId().equals(dstLink.src().deviceId()))) {
                return null;
            }

            return buildEdgeToEdgePath(srcLink, dstLink, linkPath);
        }

        /**
         * Produces a direct edge-to-edge path.
         *
         * @param srcLink
         * @param dstLink
         * @param linkPath
         * @return
         */
        private Path buildEdgeToEdgePath(EdgeLink srcLink, EdgeLink dstLink, Path linkPath) {

            List<Link> links = Lists.newArrayListWithCapacity(2);

            double cost = 0;

            // now, the cost of edge link is 0.
            links.add(srcLink);

            if (linkPath != null) {
                links.addAll(linkPath.links());
                cost += linkPath.cost();
            }

            links.add(dstLink);

            return new DefaultPath(providerId, links, cost);
        }

        //=================== The End =====================

    }

        private class BandwidthLinkWeight implements LinkWeight{
            private final double LINK_WEIGHT_DOWN = 100.0;
            private final double LINK_WEIGHT_FULL = 100.0;

            @Override
            public double weight(TopologyEdge topologyEdge) {

                if (topologyEdge.link().state() == Link.State.INACTIVE) {
                   return LINK_WEIGHT_DOWN;
                }
                long linkWireSpeed = getLinkWireSpeed(topologyEdge.link());
                long linkLoadBandWidth = getLinkLoadSpeed(topologyEdge.link());

                return linkLoadBandWidth / linkWireSpeed * 100;

            }

            private long getLinkWireSpeed(Link link) {
                long src = getPortWireSpeed(link.src());
                long dst = getPortWireSpeed(link.dst());

                return Math.min(src, dst);
            }

            private long getLinkLoadSpeed(Link link) {
                long src = getPortLoadSpeed(link.src());
                long dst = getPortLoadSpeed(link.dst());

                return Math.max(src, dst);
            }

            private long getPortWireSpeed(ConnectPoint point) {
                return deviceService.getPort(point.deviceId(), point.port()).portSpeed() * 1000000;
            }

            private long getPortLoadSpeed(ConnectPoint point) {
                return portStatisticsService.load(point).rate() * 8;
            }
        }
    }
