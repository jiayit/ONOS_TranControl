/*
 * Copyright 2017-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.test.impl;


import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onlab.packet.TCP;
import org.onlab.packet.TpPort;
import org.onlab.packet.UDP;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.Device;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.topology.TopologyService;
import org.onosproject.test.intf.TransportConService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
@Service
public class TransportConManager implements TransportConService {

    private static final int DEFAULT_PRIORITY = 10;

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;

    private ApplicationId appId;

    private ReactivePacketprocessor packetProcessor;

    private int flowPriority = DEFAULT_PRIORITY;

    private Set<ControlItem> controlItems = new HashSet<ControlItem>();

    @Activate
    public void activate() {

        appId = coreService.registerApplication("SDN-test");
        packetProcessor = new ReactivePacketprocessor();
        packetService.addProcessor(packetProcessor, PacketProcessor.director(2));
        requestIntercepts();
        log.info("Started");
    }

    @Deactivate
    public void deactivate() {
        withdrawIntercepts();
        packetService.removeProcessor(packetProcessor);
        flowRuleService.removeFlowRulesById(appId);
        log.info("Stopped");
    }

    /**
     * Entry of add blocklist function.
     *
     * @param srcip
     * @param dstip
     * @param protocol
     * @param port
     * @return true if succeed
     */

    @Override
    //Add specific flow matching srcip, dstip, protocol and port
    public boolean addBlocklist(String srcip, String dstip, String protocol, int port) {
        controlItems.add(new ControlItem(srcip, dstip, protocol, port));
        Ip4Address srcIp = Ip4Address.valueOf(srcip);
        Ip4Address dstIp = Ip4Address.valueOf(dstip);

        TrafficSelector.Builder selectorBuidler = DefaultTrafficSelector.builder();
        selectorBuidler.matchEthType(Ethernet.TYPE_IPV4)
                .matchIPSrc(Ip4Prefix.valueOf(srcIp, Ip4Prefix.MAX_MASK_LENGTH))
                .matchIPDst(Ip4Prefix.valueOf(dstIp, Ip4Prefix.MAX_MASK_LENGTH));

        if (protocol == "TCP") {
            selectorBuidler.matchIPProtocol(IPv4.PROTOCOL_TCP)
                    .matchTcpSrc(TpPort.tpPort(port));
        }
        if (protocol == "UDP") {
            selectorBuidler.matchIPProtocol(IPv4.PROTOCOL_UDP)
                    .matchTcpSrc(TpPort.tpPort(port));
        }

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .drop()
                .build();

        ForwardingObjective.Builder fowardingObjectiveBuilder = DefaultForwardingObjective.builder()
                .makePermanent()
                .withPriority(flowPriority)
                .withSelector(selectorBuidler.build())
                .withTreatment(treatment)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId);

        final Iterable<Device> devices = deviceService.getDevices();
        for (Device device : devices) {
            flowObjectiveService.forward(device.id(), fowardingObjectiveBuilder.add());
        }

        return true;
    }

    /**
     * Entry of add blocklist function.
     *
     * @param srcip
     * @param dstip
     * @param protocol
     * @param port
     * @return true if succeed
     */

    @Override
    //delete specific flow matching srcip, dstip, protocol and port
    public boolean deleteBlocklist(String srcip, String dstip, String protocol, int port) {
        controlItems.remove(new ControlItem(srcip, dstip, protocol, port));
        Ip4Address srcIp = Ip4Address.valueOf(srcip);
        Ip4Address dstIp = Ip4Address.valueOf(dstip);

        TrafficSelector.Builder selectorBuidler = DefaultTrafficSelector.builder();
        selectorBuidler.matchEthType(Ethernet.TYPE_IPV4)
                .matchIPSrc(Ip4Prefix.valueOf(srcIp, Ip4Prefix.MAX_MASK_LENGTH))
                .matchIPDst(Ip4Prefix.valueOf(dstIp, Ip4Prefix.MAX_MASK_LENGTH));

        if (protocol == "TCP") {
            selectorBuidler.matchIPProtocol(IPv4.PROTOCOL_TCP)
                    .matchTcpSrc(TpPort.tpPort(port));
        }
        if (protocol == "UDP") {
            selectorBuidler.matchIPProtocol(IPv4.PROTOCOL_UDP)
                    .matchTcpSrc(TpPort.tpPort(port));
        }

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .drop()
                .build();

        ForwardingObjective.Builder fowardingObjectiveBuilder = DefaultForwardingObjective.builder()
                .makePermanent()
                .withPriority(flowPriority)
                .withSelector(selectorBuidler.build())
                .withTreatment(treatment)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId);

        final Iterable<Device> devices = deviceService.getDevices();
        for (Device device : devices) {
            flowObjectiveService.forward(device.id(), fowardingObjectiveBuilder.remove());
        }


