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
package nctu.winlab.project6_0413335;

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

// import config
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import org.slf4j.LoggerFactory;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {
	
	// for debug
    private final Logger log = LoggerFactory.getLogger(getClass());
	
	// set timeout & priority
	private static final int DEFAULT_TIMEOUT = 60;
	private static final int DEFAULT_PRIORITY = 55556;

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

	private final Set<ConfigFactory> factories = ImmutableSet.of(new ConfigFactory<ApplicationId, nctu.winlab.project6_0413335.MyConfig>(APP_SUBJECT_FACTORY, 
	nctu.winlab.project6_0413335.MyConfig.class, 
	"myconfig"){
		@Override
		public nctu.winlab.project6_0413335.MyConfig createConfig(){
			return new nctu.winlab.project6_0413335.MyConfig();
			}
		}
	);

	private static String dhcpServer = "defaultName";
	private static String myName2 = "defaultName";
	/* --- */

    @Activate
    protected void activate() {
		appId = coreService.registerApplication("nctu.winlab.project6_0413335");

		/* --- */
		cfgService.addListener(cfgListener);
		factories.forEach(cfgService::registerConfigFactory);
		cfgListener.reconfigureNetwork(cfgService.getConfig(appId, nctu.winlab.project6_0413335.MyConfig.class));
		/* --- */
        
		packetService.addProcessor(processor, PacketProcessor.director(2));
		topologyService.addListener(topologyListener);
        requestIntercepts();
        log.info("Started", appId.id());		
    }

    @Deactivate
    protected void deactivate() {
		/* --- */
		cfgService.removeListener(cfgListener);
		factories.forEach(cfgService::unregisterConfigFactory);
		/* --- */

		withdrawIntercepts();
        flowRuleService.removeFlowRulesById(appId);
		packetService.removeProcessor(processor);
		topologyService.removeListener(topologyListener);
        processor = null;
        log.info("Stopped");
    }

	/* --- */
	private class InternalConfigListener implements NetworkConfigListener {
		private void reconfigureNetwork(nctu.winlab.project6_0413335.MyConfig cfg){
			if(cfg == null){
				return;
			}
			if(cfg.dhcpserver() != null){
				dhcpServer = cfg.dhcpserver();
			}
			if(cfg.myname2() != null){
				myName2 = cfg.myname2();
			}
		}
			
		@Override
		public void event(NetworkConfigEvent event){
		// while configuration is uploaded, update the variable.
			if((event.type() == NetworkConfigEvent.Type.CONFIG_ADDED ||
				event.type() == NetworkConfigEvent.Type.CONFIG_UPDATED) &&
				event.configClass().equals(nctu.winlab.project6_0413335.MyConfig.class)){

				nctu.winlab.project6_0413335.MyConfig cfg = cfgService.getConfig(appId, nctu.winlab.project6_0413335.MyConfig.class);
				reconfigureNetwork(cfg);
				log.info("Reconfigured, new DHCP server is {}", dhcpServer);
				log.info("demo: {}", myName2);
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

			// do not process control packets, like LLDP and BDDP packets
            if (isControlPacket(ethPkt)) {
                return;
            }
			
			if(ethPkt.getEtherType() == Ethernet.TYPE_ARP){
				//flood(context);
				return;
			}

			MacAddress macAddress = ethPkt.getSourceMAC();
			PortNumber inPort = pkt.receivedFrom().port();
			DeviceId switchId = pkt.receivedFrom().deviceId();
			
			/* --- */
			if(ethPkt.getEtherType() == Ethernet.TYPE_IPV4
			   && ethPkt.getDestinationMAC().equals(MacAddress.BROADCAST)){
				log.info("IPV4 broadcast packet received, DHCP server is {}", dhcpServer);

			   	String serverDeviceId = dhcpServer.split("/")[0];
			   	String serverPort = dhcpServer.split("/")[1];
			
				if(switchId.equals(DeviceId.deviceId(serverDeviceId))){
					//log.info(switchId + " v.s " + serverDeviceId);
					PortNumber port = PortNumber.fromString(serverPort);
					installRule(context, port, switchId);
					log.info("install DHCP broadcast packets for: " + switchId + ", to port: " + port);
					return;
				}
				
				/* find the srcHost and the DHCP server's path */
				// get graph
				TopologyGraph graph = topologyService.getGraph(topologyService.currentTopology());
				Set<TopologyVertex> V_Set = graph.getVertexes();
				Set<TopologyEdge> E_Set = graph.getEdges();

				int srcIdx = -1;
				int dstIdx = -1;
				for(int i = 0; i < V_Set.size(); i ++){
					TopologyVertex tmpV = (TopologyVertex)V_Set.toArray()[i];
					if(tmpV.deviceId().equals(DeviceId.deviceId(serverDeviceId))){
						dstIdx = i;
					}

					if(tmpV.deviceId().equals(switchId)){
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
						port = PortNumber.fromString(serverPort);
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
					
					log.info("install DHCP broadcast packets for: " + tmp.deviceId() + ", to port: " + port);
					installRule(context, port, tmp.deviceId());
				}
			   	return;
			}
			/* --- */
           
		    /*
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

			//log.info("packet-in from: " + switchId);			
			if(switchId.equals(dst.location().deviceId())){
				installRule(context, dst.location().port(), switchId);
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
				installRule(context, port, tmp.deviceId());
			}
			*/
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
	
    // Install a rule forwarding the packet to the specified port.
    private void installRule(PacketContext context, PortNumber portNumber, DeviceId did) {
		InboundPacket pkt = context.inPacket();
        Ethernet inPkt = pkt.parsed();
		PortNumber inPort = pkt.receivedFrom().port();

		// flow rule selector
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
		selectorBuilder.matchEthDst(inPkt.getDestinationMAC()).matchEthSrc(inPkt.getSourceMAC());

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
                .makeTemporary(flowTimeout)
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


