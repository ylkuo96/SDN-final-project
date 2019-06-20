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
	private static String Switches = "default";
	private static String Hosts = "default";

	private allSwitch allSwitches;	

    @Activate
    protected void activate() {
		appId = coreService.registerApplication("nctu.winlab.project8_0413335");
		cfgService.addListener(cfgListener);
		factories.forEach(cfgService::registerConfigFactory);
		cfgListener.reconfigureNetwork(cfgService.getConfig(appId, nctu.winlab.project8_0413335.MyConfig.class));
		topologyService.addListener(topologyListener);
		allSwitches = new allSwitch();
        log.info("Started", appId.id());		
    }

    @Deactivate
    protected void deactivate() {
		cfgService.removeListener(cfgListener);
		factories.forEach(cfgService::unregisterConfigFactory);
        flowRuleService.removeFlowRulesById(appId);
		topologyService.removeListener(topologyListener);
		allSwitches = null;
        log.info("Stopped");
    }

	private class allSwitch {
		private class switchInfo {
			private class hostInfo {
				MacAddress mac;
				PortNumber port;
				
				private hostInfo(MacAddress mac, PortNumber port) {
					this.mac = mac;
					this.port = port;
				}
			}
			
			DeviceId sid;
			IpPrefix ip; 
			VlanId vid;
			ArrayList<hostInfo> hostArray = new ArrayList<hostInfo>();
			
			private switchInfo(DeviceId sid, IpPrefix ip, VlanId vid) {
				this.sid = sid;
				this.ip = ip;
				this.vid = vid;
			}

			// add host to switch
			private boolean addHost(MacAddress mac, PortNumber port) {
				this.hostArray.add(new hostInfo(mac, port));
				return true;
			}
			
		}

		ArrayList<switchInfo> switchArray = new ArrayList<switchInfo>();
		
		// add switches
		private boolean addSwitch(DeviceId sid, IpPrefix ip, VlanId vid) {
			this.switchArray.add(new switchInfo(sid, ip, vid));
			return true;
		}
	}	

	private class InternalConfigListener implements NetworkConfigListener {
		private void reconfigureNetwork(nctu.winlab.project8_0413335.MyConfig cfg){
			if(cfg == null){
				return;
			}
			
			if(cfg.switches() != null){
				Switches = cfg.switches();
			}
			if(cfg.hosts() != null){
				Hosts = cfg.hosts();
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
				
				String[] switches = Switches.split("@");
				for(String tmp : switches) {
					DeviceId sid = DeviceId.deviceId(tmp.split("_")[0]);
					IpPrefix ip = IpPrefix.valueOf(tmp.split("_")[1]);
					VlanId vid = VlanId.vlanId(tmp.split("_")[2]);
					
					allSwitches.addSwitch(sid, ip, vid);
				}

				String[] hosts = Hosts.split("@");
				for(String tmp : hosts) {
					DeviceId sid = DeviceId.deviceId(tmp.split("_")[0]);
					PortNumber port = PortNumber.portNumber(tmp.split("_")[1]);
					MacAddress mac = MacAddress.valueOf(tmp.split("_")[2]);
					
					for(int i = 0; i < allSwitches.switchArray.size(); i ++){
						if(sid.equals(allSwitches.switchArray.get(i).sid)){
							allSwitches.switchArray.get(i).addHost(mac, port);
							break;
						}
					}
				}
			
				// print info
				/*
				for(int i = 0; i < allSwitches.switchArray.size(); i ++){
					log.info("switchID: " + allSwitches.switchArray.get(i).sid);
					log.info("subnetIP: " + allSwitches.switchArray.get(i).ip);
					log.info("vlanID: " + allSwitches.switchArray.get(i).vid);
					log.info("------------------------");
					for(int j = 0; j < allSwitches.switchArray.get(i).hostArray.size(); j ++){
						log.info("host mac: " + allSwitches.switchArray.get(i).hostArray.get(j).mac);
						log.info("host port: " + allSwitches.switchArray.get(i).hostArray.get(j).port);
					}
					log.info("------------------------");
				}
				*/

				/* install intra-device flow rules */
				for(int i = 0; i < allSwitches.switchArray.size(); i ++){
					DeviceId sid = allSwitches.switchArray.get(i).sid;
					for(int j = 0; j < allSwitches.switchArray.get(i).hostArray.size(); j ++){
						PortNumber port = allSwitches.switchArray.get(i).hostArray.get(j).port;
						MacAddress mac = allSwitches.switchArray.get(i).hostArray.get(j).mac;
						installR(port, sid, mac);
					}
				}
				
				/* install inter-device flow rules */
				for(int i = 0; i < allSwitches.switchArray.size(); i ++){
					DeviceId sid1 = allSwitches.switchArray.get(i).sid;
					IpPrefix ip1 = allSwitches.switchArray.get(i).ip;
					VlanId vid1 = allSwitches.switchArray.get(i).vid;
					for(int j = i+1; j < allSwitches.switchArray.size(); j ++){
						DeviceId sid2 = allSwitches.switchArray.get(j).sid;
						IpPrefix ip2 = allSwitches.switchArray.get(j).ip;
						VlanId vid2 = allSwitches.switchArray.get(j).vid;
						
						// flow rules from src: sid1; dst: sid2
						installManyRules(sid1, sid2, ip2, vid2, allSwitches);

						// flow rules from src: sid2; dst: sid1
						installManyRules(sid2, sid1, ip1, vid1, allSwitches);
					}
				}
			}
		}
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

            if (isControlPacket(ethPkt)) {
                return;
            }

			if(ethPkt.getEtherType() == Ethernet.TYPE_ARP){
				return;
			}

			if(ethPkt.getDestinationMAC().equals(MacAddress.BROADCAST)){
				return;
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
		int i = path.indexOf(dstIdx);
		int source = path.get(i + 1);

		shortestPath.add(0, dstIdx);

		if(source == srcIdx){
			shortestPath.add(0, srcIdx);
			return shortestPath;
		}
		else{
			return ProcessPath(srcIdx, source, path);
		}
	}

	private PortNumber getport(int nidx, TopologyVertex tmp, Set<TopologyVertex> V_Set, Set<TopologyEdge> E_Set, ArrayList<Integer> shortestPath){
		PortNumber port = PortNumber.portNumber(-1);
		for(int j = 0; j < E_Set.size(); j ++){
			TopologyEdge tmpE = (TopologyEdge)E_Set.toArray()[j];
			TopologyVertex ntmp = (TopologyVertex)V_Set.toArray()[nidx];
			if(tmpE.link().src().deviceId().equals(tmp.deviceId())){
				if(tmpE.link().dst().deviceId().equals(ntmp.deviceId())){
					port = tmpE.link().src().port();
					return port;
				}
			}
		}

		return port;
	}

    private boolean isControlPacket(Ethernet eth) {
        short type = eth.getEtherType();
        return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN;
    }	
	
    private void flood(PacketContext context) {
        if (topologyService.isBroadcastPoint(topologyService.currentTopology(),
                                             context.inPacket().receivedFrom())) {
            packetOut(context, PortNumber.FLOOD);
        } else {
            context.block();
        }
    }

    private void packetOut(PacketContext context, PortNumber portNumber) {
        context.treatmentBuilder().setOutput(portNumber);
        context.send();
    }	
	
    private void installR(PortNumber portNumber, DeviceId did, MacAddress dstMac) {
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
		selectorBuilder.matchEthDst(dstMac);

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(portNumber)
                .build();

        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(treatment)
                .withPriority(39997)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
				.makePermanent()
                .add();

        flowObjectiveService.forward(did, forwardingObjective);
    }	
	
    private void installSegRuleMid(PortNumber portNumber, DeviceId did, VlanId vid) {
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
		selectorBuilder.matchVlanId(vid);

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(portNumber)
                .build();

        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(treatment)
                .withPriority(flowPriority)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
				.makePermanent()
                .add();

        flowObjectiveService.forward(did, forwardingObjective);
    }

	private void installSegRuleDst(DeviceId did, VlanId vid, MacAddress mac, PortNumber port) {
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
		selectorBuilder.matchVlanId(vid).matchEthDst(mac);

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
				.popVlan()
				.setOutput(port)
                .build();

        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(treatment)
                .withPriority(flowPriority)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
				.makePermanent()
                .add();

        flowObjectiveService.forward(did, forwardingObjective);
    }

	private void installSegRuleSrc(DeviceId did, IpPrefix ip, VlanId vid, PortNumber portNumber) {
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
		selectorBuilder.matchEthType(Ethernet.TYPE_IPV4).matchIPDst(ip.getIp4Prefix());

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
				.pushVlan()
				.setVlanId(vid)
                .setOutput(portNumber)
                .build();

        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(treatment)
                .withPriority(flowPriority)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
				.makePermanent()
                .add();

        flowObjectiveService.forward(did, forwardingObjective);
    }

	private void installManyRules(DeviceId srcsid, DeviceId dstsid, IpPrefix dstip, VlanId dstvid, allSwitch allSwitches){
		TopologyGraph graph = topologyService.getGraph(topologyService.currentTopology());
		Set<TopologyVertex> V_Set = graph.getVertexes();
		Set<TopologyEdge> E_Set = graph.getEdges();

		int srcIdx = -1;
		int dstIdx = -1;
		
		for(int i = 0; i < V_Set.size(); i ++){
			TopologyVertex tmpV = (TopologyVertex)V_Set.toArray()[i];
			if(tmpV.deviceId().equals(srcsid)){
				srcIdx = i;
			}
			
			if(tmpV.deviceId().equals(dstsid)){
				dstIdx = i;
			}

			if(srcIdx != -1 && dstIdx != -1){
				break;
			}
		}

		BFS(graph, V_Set, E_Set, srcIdx, dstIdx);

		for(int i = 0; i < shortestPath.size(); i ++){
			int idx = shortestPath.get(i);
			TopologyVertex tmp = (TopologyVertex)V_Set.toArray()[idx];
			DeviceId did = tmp.deviceId();
			if(i == 0){
				int nidx = shortestPath.get(i + 1);
				PortNumber port = getport(nidx, tmp, V_Set, E_Set, shortestPath);
				installSegRuleSrc(did, dstip, dstvid, port);
			}
			else if(i == (shortestPath.size() - 1)){
				for(int j = 0; j < allSwitches.switchArray.size(); j ++){
					if(dstsid.equals(allSwitches.switchArray.get(j).sid)){
						for(int k = 0; k < allSwitches.switchArray.get(j).hostArray.size(); k ++){
							PortNumber port = allSwitches.switchArray.get(j).hostArray.get(k).port;
							MacAddress mac = allSwitches.switchArray.get(j).hostArray.get(k).mac;
							installSegRuleDst(did, dstvid, mac, port);
						}
						break;
					}
				}
			}
			else{
				int nidx = shortestPath.get(i + 1);
				PortNumber port = getport(nidx, tmp, V_Set, E_Set, shortestPath);
				installSegRuleMid(port, did, dstvid);
			}
		}
	}

	private class InternalTopologyListener implements TopologyListener {
		@Override
		public void event(TopologyEvent event) {
			// nothing to change
		}
	}
}

