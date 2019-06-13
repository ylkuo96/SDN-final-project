/*
 * Copyright 2019-present Open Networking Foundation
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
package nctu.winlab.project7_0413335;

import com.google.common.collect.ImmutableSet;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.packet.Ethernet;
import org.onlab.packet.ICMP;
import org.onlab.packet.ICMP6;
import org.onlab.packet.IPv4;
import org.onlab.packet.IPv6;
import org.onlab.packet.Ip4Prefix;
import org.onlab.packet.Ip6Prefix;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TCP;
import org.onlab.packet.TpPort;
import org.onlab.packet.UDP;
import org.onlab.packet.VlanId;
import org.onlab.util.KryoNamespace;
import org.onlab.util.Tools;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.event.Event;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.Link;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.Path;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.EthCriterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instructions;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.host.HostService;
//import org.onosproject.net.host.InterfaceIpAddress;
import org.onosproject.net.link.LinkEvent;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.topology.TopologyEvent;
import org.onosproject.net.topology.TopologyListener;
import org.onosproject.net.topology.TopologyService;
import org.onosproject.store.service.EventuallyConsistentMap;
import org.onosproject.store.service.MultiValuedTimestamp;
import org.onosproject.store.service.StorageService;
import org.onosproject.store.service.WallClockTimestamp;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.ArrayList;

// project 7 
import org.onlab.packet.Ip4Address;
import org.onlab.packet.IpAddress;
import org.onlab.packet.ARP;
import org.onosproject.net.packet.DefaultOutboundPacket;
import java.nio.ByteBuffer;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {
	
	// for debug
    private final Logger log = LoggerFactory.getLogger(getClass());
	
	// set timeout & priority
	private static final int DEFAULT_TIMEOUT = 10;
	private static final int DEFAULT_PRIORITY = 10;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected LinkService linkService;
    
	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;
	
	private LearningSwitchProcessor processor = new LearningSwitchProcessor();

    private ApplicationId appId;	

    @Property(name = "flowTimeout", intValue = DEFAULT_TIMEOUT,
            label = "Configure Flow Timeout for installed flow rules; " +
                    "default is 10 sec")
    private int flowTimeout = DEFAULT_TIMEOUT;

    @Property(name = "flowPriority", intValue = DEFAULT_PRIORITY,
            label = "Configure Flow Priority for installed flow rules; " +
                    "default is 10")
    private int flowPriority = DEFAULT_PRIORITY;
	
	private IPtoMacTable ARPtable;
	
    @Activate
    protected void activate() {
		appId = coreService.registerApplication("nctu.winlab.project7_0413335");
        packetService.addProcessor(processor, PacketProcessor.director(2));
        requestIntercepts();
		ARPtable = new IPtoMacTable();
        log.info("Started", appId.id());		
    }

    @Deactivate
    protected void deactivate() {
		withdrawIntercepts();
        flowRuleService.removeFlowRulesById(appId);
		packetService.removeProcessor(processor);
        processor = null;
		ARPtable = null;
        log.info("Stopped");
    }

    private void requestIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
		
		// tell controller that ipv4 packets need to do packet-in action
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }	

    private void withdrawIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
		
		// tell controller that ipv4 packets dont need to do packet-in anymore
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }	
	
    private class LearningSwitchProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            if (context.isHandled()) {
                return;
            }

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt == null) {
                return;
            }

			// do not process control packets, like LLDP and BDDP packets
            if (isControlPacket(ethPkt)) {
				log.info("LLDP or BDDP packets are dropped");
                return;
            }
		
			// only need to process arp packets
			if(ethPkt.getEtherType() != Ethernet.TYPE_ARP){
				return;
			}

            HostId id = HostId.hostId(ethPkt.getDestinationMAC());

            // Do not process LLDP MAC address in any way.
            if (id.mac().isLldp()) {
                return;
            }
			
			DeviceId switchId = pkt.receivedFrom().deviceId();
			MacAddress macAddress = ethPkt.getSourceMAC();
			PortNumber inPort = pkt.receivedFrom().port();

			HostId srcid = HostId.hostId(ethPkt.getSourceMAC());
			Host srcHost = hostService.getHost(srcid);
			while(srcHost == null){
				srcHost = hostService.getHost(srcid);
			}
			Set<IpAddress> ips = srcHost.ipAddresses();
			
			IpAddress ip = (IpAddress) ips.toArray()[0];
			
			// ARP request & ARP reply
			ARP arpPkt = (ARP) ethPkt.getPayload();
			IpAddress dstip = Ip4Address.valueOf(arpPkt.getTargetProtocolAddress());
			if(arpPkt.getOpCode() == ARP.OP_REQUEST){
				if(ARPtable.getMAC(ip) == null){
					ARPtable.addRecord(switchId, ip, macAddress, inPort);
				}
				
				if(ARPtable.getMAC(dstip) == null){
					// flood
					flood(context);
				}
				else{
					// build arp reply packet
					MacAddress dstMac = ARPtable.getMAC(dstip);
					Ethernet arpReply = ARP.buildArpReply(dstip.getIp4Address(), dstMac, ethPkt);	
				
					PortNumber toPort = inPort;
					DeviceId toId = switchId;
					ByteBuffer buffer =	ByteBuffer.wrap(arpReply.serialize());
					
					// directly sent back
					TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(toPort).build();
					packetService.emit(new DefaultOutboundPacket(
						toId,
						treatment,
						buffer
					));

					log.info("send arp reply to " + toId + ", " + toPort);
				}
			}
			else if(arpPkt.getOpCode() == ARP.OP_REPLY){
				if(ARPtable.getMAC(ip) == null){
					ARPtable.addRecord(switchId, ip, macAddress, inPort);
				}

				// build arp reply packet
				Ethernet arpReply = ethPkt;

				PortNumber toPort = ARPtable.getPORT(dstip);
				DeviceId toId = ARPtable.getId(dstip);
				ByteBuffer buffer =	ByteBuffer.wrap(arpReply.serialize());

				// packet out
				TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(toPort).build();
				packetService.emit(new DefaultOutboundPacket(
					toId,
					treatment,
					buffer
				));
					
				log.info("send arp reply to " + toId + ", " + toPort);
			}
        }

    }
	
    // Indicates whether this is a control packet, e.g. LLDP, BDDP
    private boolean isControlPacket(Ethernet eth) {
        short type = eth.getEtherType();
        return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN;
    }	
	
    // Floods the specified packet if permissible.
    private void flood(PacketContext context) {
        if (topologyService.isBroadcastPoint(topologyService.currentTopology(),
                                             context.inPacket().receivedFrom())) {
            packetOut(context, PortNumber.FLOOD);
        } else {
            context.block();
        }
    }

    // Sends a packet out the specified port.
    private void packetOut(PacketContext context, PortNumber portNumber) {
        context.treatmentBuilder().setOutput(portNumber);
        context.send();
    }	
	
	private class IPtoMacTable {
		private class pair {
			DeviceId switchId;
			IpAddress ip;
			MacAddress mac;
			PortNumber port;
			
			private pair(DeviceId switchId, IpAddress ip, MacAddress mac, PortNumber port) {
				this.switchId = switchId;
				this.ip = ip;
				this.mac = mac;
				this.port = port;
			}
		}
		
		ArrayList<pair> table = new ArrayList<pair>();
		
		// add record to table
		private boolean addRecord(DeviceId switchId, IpAddress ip, MacAddress mac, PortNumber port) {
			this.table.add(new pair(switchId, ip, mac, port));
			return true;
		}
		
		private MacAddress getMAC(IpAddress ip) {
			for(int i = 0; i < this.table.size(); i ++){
				// record exist
				if(this.table.get(i).ip.equals(ip)){
					return this.table.get(i).mac;
				}
			}
			
			return null;
		}
		
		private DeviceId getId(IpAddress ip) {
			for(int i = 0; i < this.table.size(); i ++){
				if(this.table.get(i).ip.equals(ip)){
					return this.table.get(i).switchId;
				}
			}
			return null;
		}
		
		private PortNumber getPORT(IpAddress ip) {
			for(int i = 0; i < this.table.size(); i ++){
				if(this.table.get(i).ip.equals(ip)){
					return this.table.get(i).port;
				}
			}
			return null;
		}
	}
}

