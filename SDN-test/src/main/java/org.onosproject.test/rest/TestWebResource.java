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
package org.onosproject.test.rest;



import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.rest.AbstractWebResource;
import org.onosproject.test.impl.ControlItem;
import org.onosproject.test.intf.TransportConService;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import java.util.HashSet;
import java.util.Set;


/**
 * Skeletal ONOS application component.
 */
@Path("test")
public class TestWebResource extends AbstractWebResource {

      private  final FlowObjectiveService flowObjectiveService = get(FlowObjectiveService.class);
    private final FlowRuleService flowRuleService = get(FlowRuleService.class);
    private  final  TransportConService service = get(TransportConService.class);
    private Set<ControlItem> controlItems = new HashSet<ControlItem>();

    @GET
    @Path("hello")
    public Response hello() {
        ObjectNode root = mapper().createObjectNode();
        root.put("Hello", 1);

        return ok(root).build();
    }

    @GET
    @Path("ControlList")
    public Response controlList() {
        controlItems = service.getControlItem();
        ObjectNode root = mapper().createObjectNode();
        ArrayNode arrayNode = root.putArray("ControlItem");
        for (ControlItem controlItem:controlItems) {
            arrayNode.add(mapper().createObjectNode()
                                  .put(controlItem.getSrcIp(), "srcIp")
                                  .put(controlItem.getDstIp(), "dstIp")
                                  .put(controlItem.getProtocol(), "protocol")
                                  .put(Integer.toString(controlItem.getPort()), "port"));

        }
        return ok(root).build();
    }

    @GET
    @Path("add/{srcip}/{dstip}/{port}/{protocol}")
    public Response addflows(@PathParam("srcip") String srcIp,
                             @PathParam("dstip") String dstIp,
                             @PathParam("port") int port,
                             @PathParam("protocol") String protocol) {
        ObjectNode root = mapper().createObjectNode()
                .put("srcIp", srcIp)
                .put("dstIp", dstIp)
                .put("port", port)
                .put("protocol", protocol);
       // ArrayNode arrayNode = root.putArray("Add");

        service.addBlocklist(srcIp, dstIp, protocol, port);


        root.put("success", true);

//        Byte proto;
//        if (protocol == "TCP") {
//            proto = IPv4.PROTOCOL_TCP;
//        } else {
//            proto = IPv4.PROTOCOL_UDP;
//        }
//        final Iterable<Device> devices = get(DeviceService.class).getDevices();
//        for (final Device device : devices) {
//
//            TrafficSelector selector = DefaultTrafficSelector.builder()
//                    .matchTcpDst(TpPort.tpPort(port))
//                    .matchIPProtocol(proto)
//                    .matchIPSrc(Ip4Prefix.valueOf(IPv4.toIPv4Address(srcIp), Ip4Prefix.MAX_MASK_LENGTH))
//                    .build();
//
//            TrafficTreatment treatment = DefaultTrafficTreatment.builder().build();
//
//            ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
//                    .withSelector(selector)
//                    .withTreatment(treatment)
//                    .withPriority(10)
//                    .withFlag(ForwardingObjective.Flag.VERSATILE)
//                    .makePermanent()
//                    .add();
//
//            flowObjectiveService.forward(device.id(),
//                            forwardingObjective);
//
//
//        }

        return ok(root).build();
    }


    @GET
    @Path("delete/{srcip}/{dstip}/{port}/{protocol}")
    public Response deleteflows(@PathParam("srcip") String srcIp,
                                @PathParam("dstip") String dstIp,
                             @PathParam("port") int port,
                             @PathParam("protocol") String protocol) {
        ObjectNode root = mapper().createObjectNode()
                .put("srcIp", srcIp)
                .put("dstIp", dstIp)
                .put("port", port)
                .put("protocol", protocol);

        service.deleteBlocklist(srcIp, dstIp, protocol, port);

        root.put("success", true);
//        ArrayNode arrayNode = root.putArray("delete");
//        Byte proto;
//        if (protocol == "TCP") {
//            proto = IPv4.PROTOCOL_TCP;
//        } else {
//            proto = IPv4.PROTOCOL_UDP;
//        }
//        Ip4Prefix srcIP = Ip4Prefix.valueOf(IPv4.toIPv4Address(srcIp), Ip4Prefix.MAX_MASK_LENGTH);
//
//        final Iterable<Device> devices = get(DeviceService.class).getDevices();
//        for (final Device device : devices) {
//            final Iterable<FlowEntry> flowEntries = flowRuleService.getFlowEntries(device.id());
//                for (final FlowEntry entry : flowEntries) {
//                   boolean matchip = false, matchport = false, matchproto = false;
//                    for (final Criterion criteria:entry.selector().criteria()) {
//                        if (criteria.type() == Criterion.Type.IPV4_SRC) {
//                            if (((IPCriterion) criteria).ip().equals(srcIP)) {
//                                matchip = true;
//                            }
//                        }
//                        if (criteria.type() == Criterion.Type.TCP_DST) {
//                            if (((TcpPortCriterion) criteria).tcpPort().equals(TpPort.tpPort(port))) {
//                                matchport = true;
//                            }
//                        }
//                        if (criteria.type() == Criterion.Type.IP_PROTO) {
//                            if (((IPProtocolCriterion) criteria).protocol() == proto) {
//                                matchproto = true;
//                            }
//                        }
//                    }
//                    if (matchip && matchport && matchproto) {
//                        flowRuleService.removeFlowRules(entry);
//                    }
//                }
//
//        }


        return ok(root).build();
    }

}
