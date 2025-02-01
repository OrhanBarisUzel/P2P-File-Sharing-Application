import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
//1k= 1 second
public class P2PConnection{
    private DatagramSocket connectionSocket;
    private DatagramSocket broadcastSocket;
    private static final int DISCOVERY_PORT= 8888;
    private static final String BROADCAST_ADDRESS= "255.255.255.255";
    private static final int PEER_TIMEOUT= 10000; 
    private static final int HEARTBEAT_INTERVAL= 1000; 
    private boolean flag; //checking that is it run or not.
    private Map<String, PeerInformation> peers;
    private String localAddress;
    private final int filePort;
    private Thread connectionThread;
    private Thread broadcastThread;
    private Thread intervalThread;
    private Thread cleanupThread;
    
    private class PeerInformation{
        Peer peer;
        long lastSeen;
        int missedHeartbeats;

        PeerInformation(Peer peer){
            this.peer= peer;
            this.lastSeen= System.currentTimeMillis();
            this.missedHeartbeats= 0;
        }
        void seen(){
            this.lastSeen= System.currentTimeMillis();
            this.missedHeartbeats= 0;
        }

        boolean isStable(){
            return System.currentTimeMillis()- lastSeen > PEER_TIMEOUT;
        }
    }
    
    public P2PConnection(int filePort){
        this.filePort= filePort;
        this.peers= new ConcurrentHashMap<>();
    }
    
    public Set<Peer> getPeers(){
        Set<Peer> activePeers= new HashSet<>();
        for (PeerInformation info : peers.values()){
            if (!info.isStable()){
                activePeers.add(info.peer);
            }
        }
        return activePeers;
    } 
    public void start() throws IOException{
        try{
            stop();
            //Create new sockets 
            connectionSocket= new DatagramSocket(null);
            connectionSocket.setReuseAddress(true);
            connectionSocket.setBroadcast(true);
            connectionSocket.bind(new InetSocketAddress(DISCOVERY_PORT));
            broadcastSocket= new DatagramSocket(null);
            broadcastSocket.setReuseAddress(true);
            broadcastSocket.setBroadcast(true);
            broadcastSocket.bind(new InetSocketAddress(0));
            flag= true;
            localAddress= findLocalAddress();
            System.out.println("Local address: " + localAddress);

            startconnectionThread();
            startBroadcastThread();
            startintervalThread();
            startCleanupThread();
            
            broadcastToAllInterfaces();
                    
        } catch (Exception e){
            stop();
            throw new IOException("P2P connection failed: " + e.getMessage(), e);
        }
    }

    private String findLocalAddress(){
        try{
            try (final DatagramSocket socket= new DatagramSocket()){
                socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
                return socket.getLocalAddress().getHostAddress();
            }
        } catch (Exception e){
            try{
                return InetAddress.getLocalHost().getHostAddress();
            } catch (Exception ex){
                return "127.0.0.1"; //local
            }
        }
    }

    private void startconnectionThread(){
        connectionThread= new Thread(() ->{
            byte[] buffer= new byte[1024];
            DatagramPacket packet= new DatagramPacket(buffer, buffer.length);
            
            while (flag){
                try{
                    connectionSocket.receive(packet);
                    handlePacket(packet);
                } catch (IOException e){
                    if (flag){
                        System.err.println("Discovery error: " + e.getMessage());
                    }
                }
            }
        }, "Discovery-Thread");
        connectionThread.start();
    }

    private void startBroadcastThread(){
        broadcastThread= new Thread(() ->{
            while (flag){
                try{
                    broadcastToAllInterfaces();
                    Thread.sleep(1000);
                } catch (Exception e){
                    if (flag){
                        System.err.println("Broadcast error: " + e.getMessage());
                    }
                }
            }
        }, "Broadcast-Thread");
        broadcastThread.start();
    }
    private void startintervalThread(){
        intervalThread= new Thread(() ->{
            while (flag){
                try{
                    sendHeartbeats();
                    Thread.sleep(HEARTBEAT_INTERVAL);
                } catch (InterruptedException e){
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e){
                    if (flag){
                        System.err.println("Heartbeat error: " + e.getMessage());
                    }
                }
            }
        }, "Heartbeat-Thread");
        intervalThread.start();
    }
    private void sendHeartbeats(){
        String message= "HEARTBEAT:" + filePort + ":"+ localAddress;
        byte[] buffer= message.getBytes();
        
        
        for(PeerInformation info : peers.values()){
            try{
                InetAddress peerAddress= InetAddress.getByName(info.peer.getAddress());
                DatagramPacket packet= new DatagramPacket(
                    buffer,
                    buffer.length,
                    peerAddress,
                    DISCOVERY_PORT
                );
                broadcastSocket.send(packet);
            }catch (Exception e){
                info.missedHeartbeats++;
                if (info.missedHeartbeats > 5){ //After 5 missed heartbeats, try broadcast
                    try{
                        broadcastDiscovery();
                    } catch (Exception be){
                        System.err.println("Broadcast error: " + be.getMessage());
                    }
                }
            }
        }
    }

