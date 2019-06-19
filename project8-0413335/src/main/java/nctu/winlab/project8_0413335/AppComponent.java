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
package nctu.winlab.project8_0413335;

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
import org.onlab.packet.IpPrefix;
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
import org.onosproject.net.link.LinkEvent;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.topology.Topology;
import org.onosproject.net.topology.TopologyEdge;
import org.onosproject.net.topology.TopologyVertex;
import org.onosproject.net.topology.TopologyGraph;
import org.onosproject.net.topology.TopologyEvent;
import org.onosproject.net.topology.TopologyListener;
import org.onosproject.net.topology.TopologyService;
import org.onosproject.store.service.EventuallyConsistentMap;
import org.onosproject.store.service.MultiValuedTimestamp;
import org.onosproject.store.service.StorageService;
import org.onosproject.store.service.WallClockTimestamp;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;

import org.onlab.packet.IpAddress;

import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import org.slf4j.LoggerFactory;

import org.onlab.packet.VlanId;
import org.onlab.packet.EthType;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {
	
	// for debug
    private final Logger log = LoggerFactory.getLogger(getClass());
	
	// set timeout & priority
	private static final int DEFAULT_TIMEOUT = 10;
	// priority should under arp
	private static final int DEFAULT_PRIORITY = 39998;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;
	
	// the class written bymyself
	private LearningSwitchProcessor processor = new LearningSwitchProcessor();

    private ApplicationId appId;	

    @Property(name = "flowTimeout", intValue = DEFAULT_TIMEOUT,
            label = "Configure Flow Timeout for installed flow rules; " +
                    "default is 60 sec")
    private int flowTimeout = DEFAULT_TIMEOUT;

    @Property(name = "flowPriority", intValue = DEFAULT_PRIORITY,
            label = "Configure Flow Priority for installed flow rules; " +
                    "default is 10")
    private int flowPriority = DEFAULT_PRIORITY;

	private final TopologyListener topologyListener = new InternalTopologyListener();

	private static ArrayList<Integer> shortestPath = new ArrayList<Integer>();

	/* --- */
	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected NetworkConfigRegistry cfgService;

	private final InternalConfigListener cfgListener = new InternalConfigListener();

	private final Set<ConfigFactory> factories = ImmutableSet.of(new ConfigFactory<ApplicationId, nctu.winlab.project8_0413335.MyConfig>(APP_SUBJECT_FACTORY, 
	nctu.winlab.project8_0413335.MyConfig.class, 
	"myconfiG"){
		@Override
		public nctu.winlab.project8_0413335.MyConfig createConfig(){
			return new nctu.winlab.project8_0413335.MyConfig();
			}
		}
	);

	// for config
	private static String s1segID = "default";
	private static String s2segID = "default";
	private static String s3segID = "default";
	private static String s2subNet = "default";
	private static String s3subNet = "default";
	private static String H1 = "default";
	private static String H2 = "default";
	private static String H3 = "default";
	private static String H4 = "default";
	private static String H5 = "default";
	/* --- */
	
    @Activate
    protected void activate() {
		appId = coreService.registerApplication("nctu.winlab.project8_0413335");

		/* --- */
		cfgService.addListener(cfgListener);
		factories.forEach(cfgService::registerConfigFactory);
		cfgListener.reconfigureNetwork(cfgService.getConfig(appId, nctu.winlab.project8_0413335.MyConfig.class));
		/* --- */

        //packetService.addProcessor(processor, PacketProcessor.director(2));
		topologyService.addListener(topologyListener);
        //requestIntercepts();
        log.info("Started", appId.id());		
    }

    @Deactivate
    protected void deactivate() {
		/* --- */
		cfgService.removeListener(cfgListener);
		factories.forEach(cfgService::unregisterConfigFactory);
		/* --- */

        flowRuleService.removeFlowRulesById(appId);
		//packetService.removeProcessor(processor);
		topologyService.removeListener(topologyListener);
		//withdrawIntercepts();
        processor = null;
        log.info("Stopped");
    }

	/* --- */
	private class InternalConfigListener implements NetworkConfigListener {
		private void reconfigureNetwork(nctu.winlab.project8_0413335.MyConfig cfg){
			if(cfg == null){
				return;
			}
			if(cfg.s1segid() != null){
				s1segID = cfg.s1segid();
			}
			if(cfg.s2segid() != null){
				s2segID = cfg.s2segid();
			}
			if(cfg.s3segid() != null){
				s3segID = cfg.s3segid();
			}
			if(cfg.s2subnet() != null){
				s2subNet = cfg.s2subnet();
			}
			if(cfg.s3subnet() != null){
				s3subNet = cfg.s3subnet();
			}
			if(cfg.h1() != null){
				H1 = cfg.h1();
			}
			if(cfg.h2() != null){
				H2 = cfg.h2();
			}
			if(cfg.h3() != null){
				H3 = cfg.h3();
			}
			if(cfg.h4() != null){
				H4 = cfg.h4();
			}
			if(cfg.h5() != null){
				H5 = cfg.h5();
			}
		}
			
		@Override
		public void event(NetworkConfigEvent event){
		// while configuration is uploaded, update the variable "myName".
			if((event.type() == NetworkConfigEvent.Type.CONFIG_ADDED ||
				event.type() == NetworkConfigEvent.Type.CONFIG_UPDATED) &&
				event.configClass().equals(nctu.winlab.project8_0413335.MyConfig.class)){

				nctu.winlab.project8_0413335.MyConfig cfg = cfgService.getConfig(appId, nctu.winlab.project8_0413335.MyConfig.class);
				reconfigureNetwork(cfg);
				/*
				log.info("(Re)configured, new s1 segment ID is {}", s1segID); 
				log.info("(Re)configured, new s2 segment ID is {}", s2segID); 
				log.info("(Re)configured, new s3 segment ID is {}", s3segID); 
				log.info("(Re)configured, new s2 subnet is {}", s2subNet); 
				log.info("(Re)configured, new s3 subnet is {}", s3subNet); 
				log.info("(Re)configured, new Host1 is switch/port/mac: {}", H1); 
				log.info("(Re)configured, new Host2 is switch/port/mac: {}", H2); 
				log.info("(Re)configured, new Host3 is switch/port/mac: {}", H3); 
				log.info("(Re)configured, new Host4 is switch/port/mac: {}", H4); 
				log.info("(Re)configured, new Host5 is switch/port/mac: {}", H5);
				*/

				/* install flow rules */
				// intra packets
				installR(PortNumber.portNumber(H1.split("/")[1]), DeviceId.deviceId(H1.split("/")[0]), MacAddress.valueOf(H1.split("/")[2]));
				installR(PortNumber.portNumber(H2.split("/")[1]), DeviceId.deviceId(H2.split("/")[0]), MacAddress.valueOf(H2.split("/")[2]));
				installR(PortNumber.portNumber(H3.split("/")[1]), DeviceId.deviceId(H3.split("/")[0]), MacAddress.valueOf(H3.split("/")[2]));
				installR(PortNumber.portNumber(H4.split("/")[1]), DeviceId.deviceId(H4.split("/")[0]), MacAddress.valueOf(H4.split("/")[2]));
				installR(PortNumber.portNumber(H5.split("/")[1]), DeviceId.deviceId(H5.split("/")[0]), MacAddress.valueOf(H5.split("/")[2]));

				// segment routing
				DeviceId s1did = DeviceId.deviceId("of:0000000000000001");
				DeviceId s2did = DeviceId.deviceId("of:0000000000000002");
				DeviceId s3did = DeviceId.deviceId("of:0000000000000003");
				
				IpPrefix s3ip = IpPrefix.valueOf(s3subNet);
				IpPrefix s2ip = IpPrefix.valueOf(s2subNet);
				
				PortNumber s2tos1port = PortNumber.portNumber("1");
				PortNumber s1tos3port = PortNumber.portNumber("2");
				PortNumber s3tos1port = PortNumber.portNumber("1");
				PortNumber s1tos2port = PortNumber.portNumber("1");
				
				VlanId s3vid = VlanId.vlanId(s3segID);
				VlanId s2vid = VlanId.vlanId(s2segID);

				// for s2
				installSegRuleSrc(s2did, s3ip, s3vid, s2tos1port);
				installSegRuleDst(s2did, s2vid);

				// for s1
				installSegRuleMid(s1tos3port, s1did, s3vid);
				installSegRuleMid(s1tos2port, s1did, s2vid);

				// for s3
				installSegRuleSrc(s3did, s2ip, s2vid, s3tos1port);
				installSegRuleDst(s3did, s3vid);
			}
		}
	}
	/* --- */


    /**
     * Request packet in via packet service.
     */
    private void requestIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }	

    /**
     * Cancel request for packet in via packet service.
     */
    private void withdrawIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }	
	
	/**
     * Packet processor responsible for forwarding packets along their paths.
     */
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

            if (isControlPacket(ethPkt)) {
                return;
            }

			if(ethPkt.getEtherType() == Ethernet.TYPE_ARP){
				return;
			}

			if(ethPkt.getDestinationMAC().equals(MacAddress.BROADCAST)){
				return;
			}

            HostId dstid = HostId.hostId(ethPkt.getDestinationMAC());
			Host dst = hostService.getHost(dstid);
			while(dst == null){
				dst = hostService.getHost(dstid);
			}

            // Do not process LLDP MAC address in any way.
            if (dstid.mac().isLldp()) {
                return;
            }
			
			MacAddress dstAddress = ethPkt.getDestinationMAC();		
			DeviceId switchId = pkt.receivedFrom().deviceId();
			MacAddress macAddress = ethPkt.getSourceMAC();
			PortNumber inPort = pkt.receivedFrom().port();

			//log.info("packet-in from: " + switchId);			

			if(switchId.equals(dst.location().deviceId())){
				//installRule(context, dst.location().port(), switchId, dst);
				log.info("install rule for: " + switchId);
				return;
			}

			// get graph
			TopologyGraph graph = topologyService.getGraph(topologyService.currentTopology());
			Set<TopologyVertex> V_Set = graph.getVertexes();
			Set<TopologyEdge> E_Set = graph.getEdges();

			int srcIdx = -1;
			int dstIdx = -1;
			for(int i = 0; i < V_Set.size(); i ++){
				TopologyVertex tmpV = (TopologyVertex)V_Set.toArray()[i];
				if(tmpV.deviceId().equals(dst.location().deviceId())){
					dstIdx = i;
				}

				if(tmpV.deviceId().equals(switchId)){ // src deviceId
					srcIdx = i;
				}

				if(srcIdx != -1 && dstIdx != -1){
					break;
				}
			}
	
			// BFS
			BFS(graph, V_Set, E_Set, srcIdx, dstIdx);

			// install rules from dst to src
			PortNumber port = inPort;
			for(int i = shortestPath.size() - 1; i >= 0; i --){
				int idx = shortestPath.get(i);
				TopologyVertex tmp = (TopologyVertex)V_Set.toArray()[idx];
				
				if(i == shortestPath.size() - 1){
					// get from host dst
					port = dst.location().port();
				}
				else{
					// get from edge info
					for(int j = 0; j < E_Set.size(); j ++){
						TopologyEdge tmpE = (TopologyEdge)E_Set.toArray()[j];
						int nidx = shortestPath.get(i + 1);
						TopologyVertex ntmp = (TopologyVertex)V_Set.toArray()[nidx];
						if(tmpE.link().src().deviceId().equals(tmp.deviceId())){
							if(tmpE.link().dst().deviceId().equals(ntmp.deviceId())){
								port = tmpE.link().src().port();
								break;
							}
						}
						
						if(tmpE.link().dst().deviceId().equals(tmp.deviceId())){
							if(tmpE.link().src().deviceId().equals(ntmp.deviceId())){
								port = tmpE.link().dst().port();
								break;
							}
						}
					}
				}
				
				log.info("install rule for: " + tmp.deviceId());
				//installRule(context, port, tmp.deviceId(), dst);
			}
        }

    }
	
	public ArrayList<Integer> BFS(TopologyGraph graph, Set<TopologyVertex> V_Set, Set<TopologyEdge> E_Set, int srcIdx, int dstIdx){
		shortestPath.clear();

		ArrayList<Integer> path = new ArrayList<Integer>();
		
		Queue<Integer> q = new LinkedList<Integer>();
		Queue<Integer> visited = new LinkedList<Integer>();
		
		q.offer(srcIdx);
		while(!q.isEmpty()){
			int v = q.poll();
			visited.offer(v);	

			// get neighbors of v
			TopologyVertex tmpV = (TopologyVertex)V_Set.toArray()[v];
			for(int i = 0; i < E_Set.size(); i ++){
				TopologyEdge tmpE = (TopologyEdge)E_Set.toArray()[i];
				if(!tmpE.link().src().deviceId().equals(tmpV.deviceId())){
					continue;
				}

				int neighbor = -1;
				for(int j = 0; j < V_Set.size(); j ++){
					TopologyVertex tmpV2 = (TopologyVertex)V_Set.toArray()[j];
					if(tmpE.link().dst().deviceId().equals(tmpV2.deviceId())){
						neighbor = j;
						break;
					}	
				}
				path.add(neighbor);
				path.add(v);

				if(neighbor == dstIdx){
					return ProcessPath(srcIdx, dstIdx, path);
				}
				else{
					if(!visited.contains(neighbor)){
						q.offer(neighbor);
					}	
				}
			}
		}

		return null;
	}

	private static ArrayList<Integer> ProcessPath(int srcIdx, int dstIdx, ArrayList<Integer> path){
		// find out where the dst node directly comes from
		int i = path.indexOf(dstIdx);
		int source = path.get(i + 1);

		// adds the destination node to the shortest path
		shortestPath.add(0, dstIdx);

		if(source == srcIdx){
			shortestPath.add(0, srcIdx);
			return shortestPath;
		}
		else{
			// set the source to be the new destination
			// then recursively find the source until source == srcIdx
			return ProcessPath(srcIdx, source, path);
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
	
    private void installR(PortNumber portNumber, DeviceId did, MacAddress dstMac) {
		// flow rule selector
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
		selectorBuilder.matchEthDst(dstMac);

		// flow rule builder
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(portNumber)
                .build();

		// construct flow rule
        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(treatment)
                .withPriority(39997)
                //.withPriority(flowPriority)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
				.makePermanent()
                //.makeTemporary(flowTimeout)
                .add();

		// flow modify
        flowObjectiveService.forward(did, forwardingObjective);
    }	
	
	// for segment routing
    private void installSegRuleMid(PortNumber portNumber, DeviceId did, VlanId vid) {
		// flow rule selector
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
		selectorBuilder.matchVlanId(vid);

		// flow rule builder
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(portNumber)
                .build();

		// construct flow rule
        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(treatment)
                .withPriority(flowPriority)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                //.makeTemporary(flowTimeout)
				.makePermanent()
                .add();

		// flow modify
        flowObjectiveService.forward(did, forwardingObjective);
    }

	private void installSegRuleDst(DeviceId did, VlanId vid) {
		// flow rule selector
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
		selectorBuilder.matchVlanId(vid);

		// flow rule builder
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
				.popVlan()
				.setOutput(PortNumber.FLOOD)
                .build();

		// construct flow rule
        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(treatment)
                .withPriority(flowPriority)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
				.makePermanent()
                //.makeTemporary(flowTimeout)
                .add();

		// flow modify
        flowObjectiveService.forward(did, forwardingObjective);
    }

	private void installSegRuleSrc(DeviceId did, IpPrefix ip, VlanId vid, PortNumber portNumber) {
		// flow rule selector
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
		selectorBuilder.matchEthType(Ethernet.TYPE_IPV4).matchIPDst(ip.getIp4Prefix());

		// flow rule builder
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
				.pushVlan()
				.setVlanId(vid)
                .setOutput(portNumber)
                .build();

		// construct flow rule
        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(treatment)
                .withPriority(flowPriority)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
				.makePermanent()
                //.makeTemporary(flowTimeout)
                .add();

		// flow modify
        flowObjectiveService.forward(did, forwardingObjective);
    }

	private class InternalTopologyListener implements TopologyListener {
		@Override
		public void event(TopologyEvent event) {
			// nothing to change
		}
	}
}