//        byte proto = IPv4.PROTOCOL_TCP;
//        Ip4Address srcIp = Ip4Address.valueOf(srcip);
//        Ip4Address dstIp = Ip4Address.valueOf(dstip);
//        if (protocol.equals("UDP")) {
//            proto = IPv4.PROTOCOL_UDP;
//        }
//
//        final Iterable<Device> devices = deviceService.getDevices();
//        for (Device device:devices) {
//            final Iterable<FlowEntry> flowEntries = flowRuleService.getFlowEntries(device.id());
//            for (FlowEntry flowEntry:flowEntries) {
//                boolean matchSrcIp = false, matchpro = false,
//                        matchport = false, matchDstIp = false;
//                for (Criterion criteria:flowEntry.selector().criteria()) {
//                    if (criteria.type() == Criterion.Type.IPV4_SRC) {
//                        if (((IPCriterion) criteria).ip().equals(srcIp)) {
//                            matchSrcIp = true;
//                        }
//
//                    }
//                    if (criteria.type() == Criterion.Type.IPV4_DST) {
//                        if (((IPCriterion) criteria).ip().equals(dstIp)) {
//                            matchDstIp = true;
//                        }
//
//                    }
//                    if (criteria.type() == Criterion.Type.IP_PROTO) {
//                        if ((byte) (((IPProtocolCriterion) criteria).protocol()) == proto) {
//                            matchpro = true;
//                        }
//
//                    }
//                    if (criteria.type() == Criterion.Type.TCP_DST) {
//                        if (((TcpPortCriterion) criteria).tcpPort().equals(TpPort.tpPort(port))) {
//                            matchport = true;
//                        }
//
//                    }
//                }
//                if (matchDstIp && matchport  && matchSrcIp) {
//                    flowRuleService.removeFlowRules((FlowRule) flowEntry);
//                }
//
//            }
//        }

        return true;
    }

    @Override
    public Set<ControlItem> getControlItem() {
        return controlItems;
    }

    // block the packet that satisfied the rule
    private class ReactivePacketprocessor implements PacketProcessor {
        public void process(PacketContext context) {
            if (context.isHandled()) {
                return;
            }
            InboundPacket pkt = context.inPacket();
            Ethernet ethpkt = pkt.parsed();

            if (ethpkt == null) {
                return;
            }

            if (ethpkt.getEtherType() != Ethernet.TYPE_IPV4) {
                return;
            }

            IPv4 packet = (IPv4) ethpkt.getPayload();
            String srcIp = Integer.toString(packet.getSourceAddress());
            String dstIp = Integer.toString(packet.getDestinationAddress());

            if (packet.getProtocol() == IPv4.PROTOCOL_TCP) {
                TCP tcpPacket = (TCP) packet.getPayload();
                if (controlItems.contains(new ControlItem(srcIp, dstIp, "TCP", tcpPacket.getDestinationPort()))) {
                    context.block();
                }
                // addBlocklist(srcIp, dstIp, "TCP", tcpPacket.getDestinationPort());

            }

            if (packet.getProtocol() == IPv4.PROTOCOL_UDP) {
                UDP udpPacket = (UDP) packet.getPayload();
                if (controlItems.contains(new ControlItem(srcIp, dstIp, "UDP", udpPacket.getDestinationPort()))) {
                    context.block();
                }
                // addBlocklist(srcIp, dstIp, "UDP", tcpPacket.getDestinationPort());

            }

            // Otherwise forward and be done with it.
            // installRule(context, path.src().port());

        }
    }

    /**
     * Request packet in via packet service.
     */
    private void requestIntercepts() {
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        selectorBuilder.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selectorBuilder.build(), PacketPriority.REACTIVE, appId);

    }

    /**
     * Cancel request for packet in via packet service.
     */
    private void withdrawIntercepts() {
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        selectorBuilder.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selectorBuilder.build(), PacketPriority.REACTIVE, appId);
    }


    // Install a rule forwarding the packet to the specified port.
//    private void installRule(PacketContext context, PortNumber portNumber) {
//
//        Ethernet inPkt = context.inPacket().parsed();
//
//        if (inPkt.getEtherType() == Ethernet.TYPE_IPV4) {
//            IPv4 ipv4 = (IPv4) inPkt.getPayload();
//            if (ipv4.getDestinationAddress() == IPv4.toIPv4Address("10.0.0.3")) {
//
//                TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
//                selectorBuilder.matchIPDst(Ip4Prefix.valueOf(ipv4.getDestinationAddress(),
//                                                             Ip4Prefix.MAX_MASK_LENGTH));
//
//                TrafficTreatment treatment = DefaultTrafficTreatment.builder().build();
//
//                ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
//                        .withSelector(selectorBuilder.build())
//                        .withTreatment(treatment)
//                        .withFlag(ForwardingObjective.Flag.VERSATILE)
//                        .withPriority(10)
//                        .fromApp(appId)
//                        .add();
//
//                flowObjectiveService.forward(context.inPacket().receivedFrom().deviceId(), forwardingObjective);
//            } else  {
//                TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
//                selectorBuilder.matchEthDst(inPkt.getSourceMAC())
//                        .matchEthSrc(inPkt.getDestinationMAC());
//
//
//                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
//                        .setOutput(portNumber)
//                        .build();
//
//                ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
//                        .withSelector(selectorBuilder.build())
//                        .withTreatment(treatment)
//                        .withPriority(40000)
//                        .withFlag(Forwarding
}