    private void startCleanupThread(){
        cleanupThread= new Thread(() ->{
            while (flag){
                try{
                    cleanupStaleConnections();
                    Thread.sleep(PEER_TIMEOUT / 2);
                } catch (InterruptedException e){
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "Cleanup-Thread");
        cleanupThread.start();
    }

    private void cleanupStaleConnections(){
        Iterator<Map.Entry<String, PeerInformation>> it= peers.entrySet().iterator();
        while (it.hasNext()){
            Map.Entry<String, PeerInformation> entry= it.next();
            PeerInformation info= entry.getValue();
            if (info.isStable()){
                System.out.println("Peer disconnected: " + info.peer.getAddress());
                it.remove();
            }
        }
    }

    private void broadcastToAllInterfaces() throws IOException{
        //first try direct broadcast
        sendBroadcast(InetAddress.getByName(BROADCAST_ADDRESS));

        //then try interface-specific broadcasts
        Enumeration<NetworkInterface> interfaces= NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()){
            NetworkInterface networkInterface= interfaces.nextElement();
            if (!networkInterface.isUp()|| networkInterface.isLoopback()){
                continue;
            }
            //trying both subnet broadcast and direct broadcast
            for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()){
                InetAddress broadcast= interfaceAddress.getBroadcast();
                if (broadcast != null){
                    sendBroadcast(broadcast);
                }
            }

            //Also try broadcasting to all addresses in the subnet
            for (InetAddress address : Collections.list(networkInterface.getInetAddresses())){
                if (address instanceof Inet4Address){
                    String hostAddress= address.getHostAddress();
                    String subnet= hostAddress.substring(0, hostAddress.lastIndexOf('.') + 1);
                    for (int i= 1; i < 255; i++){
                        try{
                            InetAddress targetAddress= InetAddress.getByName(subnet + i);
                            sendBroadcast(targetAddress);
                        } catch (Exception e){
                            
                        }
                    }
                }
            }
        }
    }
    private void broadcastDiscovery() throws IOException{
        
        sendBroadcast(InetAddress.getByName(BROADCAST_ADDRESS)); 
        Enumeration<NetworkInterface> interfaces= NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()){
            NetworkInterface networkInterface= interfaces.nextElement();
            if (!networkInterface.isUp() || networkInterface.isLoopback()){
                continue;
            }
            for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()){
                InetAddress broadcast= interfaceAddress.getBroadcast();
                if (broadcast != null){
                    sendBroadcast(broadcast);
                }
            }
        }
    }
    private void sendBroadcast(InetAddress address){
        try{
            String message= "DISCOVER:" + filePort + ":" + localAddress;
            byte[] buffer= message.getBytes();
            DatagramPacket packet= new DatagramPacket(
                buffer,
                buffer.length,
                address,
                DISCOVERY_PORT
            );
            broadcastSocket.send(packet);
        } catch (Exception e){
        	
        }
    }
    private void handlePacket(DatagramPacket packet){
        String message= new String(packet.getData(), 0, packet.getLength());
        String[] parts= message.split(":");
        if (parts.length== 3){
            try{
                int peerFilePort= Integer.parseInt(parts[1]);
                String peerAddress= parts[2];
                
                if (!peerAddress.equals(localAddress)){
                    Peer peer= new Peer(peerAddress, peerFilePort);
                    PeerInformation PeerInformation= peers.get(peerAddress);
                    
                    if (PeerInformation== null){
                        // New peer
                        peers.put(peerAddress, new PeerInformation(peer));
                        System.out.println("New peer connected: " + peerAddress);
                        sendDirectResponse(InetAddress.getByName(peerAddress));
                    } else{
                        //Existing peer - update last seen
                        PeerInformation.seen();
                    }
                }
            } catch (Exception e){
                System.err.println("Error message: " + e.getMessage());
            }
        }
    }

    private void sendDirectResponse(InetAddress address){
        try{
            String message= "RESPONSE:" + filePort + ":" + localAddress;
            byte[] buffer= message.getBytes();
            DatagramPacket response= new DatagramPacket(
                buffer,
                buffer.length,
                address,
                DISCOVERY_PORT
            );
            broadcastSocket.send(response);
        } catch (Exception e){
            System.err.println("Error: " + e.getMessage());
        }
    }

    public void stop(){
        flag= false;
        if (connectionSocket!= null){
            connectionSocket.close();
        }
        if (broadcastSocket!= null){
            broadcastSocket.close();
        }
        
        Thread[] threads={connectionThread, broadcastThread, intervalThread, cleanupThread};
        for (Thread thread : threads){
            if (thread != null){
                thread.interrupt();
                try{
                    thread.join(1000);
                } catch (InterruptedException e){
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        peers.clear();
    }
}